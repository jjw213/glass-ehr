import { createServer, type Server } from 'node:http';
import type { Db } from './db';
import { config } from './config';
import { HttpError, parseCookies, readJson, sendJson, serveStatic, type Ctx } from './http';
import { buildRoutes } from './routes';

export function createApp(db: Db): Server {
  const router = buildRoutes(db);

  return createServer(async (req, res) => {
    const url = new URL(req.url ?? '/', 'http://localhost');
    const started = Date.now();
    try {
      if (url.pathname.startsWith('/api/')) {
        // CSRF guard: state-changing requests must be JSON (browsers can't send that cross-site without CORS)
        if (req.method === 'POST' && !String(req.headers['content-type'] ?? '').includes('application/json')) {
          throw new HttpError(415, 'Content-Type 은 application/json 이어야 해요');
        }
        const m = router.match(req.method ?? 'GET', url.pathname);
        if (!m) throw new HttpError(404, '없는 API 예요');
        const ctx: Ctx = {
          req, res, params: m.params,
          body: await readJson(req),
          cookies: parseCookies(req.headers.cookie),
          ip: String(req.headers['x-forwarded-for'] ?? req.socket.remoteAddress ?? '').split(',')[0].trim(),
        };
        const data = await m.handler(ctx);
        sendJson(res, 200, data);
      } else if (req.method === 'GET' && serveStatic(config.staticDir, url.pathname, res)) {
        return;
      } else {
        sendJson(res, 404, { error: 'Not found' });
      }
    } catch (e) {
      if (e instanceof HttpError) {
        sendJson(res, e.status, { error: e.message });
      } else {
        console.error(`[${req.method} ${url.pathname}]`, e);
        sendJson(res, 500, { error: '서버 오류가 발생했어요' });
      }
    } finally {
      if (url.pathname.startsWith('/api/') && process.env.LOG_REQUESTS !== 'false') {
        console.log(`${req.method} ${url.pathname} ${res.statusCode} ${Date.now() - started}ms`);
      }
    }
  });
}
