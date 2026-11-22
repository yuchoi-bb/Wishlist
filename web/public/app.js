/**
 * Arc on the web: the same table, from the same Firestore, over the same sign-in.
 *
 * There is no server here. The browser talks to Firestore directly, and firestore.rules decides
 * what it may see: a private 할 일 under `users/{uid}` is reachable only by that account, and a
 * shared one under `shared_items` only by the accounts named in its members list. Signing in as
 * someone else shows their own list rather than anything of this account's.
 */
import { initializeApp } from '/__/firebase/11.0.2/firebase-app.js';
import {
  GoogleAuthProvider, getAuth, onAuthStateChanged, signInWithPopup, signOut,
} from '/__/firebase/11.0.2/firebase-auth.js';
import {
  addDoc, collection, deleteDoc, doc, getDoc, getFirestore, onSnapshot, query, setDoc, updateDoc,
  where, writeBatch,
} from '/__/firebase/11.0.2/firebase-firestore.js';
import {
  SORT_FIELDS, buildRows, categoryColor, effectiveEndDate, formatDate, formatRemainingDays,
  formatYearMonth, monthColor, priorityLabel, rowIsDone, daysUntil,
} from './table.js';
import { showEditor } from './editor.js';

const $ = (id) => document.getElementById(id);
const state = {
  items: [], categoryColors: [], sortField: 'END_DATE', ascending: true, hideCompleted: true,
  uid: null, loaded: false,
  // The two halves the list is built from, each with its own listener: this account's private
  // items, and the shared ones it is a member of.
  own: [], shared: [], loadedOwn: false, loadedShared: false,
  // Linked 할 일 opened so far, outermost first. Empty means the whole table.
  focusStack: [],
};

/**
 * The two halves as one list. Keyed by id with shared winning, so the moment during a share when
 * the document exists in both collections shows one row rather than two.
 */
function mergeItems() {
  const byId = new Map();
  for (const item of state.own) byId.set(item.id, item);
  for (const item of state.shared) byId.set(item.id, item);
  state.items = [...byId.values()];
  state.loaded = state.loadedOwn && state.loadedShared;
}

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
    state.loadedOwn = false;
    state.loadedShared = false;
    state.items = [];
    state.own = [];
    state.shared = [];
    $('account').textContent = user?.email ?? '';
    document.body.dataset.signedIn = user ? 'yes' : 'no';
    if (user) {
      subscribe(db, user.uid);
      registerEmail(db, user);
    }
    render();
  });
}

/**
 * The name of an address's entry in email_index: the SHA-256 of the lowercased address, in hex.
 *
 * Hashing is what keeps the collection from being a list of everybody's email addresses. The rules
 * allow `get` but not `list`, so an address already known can be looked up while the collection as
 * a whole cannot be read out — and they check this same hash against the writer's own sign-in
 * token, so nobody can register an address that isn't theirs.
 */
