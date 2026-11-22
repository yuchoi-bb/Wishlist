# Arc on the web

The same table, from the same Firestore, over the same Google sign-in. Open it on a desktop and
the eight columns the phone makes you swipe for are all on screen at once.

There is no server. The browser talks to Firestore directly, with the user's own sign-in, and
`firestore.rules` decides what it may see: a private 할 일 under `users/{uid}` is reachable only by
that account, a shared one under `shared_items` only by the accounts in its member list. Signing in
as anyone else shows their own empty list. Both ends hold a snapshot listener, so a tick on either
shows up on the other within about a second.

## What it does

- The 표 the app shows: 대분류 · 중분류 · 할 일 · 세부항목 · 예정일 · 남은날짜 · 순위 · 완료
- Month bands and the per-month row wash, under the 예정일 sort, as on the phone
- The category colour stripe, including colours picked in the app's 분류 색 settings
- All six sorts, either direction, and 완료항목 숨기기
- Ticking 완료 — a 세부항목 line flips that step, an 항목 line flips the whole item, neither
  cascading into the other, which is what the app's own toggles do
- Adding, editing and deleting, through the same editor the app has, in its field order:
  할 일 → 우선순위 → 최종 종료일 → 세부항목 → 분류 → 메모, with 시작일 and 완료 at the end.
  A row opens it; 할 일 추가 opens an empty one. 대/중분류 suggest what is already in use, and a
  new 세부항목 inherits the 항목's 최종 종료일, as the app's editor does.

- Hiding completed 세부항목 inside the editor (완료 숨기기), starting from whatever 완료항목 숨기기
  is set to for the table. Hiding only changes what is drawn — hidden steps are still saved.
- Reordering 세부항목 with ↑ ↓ in the editor. Stored order is the order the table shows them
  under their 항목, so moving one here moves it everywhere.
- Linking a 세부항목 to another 할 일 (**할 일 연결** in the editor). The row carries a ›; opening
  it narrows the table to that 할 일's own steps, and 뒤로 returns. The target stays an ordinary
  항목 with its own place in the table — a link is a way in, not a second kind of thing.
- Sharing one 할 일 with another Google account (**공유** in the editor). The row carries a ⇄ and
  both sides see each other's ticks live. See 공유 below.

Dates go in as epoch millis at local midnight, which `new Date(y, m, d)` gives directly — the
browser is in the same zone as the phone, so nothing has to be converted the way ../mcp does it.

## 공유 — 한 할 일을 다른 Google 계정과

A 할 일 is private until it is shared, and sharing is per item: nothing else in the account becomes
visible. Sharing **moves** the document from `users/{uid}/wishlist_items/{id}` to
`shared_items/{id}`, keeping its id so a 세부항목 linked to it still resolves, and both halves of
the move go in one batch so the item is never in two places or none.

Who can do what, as `firestore.rules` enforces it:

| | 주인 (ownerUid) | 공유받은 사람 |
|---|---|---|
| 내용 보기 · 고치기 · 완료 체크 | ✅ | ✅ |
| 공유 상대 추가 | ✅ | ❌ |
| 공유 중단 | ✅ 되가져옴 | ✅ 자기만 빠짐 |
| 할 일 삭제 | ✅ | ❌ |

**Naming the other account.** Type their Google email address into **사람 추가**.

Firebase Auth cannot be asked for an account by email from a client — only a server holding the
Admin SDK can — so the address is resolved through `email_index`, a small directory each account
writes itself at sign-in. A document there is named by the **SHA-256 of the lowercased address**
and holds nothing but a uid, and the rules allow `get` but never `list`: an address you already
know can be looked up, while the collection cannot be read out as a list of everybody's email
addresses. They also check that same hash against the writer's own sign-in token, so an address
can only ever be registered by the account that owns it.

Two things follow. **The other person has to have opened Arc at least once** — before that they are
not in the directory and the share says so rather than failing quietly. And a uid still works in
the same box, for exactly that case: they read theirs from **내 UID 복사** (the header here, 설정 in
the app) and send it over.

**Publish the rules first.** `firestore.rules` at the repository root gained the `shared_items`
and `email_index` blocks; until it is pasted into Firebase Console → Firestore Database → Rules,
every client reports `공유 할 일을 읽을 권한이 없습니다` and shares fail. The blocks only add
collections, so publishing before the app and the web catch up breaks nothing.

