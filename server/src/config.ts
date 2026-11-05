import { existsSync } from 'node:fs';
import { resolve } from 'node:path';

// load server/.env if present (Node ≥ 21.7 built-in, no dotenv needed)
const envFile = resolve(import.meta.dirname, '..', '.env');
if (existsSync(envFile)) process.loadEnvFile(envFile);

const env = (k: string, d?: string) => {
  const v = process.env[k] ?? d;
  if (v === undefined) throw new Error(`환경변수 ${k} 가 필요해요 (server/.env 참고)`);
  return v;
};

export const config = {
  port: Number(env('PORT', '4000')),
  dbClient: env('DB_CLIENT', 'oracle') as 'oracle' | 'sqlite',
  oracle: {
    user: process.env.ORACLE_USER ?? '',
    password: process.env.ORACLE_PASSWORD ?? '',
    connectString: process.env.ORACLE_CONNECT_STRING ?? 'localhost:1521/FREEPDB1',
  },
  sqliteFile: env('SQLITE_FILE', resolve(import.meta.dirname, '..', 'dev.sqlite')),
  sessionHours: Number(env('SESSION_HOURS', '12')),
  /** set to true when served over HTTPS */
  cookieSecure: env('COOKIE_SECURE', 'false') === 'true',
  /** folder with the built frontend (npm run build → dist). Empty = API only. */
  staticDir: process.env.STATIC_DIR ?? resolve(import.meta.dirname, '..', '..', 'dist'),
};