async function emailDigest(email) {
  const bytes = new TextEncoder().encode(email.trim().toLowerCase());
  const hash = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Notes this account under its email address, so somebody else can share with it by typing that
 * address instead of a uid.
 *
 * Best-effort and silent: a failure only means sharing falls back to the uid, and the message for a
 * recipient who cannot be found already says they may not have signed in yet. Reads before writing
 * because this runs on every page load, and nothing usually changes.
 */
async function registerEmail(db, user) {
  if (!user.email) return;
  try {
    const entry = doc(db, 'email_index', await emailDigest(user.email));
    const stored = await getDoc(entry);
    if (stored.data()?.uid === user.uid) return;
    await setDoc(entry, { uid: user.uid });
  } catch { /* the uid still works */ }
}

/**
 * Turns what was typed into the uid that sharing needs: an email address through the directory,
 * anything else taken as a uid already. Null when no account has registered that address — which,
 * since the entry is written at sign-in, mostly means they haven't opened Arc yet.
 */
async function resolveRecipient(entered) {
  const trimmed = entered.trim();
  if (!trimmed.includes('@')) return trimmed || null;
  try {
    const entry = await getDoc(doc(state.db, 'email_index', await emailDigest(trimmed)));
    return entry.data()?.uid ?? null;
  } catch {
    return null;
  }
}

let unsubscribers = [];
function subscribe(db, uid) {
  unsubscribers.forEach((fn) => fn());
  // The same live subscription the phone holds, so a tick on either shows up on the other without
  // a refresh.
  unsubscribers = [
    onSnapshot(collection(db, 'users', uid, 'wishlist_items'), (snapshot) => {
      state.own = snapshot.docs.map(readItem);
      state.loadedOwn = true;
      mergeItems();
      render();
    }, showError),
    // Shared 할 일 live outside anybody's subtree, so they are reached by membership rather than by
    // path. Rejected until firestore.rules carries the shared_items block, which is worth saying in
    // those words — "Missing or insufficient permissions" names no remedy.
    onSnapshot(
      query(collection(db, 'shared_items'), where('members', 'array-contains', uid)),
      (snapshot) => {
        state.shared = snapshot.docs.map(readItem);
        state.loadedShared = true;
        mergeItems();
        render();
      },
      (error) => {
        // Still counts as loaded, or the table sits on 불러오는 중… forever over a collection that
        // is empty for almost everyone.
        state.loadedShared = true;
        mergeItems();
        render();
        showError(error?.code === 'permission-denied'
          ? '공유 할 일을 읽을 권한이 없습니다. Firebase 콘솔에서 firestore.rules를 게시해주세요.'
          : error);
      },
    ),
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
      linkedItemId: s?.linkedItemId || null,
    })),
    majorCategory: d.majorCategory ?? null,
    minorCategory: d.minorCategory ?? null,
    startedAt: d.startedAt ?? 0,
    endDate: d.endDate ?? null,
    isDone: d.isDone === true,
    priority: typeof d.priority === 'number' ? d.priority : 2,
    position: d.position ?? 0,
    // Absent on a private 할 일, which is most of them. An empty member list is exactly what says
    // the document is private, and so which collection it lives in.
    ownerUid: typeof d.ownerUid === 'string' ? d.ownerUid : '',
    members: Array.isArray(d.members) ? d.members.filter((m) => typeof m === 'string' && m) : [],
  };
}

/** True once a 할 일 has a member list, which is also to say it lives in shared_items. */
const isShared = (item) => (item.members?.length ?? 0) > 0;

/** Whether this account may change who a 할 일 is shared with, or delete it for everyone. */
const isMine = (item) => !isShared(item) || item.ownerUid === state.uid;

/**
 * Ticking a line, with the same meaning the app gives it: a 세부항목 line flips only that step,
 * an 항목 line flips the whole item. Neither cascades into the other.
 *
 * Firestore cannot address one element of an array, so a step is written by sending the whole
 * subItems list back — rebuilt from what is on screen, which is the latest snapshot.
 */
async function toggle(row) {
  // A linked step is a view of the 할 일 it names, so ticking it ticks that 할 일 — the one place
  // the state lives, and the only place the person it was sent to can see it.
  const linkedId = row.subItem?.linkedItemId;
  if (linkedId) {
    const target = state.items.find((i) => i.id === linkedId);
    if (!target) return;
    await updateDoc(refFor(target), { isDone: !target.isDone }).catch(showError);
    return;
  }

  const ref = refFor(row.item);
  const patch = row.subIndex < 0
    ? { isDone: !row.item.isDone }
    : {
      subItems: row.item.subItems.map((s, i) => ({
        title: s.title,
        done: i === row.subIndex ? !s.done : s.done,
        endDate: s.endDate,
        linkedItemId: s.linkedItemId ?? null,
      })),
    };
  await updateDoc(ref, patch).catch(showError);
}

/**
 * The document shape ArcItem.toFirestoreMap writes on Android, field for field. A name that drifts
 * from the app's is dropped on its next read, so this list stays in step with that function.
 */
const toDocument = (item) => ({
  title: item.title,
  memo: item.memo,
  subItems: item.subItems.map((s) => ({
    title: s.title, done: s.done, endDate: s.endDate, linkedItemId: s.linkedItemId ?? null,
  })),
  majorCategory: item.majorCategory,
  minorCategory: item.minorCategory,
  startedAt: item.startedAt,
  endDate: item.endDate,
  isDone: item.isDone,
  priority: item.priority,
  position: item.position,
  // Written on every save so a shared 할 일 edited from either side keeps its member list: this
  // replaces the whole document, and a field left out of it would end the share silently.
  ownerUid: item.ownerUid ?? '',
  members: item.members ?? [],
});

const itemsRef = () => collection(state.db, 'users', state.uid, 'wishlist_items');
const sharedRef = () => collection(state.db, 'shared_items');

/** Where this 할 일 is stored, which its member list decides. */
const refFor = (item) => doc(isShared(item) ? sharedRef() : itemsRef(), item.id);

