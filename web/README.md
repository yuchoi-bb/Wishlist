# Arc on the web

The same table, from the same Firestore, over the same Google sign-in. Open it on a desktop and
the eight columns the phone makes you swipe for are all on screen at once.

There is no server. The browser talks to Firestore directly, with the user's own sign-in, and
`firestore.rules` — `request.auth.uid == userId` — decides what it may see. Signing in as anyone
else shows their own empty list. **The Android app was not changed for this**, and does not know
this exists: both ends hold a snapshot listener, so a tick on either shows up on the other within
about a second.

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

Dates go in as epoch millis at local midnight, which `new Date(y, m, d)` gives directly — the
browser is in the same zone as the phone, so nothing has to be converted the way ../mcp does it.

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
| `public/app.js` | Firebase wiring and rendering |
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
