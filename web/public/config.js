/**
 * Only needed when the page is NOT served by Firebase Hosting — deployed, app.js reads the
 * project's own settings from /__/firebase/init.json and ignores this file entirely.
 *
 * These values are not secrets. They identify the project, they are visible in any browser that
 * loads the page, and Firestore's rules are what actually protect the data. Fill them from
 * Firebase Console → 프로젝트 설정 → 내 앱 → 웹 앱.
 */
export const firebaseConfig = {
  apiKey: '여기에_apiKey',
  authDomain: 'up-wishlist.firebaseapp.com',
  projectId: 'up-wishlist',
  appId: '여기에_appId',
};
