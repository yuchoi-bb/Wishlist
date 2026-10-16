# arc-mcp

An MCP server that files 할 일 into Arc. With it connected, an assistant can be told "이거 Arc에
할 일로 넣어줘" and the item appears on the phone — no copying, no share sheet.

## Why no app change was needed

Arc keeps its items in Firestore under `users/{uid}/wishlist_items/{id}`, and the Android app
subscribes with a snapshot listener. A write from anywhere reaches the phone in about a second on
its own. This server is simply another writer; the app does not know it exists.

```
Claude / ChatGPT ──MCP over HTTPS──▶ arc-mcp ──Admin SDK──▶ Firestore ──listener──▶ Arc 앱
```

## Tools

| Tool | Does |
|---|---|
| `list_categories` | 대분류/중분류 already in use — call first so items are filed under existing ones |
| `list_tasks` | Items, newest deadline first; `includeDone`, `majorCategory`, `dueBefore` filters |
| `add_task` | One 항목, with its 세부항목, dates, 분류 and 우선순위 |
| `update_task` | Merges onto the stored item; `subItems` replaces the whole list |
| `complete_task` | Marks 완료 |
| `delete_task` | Removes it, on every device |

## Two things to get right

**Dates are local midnight.** Arc stores every date as epoch millis at the start of the day in the
*device's* zone. Cloud Run runs in UTC, so `ARC_TIMEZONE` decides what midnight means; leave it at
`Asia/Seoul` unless the phone is elsewhere. Set it wrong and every date reads one day off in the
app, and 남은날짜 counts down from the wrong one.

**The Admin SDK ignores `firestore.rules`.** Those rules only constrain clients that sign in. This
process is privileged, so `ARC_MCP_TOKEN` is the only thing between the internet and the items:
make it long and random, and treat it like a password. `ARC_USER_UID` is fixed by configuration
rather than chosen per request, so a caller who got through still cannot reach another account.

## Configuration

| Variable | Required | Meaning |
|---|---|---|
| `ARC_USER_UID` | yes | Firebase Auth uid to write under. Firebase Console → Authentication → Users (the app's 설정 screen only shows the first 8 characters) |
| `ARC_MCP_TOKEN` | yes | Shared secret. `openssl rand -hex 32` |
| `ARC_TIMEZONE` | no | Defaults to `Asia/Seoul` |
| `FIREBASE_SERVICE_ACCOUNT` | no | Service-account JSON. Omit on Cloud Run, where the attached service account is picked up automatically |
| `PORT` | no | Set by Cloud Run; 8080 otherwise |

## Deploying to Cloud Run

Run from this directory, against the same Google Cloud project as the Firebase app.

```bash
PROJECT=<firebase-project-id>
TOKEN=$(openssl rand -hex 32)

gcloud run deploy arc-mcp \
  --source . \
  --project "$PROJECT" \
  --region asia-northeast3 \
  --allow-unauthenticated \
  --set-env-vars "ARC_USER_UID=<your-uid>,ARC_MCP_TOKEN=$TOKEN,ARC_TIMEZONE=Asia/Seoul"

echo "token: $TOKEN"
```

`--allow-unauthenticated` lets the AI client reach it; `ARC_MCP_TOKEN` is what actually guards it.
The deploy prints a service URL — the MCP endpoint is that URL plus `/mcp/<token>`.

Grant the service account Firestore access once:

```bash
SA=$(gcloud run services describe arc-mcp --region asia-northeast3 \
       --project "$PROJECT" --format 'value(spec.template.spec.serviceAccountName)')
gcloud projects add-iam-policy-binding "$PROJECT" \
  --member "serviceAccount:$SA" --role roles/datastore.user
```

Check it answers: `curl https://<service-url>/healthz` → `{"ok":true,"timeZone":"Asia/Seoul"}`.
That path needs no token, so it is safe to use from anywhere.

## Connecting a client

**Claude** — Settings → Connectors → Add custom connector, URL `https://<service-url>/mcp/<token>`.

**ChatGPT** — custom connectors, behind Developer Mode. Which plans may add one has moved around;
check OpenAI's help centre for the current rule.

**Gemini** — the consumer app does not reliably take a custom MCP server. Gemini CLI does, via
`mcpServers` in `~/.gemini/settings.json`.

Clients that let you set a header can use `Authorization: Bearer <token>` against plain `/mcp`
instead, which keeps the secret out of URLs and logs. The path form exists because several do not.

## Running it locally

```bash
npm install
npm run build
FIRESTORE_EMULATOR_HOST=127.0.0.1:8777 GOOGLE_CLOUD_PROJECT=arc-test \
ARC_USER_UID=testuid ARC_MCP_TOKEN=dev PORT=8099 npm start
```

With `FIRESTORE_EMULATOR_HOST` set, the Admin SDK talks to the emulator
(`firebase emulators:start --only firestore`) and needs no credentials, so tool calls can be
exercised without touching real data.

## A note on the stateless transport

The transport is built with `sessionIdGenerator: undefined`. Cloud Run scales instances up and
down freely, so two requests in one conversation need not land on the same process; a transport
that handed out a session id would offer one the next instance has never heard of. Each request
instead carries everything it needs, and its server and transport are closed with the response.
