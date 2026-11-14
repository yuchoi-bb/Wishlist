/**
 * Arc on the web: the same table, from the same Firestore, over the same sign-in.
 *
 * There is no server here. The browser talks to Firestore directly, and firestore.rules decides
 * what it may see — `request.auth.uid == userId` — so signing in as someone else shows their own
 * (empty) list rather than anything of this account's. Nothing in the Android app had to change.
 */
import { initializeApp } from 'https://www.gstatic.com/firebasejs/11.0.2/firebase-app.js';
import {
  GoogleAuthProvider, getAuth, onAuthStateChanged, signInWithPopup, signOut,
} from 'https://www.gstatic.com/firebasejs/11.0.2/firebase-auth.js';
import {
  collection, doc, getFirestore, onSnapshot, updateDoc,
} from 'https://www.gstatic.com/firebasejs/11.0.2/firebase-firestore.js';
import {
  SORT_FIELDS, buildRows, categoryColor, effectiveEndDate, formatDate, formatRemainingDays,
  formatYearMonth, monthColor, priorityLabel, rowIsDone, daysUntil,
} from './table.js';

const $ = (id) => document.getElementById(id);
const state = {
  items: [], categoryColors: [], sortField: 'END_DATE', ascending: true, hideCompleted: true,
  uid: null, loaded: false,
};

/* ---------------------------------------------------------------- Firebase */

async function firebaseConfig() {
  // Firebase Hosting serves the project's own config at this path, so a deployed copy needs no
  // keys pasted in. Opened any other way, config.js is the fallback.
  try {
    const response = await fetch('/__/firebase/init.json');
    if (response.ok) return await response.json();
  } catch { /* not on Hosting */ }
  const { firebaseConfig: local } = await import('./config.js');
  if (!local?.apiKey || local.apiKey.startsWith('여기에')) {
    throw new Error('Firebase 설정이 없습니다. Hosting에 배포하거나 config.js를 채워주세요.');
  }
  return local;
}

async function start() {
  const app = initializeApp(await firebaseConfig());
  const auth = getAuth(app);
  const db = getFirestore(app);

  $('signin').onclick = () => signInWithPopup(auth, new GoogleAuthProvider()).catch(showError);
  $('signout').onclick = () => signOut(auth);

  onAuthStateChanged(auth, (user) => {
    state.uid = user?.uid ?? null;
    state.loaded = false;
    state.items = [];
    $('account').textContent = user?.email ?? '';
    document.body.dataset.signedIn = user ? 'yes' : 'no';
    if (user) subscribe(db, user.uid);
    render();
  });
}

let unsubscribers = [];
function subscribe(db, uid) {
  unsubscribers.forEach((fn) => fn());
  // The same live subscription the phone holds, so a tick on either shows up on the other without
  // a refresh.
  unsubscribers = [
    onSnapshot(collection(db, 'users', uid, 'wishlist_items'), (snapshot) => {
      state.items = snapshot.docs.map(readItem);
      state.loaded = true;
      render();
    }, showError),
    onSnapshot(doc(db, 'users', uid, 'settings', 'categoryColors'), (snapshot) => {
      state.categoryColors = (snapshot.data()?.entries ?? []).map((e) => ({
        major: e.major, minor: e.minor ?? null, paletteIndex: e.paletteIndex ?? 0,
      }));
      render();
    }, showError),
  ];
  state.db = db;
}

/** Reads a stored document the way the app's toArcItem does, defaulting anything missing. */
function readItem(snapshot) {
  const d = snapshot.data();
  return {
    id: snapshot.id,
    title: d.title ?? '',
    memo: d.memo ?? null,
    subItems: (d.subItems ?? []).map((s) => ({
      title: s?.title ?? '', done: s?.done === true, endDate: s?.endDate ?? null,
    })),
    majorCategory: d.majorCategory ?? null,
    minorCategory: d.minorCategory ?? null,
    startedAt: d.startedAt ?? 0,
    endDate: d.endDate ?? null,
    isDone: d.isDone === true,
    priority: typeof d.priority === 'number' ? d.priority : 2,
    position: d.position ?? 0,
  };
}

/**
 * Ticking a line, with the same meaning the app gives it: a 세부항목 line flips only that step,
 * an 항목 line flips the whole item. Neither cascades into the other.
 *
 * Firestore cannot address one element of an array, so a step is written by sending the whole
 * subItems list back — rebuilt from what is on screen, which is the latest snapshot.
 */
async function toggle(row) {
  const ref = doc(state.db, 'users', state.uid, 'wishlist_items', row.item.id);
  const patch = row.subIndex < 0
    ? { isDone: !row.item.isDone }
    : {
      subItems: row.item.subItems.map((s, i) =>
        ({ title: s.title, done: i === row.subIndex ? !s.done : s.done, endDate: s.endDate })),
    };
  await updateDoc(ref, patch).catch(showError);
}

/* ------------------------------------------------------------------ render */

const dark = () => matchMedia('(prefers-color-scheme: dark)').matches;