/**
 * Shares a 할 일 with another Google account, named by its uid.
 *
 * The first share moves the document out of this account's subtree into shared_items, under the
 * same id so a 세부항목 linked to it still resolves. One batch, so the item is never in both places
 * (two rows) or neither (lost) — which a write followed by a delete would allow.
 */
async function share(item, withUid) {
  const shared = {
    ...item,
    ownerUid: isShared(item) ? item.ownerUid : state.uid,
    members: [...new Set([state.uid, ...(item.members ?? []), withUid])],
  };
  const batch = writeBatch(state.db);
  batch.set(doc(sharedRef(), item.id), toDocument(shared));
  if (!isShared(item)) batch.delete(doc(itemsRef(), item.id));
  await batch.commit();
}

/** Local midnight today, which is how every date in a document is stored. */
const todayMillis = () => {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
};

/**
 * Sends one 세부항목 to another account, leaving the rest of the 할 일 private.
 *
 * A 세부항목 is an element of an array inside a document, so it cannot be shared on its own. What
 * it can do is become a 할 일, which is what linkedItemId has meant since links were added. So the
 * step is promoted into its own 할 일, born straight into shared_items with the recipient in it,
 * and the step it came from is left pointing at it.
 *
 * One batch, so the parent is never left pointing at a 할 일 that was not created — a link to
 * nothing, which the table would quietly draw as an ordinary step.
 */
async function sendSubItem(parent, index, withUid) {
  const step = parent.subItems[index];
  if (!step || step.linkedItemId) return;

  // Generated here rather than by addDoc, because the parent has to point at it in the same batch.
  const childRef = doc(sharedRef());
  const child = {
    title: step.title,
    // Where it came from, since the recipient gets the step and not the 할 일 around it.
    memo: `「${parent.title}」의 세부항목`,
    subItems: [],
    majorCategory: parent.majorCategory,
    minorCategory: parent.minorCategory,
    startedAt: todayMillis(),
    endDate: step.endDate ?? parent.endDate,
    isDone: step.done,
    priority: parent.priority,
    position: Date.now(),
    ownerUid: state.uid,
    members: [...new Set([state.uid, withUid])],
  };
  const linked = {
    ...parent,
    subItems: parent.subItems.map((s, at) => (at === index ? { ...s, linkedItemId: childRef.id } : s)),
  };

  const batch = writeBatch(state.db);
  batch.set(childRef, toDocument(child));
  batch.set(refFor(linked), toDocument(linked));
  await batch.commit();
}

/**
 * Stops sharing. The owner takes the 할 일 back — it returns to their own subtree, keeping its id —
 * while anyone else simply drops out of the member list, leaving it with its owner.
 */
async function unshare(item) {
  if (!isShared(item)) return;
  if (item.ownerUid === state.uid) {
    const batch = writeBatch(state.db);
    batch.set(doc(itemsRef(), item.id), toDocument({ ...item, ownerUid: '', members: [] }));
    batch.delete(doc(sharedRef(), item.id));
    await batch.commit();
  } else {
    await updateDoc(doc(sharedRef(), item.id), {
      members: item.members.filter((m) => m !== state.uid),
    });
  }
}

/** Opens the editor on an existing 할 일, or on a new one when given null, and saves the result. */
async function edit(item) {
  const majors = [...new Set(state.items.map((i) => i.majorCategory).filter(Boolean))].sort(
    (a, b) => a.localeCompare(b, 'ko'));
  const result = await showEditor(item, {
    majorCategories: majors,
    linkCandidates: state.items,
    hideCompletedSubItems: state.hideCompleted,
    myUid: state.uid,
    minorCategoriesFor: (major) => [...new Set(state.items
      .filter((i) => i.majorCategory === major)
      .map((i) => i.minorCategory)
      .filter(Boolean))].sort((a, b) => a.localeCompare(b, 'ko')),
  });
  if (!result) return;

  try {
    if (result.action === 'delete') {
      await deleteDoc(refFor(item));
    } else if (result.action === 'share') {
      const withUid = await resolveRecipient(result.recipient);
      if (!withUid) {
        showError(new Error(`${result.recipient} 로 가입한 Arc 계정을 찾지 못했습니다. `
          + '상대가 Arc에 한 번 로그인한 뒤 다시 시도하거나, 상대의 UID를 받아 넣어주세요.'));
      } else if (withUid === state.uid) {
        showError(new Error('자기 자신과는 공유할 수 없습니다.'));
      } else {
        // The share carries the form as it stands, so edits made in this sitting are saved by the
        // same write instead of being left behind.
        await share(result.item, withUid);
      }
    } else if (result.action === 'unshare') {
      await unshare(result.item);
    } else if (result.action === 'send') {
      const withUid = await resolveRecipient(result.recipient);
      if (!withUid) {
        showError(new Error(`${result.recipient} 로 가입한 Arc 계정을 찾지 못했습니다. `
          + '상대가 Arc에 한 번 로그인한 뒤 다시 시도하거나, 상대의 UID를 받아 넣어주세요.'));
      } else if (withUid === state.uid) {
        showError(new Error('자기 자신에게는 보낼 수 없습니다.'));
      } else {
        await sendSubItem(result.item, result.index, withUid);
      }
    } else if (result.item.id) {
      await setDoc(refFor(result.item), toDocument(result.item));
    } else {
      // A 할 일 is always born private; sharing it is a separate, deliberate step.
      await addDoc(itemsRef(), toDocument(result.item));
    }
  } catch (error) {
    showError(error);
  }
}

