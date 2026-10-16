/**
 * The HTTP front door, shaped for Cloud Run.
 *
 * Stateless on purpose: Cloud Run starts and stops instances freely, so two requests of one
 * conversation need not reach the same process. A session-keeping transport would hand out an id
 * that the next instance has never heard of. With `sessionIdGenerator: undefined` each request
 * carries everything it needs, and a fresh server and transport are built per request and closed
 * with it — cheap, and nothing to lose when an instance goes away mid-conversation.
 */
import express, { type Request, type Response } from 'express';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { ArcStore } from './store.js';
import { registerArcTools } from './tools.js';

function required(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`환경변수 ${name}가 필요합니다.`);
  return value;
}

const uid = required('ARC_USER_UID');
const token = required('ARC_MCP_TOKEN');
const timeZone = process.env.ARC_TIMEZONE?.trim() || 'Asia/Seoul';
const port = Number(process.env.PORT ?? 8080);

const store = new ArcStore(uid, timeZone, process.env.FIREBASE_SERVICE_ACCOUNT?.trim() || undefined);

const app = express();
app.use(express.json({ limit: '1mb' }));

/**
 * Nothing but the shared secret stands between the internet and the user's items, because the
 * Admin SDK answers to no security rule: firestore.rules only governs clients that sign in, and
 * this process is privileged. So the check is on every request and the comparison is
 * length-independent only in the sense that the token is fixed-size — a mismatch returns 401
 * before any Firestore call is made.
 *
 * Accepted either as a bearer header or as the last path segment, since not every MCP client lets
 * you set a header on a custom connector.
 */
function authorized(req: Request): boolean {
  const header = req.header('authorization');
  if (header?.startsWith('Bearer ') && header.slice(7).trim() === token) return true;
  return req.params.token === token;
}

async function handleMcp(req: Request, res: Response): Promise<void> {
  if (!authorized(req)) {
    res.status(401).json({ error: 'unauthorized' });
    return;
  }

  const server = new McpServer({ name: 'arc', version: '1.0.0' });
  registerArcTools(server, store, timeZone);

  const transport = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
  // Closing the transport with the response is what keeps a stateless instance from leaking one
  // server per request.
  res.on('close', () => {
    void transport.close();
    void server.close();
  });

  try {
    await server.connect(transport);
    await transport.handleRequest(req, res, req.body);
  } catch (error) {
    console.error('MCP 요청 처리 실패', error);
    if (!res.headersSent) res.status(500).json({ error: 'internal error' });
  }
}

app.post('/mcp', handleMcp);
app.post('/mcp/:token', handleMcp);

// Cloud Run's own check, which must not require the secret.
app.get('/healthz', (_req, res) => {
  res.json({ ok: true, timeZone });
});

app.listen(port, '0.0.0.0', () => {
  console.log(`arc-mcp listening on ${port} (uid ${uid.slice(0, 8)}…, ${timeZone})`);
});
