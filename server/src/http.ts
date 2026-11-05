import type { IncomingMessage, ServerResponse } from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { extname, join, normalize, resolve } from 'node:path';

/** Throw this from a handler to send a JSON error with a status code. */
export class HttpError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export interface Ctx {
  req: IncomingMessage;
  res: ServerResponse;
  params: Record<string, string>;
  body: unknown;
  cookies: Record<string, string>;
  ip: string;
}
type Handler = (ctx: Ctx) => Promise<unknown> | unknown;
interface Route { method: string; re: RegExp; keys: string[]; handler: Handler }

/** Tiny router: router.get('/api/x/:id', ctx => data). Returning a value sends it as JSON. */
export class Router {
  private routes: Route[] = [];
  private add(method: string, path: string, handler: Handler) {
    const keys: string[] = [];
    const re = new RegExp('^' + path.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '/?$');
    this.routes.push({ method, re, keys, handler });
  }
  get(p: string, h: Handler) { this.add('GET', p, h); }
  post(p: string, h: Handler) { this.add('POST', p, h); }

  match(method: string, path: string) {
    for (const r of this.routes) {
      if (r.method !== method) continue;
      const m = r.re.exec(path);
      if (m) return { handler: r.handler, params: Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])])) };
    }
    return null;
  }
}

export function parseCookies(header = ''): Record<string, string> {
  const out: Record<string, string> = {};
  for (const part of header.split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}

export async function readJson(req: IncomingMessage, limit = 100_000): Promise<unknown> {
  if (req.method === 'GET' || req.method === 'HEAD') return undefined;
  let size = 0;
  const chunks: Buffer[] = [];
  for await (const c of req) {
    size += (c as Buffer).length;
    if (size > limit) throw new HttpError(413, '요청이 너무 커요');
    chunks.push(c as Buffer);
  }
  if (!chunks.length) return undefined;
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new HttpError(400, '잘못된 JSON 이에요'); }
}

export function sendJson(res: ServerResponse, status: number, data: unknown) {
  const body = JSON.stringify(data ?? null);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(body);
}

const MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.ico': 'image/x-icon', '.json': 'application/json',
};

/** Serve the built frontend; unknown paths fall back to index.html. Returns false when no build exists. */
export function serveStatic(dir: string, urlPath: string, res: ServerResponse): boolean {
  if (!dir || !existsSync(join(dir, 'index.html'))) return false;
  const root = resolve(dir);
  let file = normalize(join(root, decodeURIComponent(urlPath)));
  if (!file.startsWith(root)) file = join(root, 'index.html');
  if (!existsSync(file) || statSync(file).isDirectory()) file = join(root, 'index.html');
  const immutable = file.includes(`${root}/assets/`) || file.includes(`${root}\\assets\\`);
  res.writeHead(200, {
    'Content-Type': MIME[extname(file)] ?? 'application/octet-stream',
    'Cache-Control': immutable ? 'public, max-age=31536000, immutable' : 'no-cache',
  });
  createReadStream(file).pipe(res);
  return true;
}
