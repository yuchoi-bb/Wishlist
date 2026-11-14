/**
 * Everything that decides what the table shows, kept apart from Firebase and the DOM so it can be
 * run and checked on its own.
 *
 * All of it mirrors the Android app: ArcRepository's row building and comparators, CategoryColors,
 * MonthColors, DateOnly. Where the two disagree the phone is right — these are a second
 * implementation of one spec, not a new one.
 */

/** Java's String.hashCode, which is what the app's automatic category colours are keyed on. */
export function javaHash(text) {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (Math.imul(31, hash) + text.charCodeAt(i)) | 0;
  return hash;
}

export const CATEGORY_PALETTE = [
  '#4F7CFF', '#2FA37A', '#E0693E', '#8E5BD0',
  '#C0397B', '#2A9AAF', '#B08A1E', '#5E6E8C',
];

const hexToRgb = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16));
const rgbToHex = (rgb) => '#' + rgb.map((v) => Math.round(v).toString(16).padStart(2, '0')).join('');
const mix = (rgb, target, amount) => rgb.map((v, i) => v + (target[i] - v) * amount);

function prefColor(prefs, major, minor) {
  const hit = prefs.find((p) => p.major === major && (p.minor ?? null) === minor);
  if (!hit) return null;
  // Kotlin's mod() is always non-negative, unlike JS's %, so a stored negative index still lands
  // on a real colour instead of undefined.
  const size = CATEGORY_PALETTE.length;
  return CATEGORY_PALETTE[((hit.paletteIndex % size) + size) % size];
}

/**
 * The colour identifying a (대/중분류), or null when there is no 대분류 to colour by.
 *
 * The 대분류 hue matches the app exactly — same palette, same hash, same index. The 중분류 shade
 * steps are close but not identical: Compose interpolates colour in Oklab and this mixes in sRGB,
 * so a shaded 중분류 can sit a shade off what the phone draws. The family is the same either way.
 */
export function categoryColor(major, minor, prefs = []) {
  const majorKey = (major ?? '').trim() || null;
  if (!majorKey) return null;
  const minorKey = (minor ?? '').trim() || null;

  if (minorKey) {
    const exact = prefColor(prefs, majorKey, minorKey);
    if (exact) return exact;
  }
  const base = prefColor(prefs, majorKey, null)
    ?? CATEGORY_PALETTE[Math.abs(javaHash(majorKey)) % CATEGORY_PALETTE.length];
  if (!minorKey) return base;

  const shift = ((Math.abs(javaHash(minorKey)) % 5) - 2) * 0.12;
  if (shift === 0) return base;
  const target = shift > 0 ? [255, 255, 255] : [0, 0, 0];
  return rgbToHex(mix(hexToRgb(base), target, Math.abs(shift)));
}

/** One hue per month, 30° apart, so the month boundary reads without looking at a date. */
export function monthColor(millis, dark) {
  if (millis == null) return null;
  const month = new Date(millis).getMonth() + 1;
  const h = (month - 1) * 30;
  const s = dark ? 60 : 80;
  const l = dark ? 50 : 42.5; // hsv(v=1,s=.6) and hsv(v=.85,s=.8) as hsl
  return `hsl(${h} ${s}% ${l}%)`;
}

const pad = (n) => String(n).padStart(2, '0');

/** "26.09.30" — two-digit year, as the table has eight columns to fit. */
export function formatDate(millis) {
  if (millis == null) return '';
  const d = new Date(millis);
  return `${pad(d.getFullYear() % 100)}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}`;
}

