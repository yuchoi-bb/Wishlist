/**
 * The rules as a user will paste them, run against the real Firestore rules engine.
 *
 * Every claim the README and the UI make about who can do what is one case here.
 */
import { readFileSync } from 'node:fs';
import {
  assertFails, assertSucceeds, initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection, deleteDoc, doc, getDoc, getDocs, setDoc, updateDoc,
} from 'firebase/firestore';
import { createHash } from 'node:crypto';

const ME = 'me-uid';
const FRIEND = 'friend-uid';
const STRANGER = 'stranger-uid';
const MY_EMAIL = 'me@example.com';

const digest = (email) => createHash('sha256').update(email.trim().toLowerCase()).digest('hex');

let pass = 0;
let fail = 0;
const check = async (label, promise) => {
  try {
    await promise;
    pass += 1;
    console.log(`  ✓ ${label}`);
  } catch (error) {
    fail += 1;
    console.log(`  ✗ ${label}\n      ${error.message.split('\n')[0]}`);
  }
};

const env = await initializeTestEnvironment({
  projectId: 'rules-test',
  firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8'), host: '127.0.0.1', port: 8080 },
});

const as = (uid, token = {}) => env.authenticatedContext(uid, token).firestore();
const anon = () => env.unauthenticatedContext().firestore();

// Seed past the rules, the way the app's own data already exists.
await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, 'users', ME, 'wishlist_items', 'mine'), { title: '내 할 일' });
  await setDoc(doc(db, 'shared_items', 'ours'), {
    title: '함께 하는 일', ownerUid: ME, members: [ME, FRIEND], subItems: [],
  });
  await setDoc(doc(db, 'email_index', digest(MY_EMAIL)), { uid: ME });
});

console.log('\n내 subtree (기존 규칙 그대로)');
await check('내 할 일을 내가 읽는다', assertSucceeds(getDoc(doc(as(ME), 'users', ME, 'wishlist_items', 'mine'))));
await check('남의 할 일은 못 읽는다', assertFails(getDoc(doc(as(STRANGER), 'users', ME, 'wishlist_items', 'mine'))));
await check('로그인 안 하면 못 읽는다', assertFails(getDoc(doc(anon(), 'users', ME, 'wishlist_items', 'mine'))));

console.log('\n공유된 할 일');
await check('주인이 읽는다', assertSucceeds(getDoc(doc(as(ME), 'shared_items', 'ours'))));
await check('공유받은 사람이 읽는다', assertSucceeds(getDoc(doc(as(FRIEND), 'shared_items', 'ours'))));
await check('남은 못 읽는다', assertFails(getDoc(doc(as(STRANGER), 'shared_items', 'ours'))));
await check(
  '공유받은 사람이 내용을 고친다 (체크의 핵심)',
  assertSucceeds(updateDoc(doc(as(FRIEND), 'shared_items', 'ours'), { subItems: [{ title: '한 단계', done: true }] })),
);
await check(
  '공유받은 사람은 상대를 더 못 넣는다',
  assertFails(updateDoc(doc(as(FRIEND), 'shared_items', 'ours'), { members: [ME, FRIEND, STRANGER] })),
);
await check(
  '공유받은 사람은 주인을 못 바꾼다',
  assertFails(updateDoc(doc(as(FRIEND), 'shared_items', 'ours'), { ownerUid: FRIEND })),
);
await check(
  '공유받은 사람은 못 지운다',
  assertFails(deleteDoc(doc(as(FRIEND), 'shared_items', 'ours'))),
);
await check(
  '주인은 상대를 더 넣는다',
  assertSucceeds(updateDoc(doc(as(ME), 'shared_items', 'ours'), { members: [ME, FRIEND, STRANGER] })),
);
await check(
  '남은 자기를 끼워 넣을 수 없다',
  assertFails(updateDoc(doc(as('nobody'), 'shared_items', 'ours'), { members: [ME, FRIEND, 'nobody'] })),
);

await env.withSecurityRulesDisabled(async (ctx) => {
  await setDoc(doc(ctx.firestore(), 'shared_items', 'ours'), {
    title: '함께 하는 일', ownerUid: ME, members: [ME, FRIEND], subItems: [],
  });
});
await check(
  '공유받은 사람이 스스로 빠진다 (공유에서 나가기)',
  assertSucceeds(updateDoc(doc(as(FRIEND), 'shared_items', 'ours'), { members: [ME] })),
);

console.log('\n공유 시작 — 주인이 자기 subtree에서 shared_items로 옮긴다');
await check(
  '내가 주인으로 만든다',
  assertSucceeds(setDoc(doc(as(ME), 'shared_items', 'new1'), { title: 'x', ownerUid: ME, members: [ME, FRIEND] })),
);
await check(
  '남을 주인으로 꾸며 만들 수 없다',
  assertFails(setDoc(doc(as(STRANGER), 'shared_items', 'new2'), { title: 'x', ownerUid: ME, members: [ME, STRANGER] })),
);
await check(
  '나를 빼고 만들 수 없다',
  assertFails(setDoc(doc(as(ME), 'shared_items', 'new3'), { title: 'x', ownerUid: ME, members: [FRIEND] })),
);
await check('주인은 지운다', assertSucceeds(deleteDoc(doc(as(ME), 'shared_items', 'new1'))));

console.log('\n이메일 → UID 변환표');
await check(
  '아는 이메일은 찾을 수 있다',
  assertSucceeds(getDoc(doc(as(FRIEND), 'email_index', digest(MY_EMAIL)))),
);
await check(
  '목록으로는 긁어갈 수 없다 (이메일 전체가 새지 않음)',
  assertFails(getDocs(collection(as(FRIEND), 'email_index'))),
);
await check(
  '내 이메일로 내 uid를 등록한다',
  assertSucceeds(setDoc(
    doc(as('new-user', { email: 'New.User@Example.com' }), 'email_index', digest('new.user@example.com')),
    { uid: 'new-user' },
  )),
);
await check(
  '남의 이메일을 내 uid로 가로챌 수 없다',
  assertFails(setDoc(
    doc(as(STRANGER, { email: 'stranger@example.com' }), 'email_index', digest(MY_EMAIL)),
    { uid: STRANGER },
  )),
);
await check(
  '내 이메일로 남의 uid를 넣을 수 없다',
  assertFails(setDoc(
    doc(as(STRANGER, { email: 'stranger@example.com' }), 'email_index', digest('stranger@example.com')),
    { uid: ME },
  )),
);
await check(
  '로그인 없이는 아무것도 못 읽는다',
  assertFails(getDoc(doc(anon(), 'email_index', digest(MY_EMAIL)))),
);

await env.cleanup();
console.log(`\n통과 ${pass} / 실패 ${fail}`);
process.exit(fail === 0 ? 0 : 1);