The rules are covered by a test against the real rules engine (`@firebase/rules-unit-testing`
against the Firestore emulator): twenty-three cases, one per claim the table above makes, plus the
directory's own — that an address can be resolved but the collection cannot be listed, and that
nobody can register an address that isn't theirs.

## Deploying

Firebase Hosting is on the free Spark plan, so unlike the MCP server in `../mcp` this needs no
billing account.

```bash
cd web
npx -y firebase-tools login --no-localhost       # once
npx -y firebase-tools deploy --only hosting
```

`.firebaserc` already names the project (`up-wishlist`). The deploy prints the address, normally
`https://up-wishlist.web.app`.

**Register a Web app first**, if the project has none: Firebase Console → 프로젝트 설정 → 내 앱 →
웹 앱 추가. Hosting then serves the project's own settings at `/__/firebase/init.json`, which is
where the page reads them — nothing to paste. `public/config.js` is only the fallback for opening
the page some other way, and is filled in by hand.

Google sign-in needs the domain on Firebase Console → Authentication → Settings → 승인된 도메인.
`web.app` and `firebaseapp.com` are there by default, so a plain Hosting deploy needs no change; a
custom domain does.

### 자동 배포 (한 번만 설정하면 끝)

`.github/workflows/deploy-web.yml` deploys on every push that touches `web/`, so after the setup
below nobody has to run the CLI again.

It needs one GitHub secret — a Google service account that may publish to Hosting. The Firebase
CLI creates the account, grants it, and uploads the secret in one go:

```bash
cd web
npx -y firebase-tools init hosting:github
```

It asks for the repository (`yuchoi-bb/Wishlist`) and opens a browser to authorise GitHub. Answer
**no** when it offers to set up a workflow file — one is already here, pointed at this repo's
layout; the generated one assumes `firebase.json` sits at the root.

The secret it creates is named `FIREBASE_SERVICE_ACCOUNT_UP_WISHLIST`, which is the name the
workflow reads. If you make the key by hand instead (GCP Console → IAM → 서비스 계정, role
**Firebase Hosting Admin**, JSON key), paste the whole JSON into a repository secret under that
exact name: GitHub → Settings → Secrets and variables → Actions.

The workflow only watches the branch named in its `on.push.branches`. If the work moves to another
branch, change it there too, or pushes keep succeeding while the site stops updating.

Running it by hand: GitHub → Actions → Deploy web → **Re-run all jobs** on a finished run. The
**Run workflow** button is not there, and will not be until this workflow file reaches the
repository's default branch — GitHub reads `workflow_dispatch` only from that branch, whatever
branch the run would target. Pushing to `web/` is the normal trigger either way.

The secret is the service account key file verbatim. Copying it out of a terminal breaks it: long
lines wrap, the wrap becomes a real newline inside `private_key`, and Firebase then fails with
`Bad control character in string literal ... have you run firebase login?`, which points at the
wrong thing entirely. Set it from the file and skip the clipboard:

```bash
gh secret set FIREBASE_SERVICE_ACCOUNT_UP_WISHLIST --repo yuchoi-bb/Wishlist < key.json
```

The workflow parses the secret before it deploys, so a damaged one is named as damaged.

### Trying it locally

```bash
cd web
npx -y firebase-tools emulators:start --only hosting
```

The Hosting emulator serves `/__/firebase/init.json` too, so sign-in and live data work against the
real project without deploying.

## Files

| | |
|---|---|
| `public/index.html` | Markup and the app's palette, light and dark |
| `public/table.js` | Rows, sorts, colours, dates — no Firebase, no DOM, so it can be tested alone |
| `public/app.js` | Firebase wiring, sharing, and rendering |
| `public/config.js` | Fallback settings for running off Hosting |

`table.js` is a second implementation of what `ArcRepository`, `CategoryColors`, `MonthColors` and
`DateOnly` do on Android. Where the two disagree the phone is right.

Two deliberate near-misses:

- **중분류 shades.** The 대분류 hue matches exactly — same palette, same `String.hashCode`, checked
  against a real JVM. The shade applied for a 중분류 can sit a step off, because Compose
  interpolates colour in Oklab and this mixes in sRGB.
- **Korean ordering.** `Intl.Collator('ko')` for `java.text.Collator`. The same in practice, not
  guaranteed identical on unusual strings.

## On a phone

Everything but 대분류 and 중분류 stays on screen below 640px; the colour stripe still says which
category a row belongs to. The phone has the real app, so this is the glance version.