export function formatYearMonth(millis) {
  const d = new Date(millis);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월`;
}

const startOfDay = (millis) => {
  const d = new Date(millis);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
};

/** Whole days from today, counted on calendar days rather than elapsed milliseconds. */
export function daysUntil(endDate, now = Date.now()) {
  return Math.round((startOfDay(endDate) - startOfDay(now)) / 86400000);
}

export function formatRemainingDays(endDate, now = Date.now()) {
  if (endDate == null) return '-';
  const days = daysUntil(endDate, now);
  if (days > 0) return `D-${days}`;
  if (days === 0) return '오늘';
  return `D+${-days}`;
}

export const priorityLabel = (p) => (p === 1 ? '높음' : p === 2 ? '보통' : '여유');

export const SORT_FIELDS = [
  { id: 'END_DATE', label: '예정일', keepsItemTogether: false },
  { id: 'SUB_ITEM', label: '세부항목', keepsItemTogether: false },
  { id: 'CATEGORY', label: '대/중분류', keepsItemTogether: true },
  { id: 'TITLE', label: '할 일', keepsItemTogether: true },
  { id: 'PRIORITY', label: '순위', keepsItemTogether: true },
  { id: 'START_DATE', label: '시작일', keepsItemTogether: true },
];

const collator = new Intl.Collator('ko');
/** Null and blank sort together and last, the way the app's sortKey() puts uncategorised at the end. */
const sortKey = (value) => (value ?? '').trim() || '￿';

const effectiveEndDate = (row) => row.subItem?.endDate ?? row.item.endDate ?? null;
const rowIsDone = (row) => row.item.isDone || (row.subItem?.done ?? false);

function itemComparator(field) {
  const byField = {
    CATEGORY: (a, b) =>
      collator.compare(sortKey(a.majorCategory), sortKey(b.majorCategory))
      || collator.compare(sortKey(a.minorCategory), sortKey(b.minorCategory)),
    TITLE: (a, b) => collator.compare(a.title, b.title),
    PRIORITY: (a, b) => a.priority - b.priority,
    START_DATE: (a, b) => a.startedAt - b.startedAt,
    END_DATE: (a, b) => a.position - b.position,
    SUB_ITEM: (a, b) => a.position - b.position,
  }[field];
  // Stable tail, so items sharing a key keep their order instead of shuffling on every snapshot.
  return (a, b) => byField(a, b) || collator.compare(a.title, b.title) || a.position - b.position;
}

function rowComparator(field) {
  const FAR_FUTURE = Number.MAX_SAFE_INTEGER;
  const byField = field === 'END_DATE'
    ? (a, b) => (effectiveEndDate(a) ?? FAR_FUTURE) - (effectiveEndDate(b) ?? FAR_FUTURE)
    : field === 'SUB_ITEM'
      ? (a, b) => collator.compare(a.subItem?.title ?? '', b.subItem?.title ?? '')
      : (a, b) => a.item.position - b.item.position;
  return (a, b) =>
    byField(a, b) || collator.compare(a.item.title, b.item.title) || a.subIndex - b.subIndex;
}

/**
 * The table's lines, in the order the chosen sort puts them.
 *
 * An 항목-level sort emits an 항목 line followed by its own 세부항목, so a block travels together.
 * A 세부항목-level sort emits one line per 세부항목 across every item, each repeating its 항목.
 */
export function buildRows(items, { sortField = 'END_DATE', ascending = true, hideCompleted = true } = {}) {
  const keepsTogether = SORT_FIELDS.find((f) => f.id === sortField)?.keepsItemTogether ?? false;
  const sign = ascending ? 1 : -1;
  const visible = (row) => !hideCompleted || !rowIsDone(row);

  if (keepsTogether) {
    const cmp = itemComparator(sortField);
    return [...items]
      .sort((a, b) => sign * cmp(a, b))
      .flatMap((item) => [
        { item, subItem: null, subIndex: -1, grouped: true },
        ...item.subItems.map((subItem, subIndex) => ({ item, subItem, subIndex, grouped: true })),
      ].filter(visible));
  }

  const cmp = rowComparator(sortField);
  return items
    .flatMap((item) =>
      // Nothing to break out, so the 항목 stands in for itself rather than disappearing.
      item.subItems.length === 0
        ? [{ item, subItem: null, subIndex: -1, grouped: false }]
        : item.subItems.map((subItem, subIndex) => ({ item, subItem, subIndex, grouped: false })))
    .filter(visible)
    .sort((a, b) => sign * cmp(a, b));
}

export { effectiveEndDate, rowIsDone };