function render() {
  $('sort').value = state.sortField;
  $('direction').textContent = state.ascending ? '↑ 오름차순' : '↓ 내림차순';
  $('hide-completed').checked = state.hideCompleted;

  const rows = buildRows(state.items, state);
  $('summary').textContent = summary();

  const body = $('rows');
  body.replaceChildren();
  if (!state.loaded) { $('empty').textContent = '불러오는 중…'; $('empty').hidden = false; return; }
  if (rows.length === 0) { $('empty').textContent = '아직 등록된 항목이 없습니다.'; $('empty').hidden = false; return; }
  $('empty').hidden = true;

  // Month bands, only under the 예정일 sort — the one order in which a month boundary means
  // anything. Same rule as the app's banded table.
  let band = null;
  const isDark = dark();
  for (const row of rows) {
    const due = effectiveEndDate(row);
    if (state.sortField === 'END_DATE') {
      const key = due == null ? 'none' : formatYearMonth(due);
      if (key !== band) {
        band = key;
        body.append(bandRow(key === 'none' ? '예정일 없음' : key, monthColor(due, isDark)));
      }
    }
    body.append(tableRow(row, isDark));
  }
}

/** Small counts read better as words; past ten, a figure is clearer than a mouthful. */
const SPELLED = ['없음', '하나', '둘', '셋', '넷', '다섯', '여섯', '일곱', '여덟', '아홉', '열'];
const spellCount = (n) => SPELLED[n] ?? `${n}개`;

/**
 * The same sentence the app's header carries, counted the same way: anything already past is
 * today's problem rather than a separate number, and the week runs seven days out.
 */
function summary() {
  const days = buildRows(state.items, { ...state, hideCompleted: true })
    .map(effectiveEndDate)
    .filter((due) => due != null)
    .map((due) => daysUntil(due));
  const today = days.filter((d) => d <= 0).length;
  const week = days.filter((d) => d >= 0 && d <= 7).length;
  if (today > 0) return `오늘까지 ${spellCount(today)}, 이번 주 ${spellCount(week)}.`;
  if (week > 0) return `이번 주 마감 ${spellCount(week)}.`;
  return '이번 주 마감은 없습니다.';
}

function bandRow(label, color) {
  const tr = document.createElement('tr');
  tr.className = 'band';
  const td = document.createElement('td');
  td.colSpan = 8;
  td.textContent = label;
  if (color) td.style.borderLeftColor = color;
  tr.append(td);
  return tr;
}

function cell(text, className) {
  const td = document.createElement('td');
  td.textContent = text;
  if (className) td.className = className;
  return td;
}

function tableRow(row, isDark) {
  const { item, subItem } = row;
  const due = effectiveEndDate(row);
  const done = rowIsDone(row);

  const tr = document.createElement('tr');
  if (done) tr.classList.add('done');
  if (row.grouped && row.subIndex >= 0) tr.classList.add('child');

  // A stripe for the category and a wash for the month, the two cues the app's table uses.
  const stripe = categoryColor(item.majorCategory, item.minorCategory, state.categoryColors);
  if (stripe) tr.style.setProperty('--stripe', stripe);
  if (state.sortField === 'END_DATE') {
    const tint = monthColor(due, isDark);
    if (tint) tr.style.setProperty('--tint', tint);
  }

  tr.append(
    cell(item.majorCategory ?? '', 'major'),
    cell(item.minorCategory ?? '', 'minor'),
    // Under a grouped sort the 항목 is already its own line above, so repeating it on each child
    // would just be noise.
    cell(row.grouped && row.subIndex >= 0 ? '' : item.title, 'title'),
    cell(subItem ? subItem.title : (row.subIndex < 0 && item.subItems.length ? '' : '-'), 'sub'),
    cell(formatDate(due), 'date'),
  );

  const remaining = document.createElement('td');
  remaining.className = 'remaining';
  const chip = document.createElement('span');
  chip.textContent = formatRemainingDays(due);
  if (due != null && !done) {
    const days = daysUntil(due);
    chip.dataset.urgency = days < 0 ? 'late' : days <= 7 ? 'soon' : 'later';
  }
  remaining.append(chip);
  tr.append(remaining, cell(priorityLabel(item.priority), 'priority'));

  const check = document.createElement('td');
  check.className = 'check';
  const box = document.createElement('input');
  box.type = 'checkbox';
  box.checked = done;
  box.ariaLabel = '완료';
  box.onchange = () => { box.disabled = true; toggle(row).finally(() => { box.disabled = false; }); };
  check.append(box);
  tr.append(check);
  return tr;
}

function showError(error) {
  const banner = $('error');
  banner.textContent = error?.message ?? String(error);
  banner.hidden = false;
}

/* ------------------------------------------------------------------ wiring */

$('hide-completed').onchange = (e) => { state.hideCompleted = e.target.checked; render(); };
$('sort').onchange = (e) => { state.sortField = e.target.value; render(); };
$('direction').onclick = () => { state.ascending = !state.ascending; render(); };
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', render);

for (const field of SORT_FIELDS) {
  const option = document.createElement('option');
  option.value = field.id;
  option.textContent = field.label;
  $('sort').append(option);
}

start().catch(showError);