/* ------------------------------------------------------------------ render */

const dark = () => matchMedia('(prefers-color-scheme: dark)').matches;

const focusedItem = () => {
  const id = state.focusStack[state.focusStack.length - 1];
  return id ? state.items.find((i) => i.id === id) ?? null : null;
};

function render() {
  // A target deleted on another device would otherwise strand the page on a title and a table that
  // no longer have anything behind them.
  while (state.focusStack.length && state.items.length && !focusedItem()) state.focusStack.pop();

  const focused = focusedItem();
  $('title').textContent = focused ? focused.title : 'Arc';
  $('back').hidden = !focused;

  $('sort').value = state.sortField;
  $('direction').textContent = state.ascending ? '↑ 오름차순' : '↓ 내림차순';
  $('hide-completed').checked = state.hideCompleted;

  const all = buildRows(state.items, state);
  const rows = focused ? all.filter((r) => r.item.id === focused.id) : all;
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

/**
 * The 할 일 column. A shared item carries a trailing ⇄ — the arrows go both ways because either
 * side can tick it and every member sees the tick — and says so on hover, which the phone's table
 * has no room for.
 */
function titleCell(row) {
  // Under a grouped sort the 항목 is already its own line above, so repeating it on each child
  // would just be noise.
  const repeated = row.grouped && row.subIndex >= 0;
  const shared = isShared(row.item);
  const td = cell(repeated ? '' : row.item.title + (shared ? '  \u21c4' : ''), 'title');
  if (!repeated && shared) td.title = '다른 계정과 공유 중인 할 일입니다.';
  return td;
}

function tableRow(row, isDark) {
  const { item, subItem } = row;
  const due = effectiveEndDate(row);
  const done = rowIsDone(row);

  const linkedId = subItem?.linkedItemId ?? null;
  const linkTarget = linkedId ? state.items.find((i) => i.id === linkedId) : null;

  const tr = document.createElement('tr');
  tr.dataset.editable = '';
  tr.onclick = (e) => {
    if (e.target.closest('.check')) return;
    // Opening the link is what a tap on it means; the 할 일 it names is edited from its own screen.
    if (linkTarget) { state.focusStack.push(linkTarget.id); render(); } else { edit(item); }
  };
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
    titleCell(row),
    cell(
      subItem ? subItem.title + (linkedId ? '  \u203a' : '')
        : (row.subIndex < 0 && item.subItems.length ? '' : '-'),
      linkedId ? 'sub linked' : 'sub',
    ),
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
$('fab').onclick = () => edit(null);
$('back').onclick = () => { state.focusStack.pop(); render(); };

/**
 * Hands over this account's uid, which is the only name one person can give another to be shared a
 * 할 일: a browser cannot look an account up by email address. The clipboard can be refused — an
 * insecure origin, a browser that asks first — so there is a window to copy it out of by hand.
 */
$('copy-uid').onclick = async () => {
  if (!state.uid) return;
  const button = $('copy-uid');
  try {
    await navigator.clipboard.writeText(state.uid);
    button.textContent = '복사했습니다';
    setTimeout(() => { button.textContent = '내 UID 복사'; }, 2000);
  } catch {
    prompt('이 UID를 할 일을 공유해 줄 상대에게 보내주세요.', state.uid);
  }
};
matchMedia('(prefers-color-scheme: dark)').addEventListener('change', render);

for (const field of SORT_FIELDS) {
  const option = document.createElement('option');
  option.value = field.id;
  option.textContent = field.label;
  $('sort').append(option);
}

start().catch(showError);
