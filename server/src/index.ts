import { config } from './config';
import { openDb } from './db';
import { createApp } from './app';

const db = await openDb();
const server = createApp(db);

server.listen(config.port, () => {
  console.log(`Glass HR API  →  http://localhost:${config.port}/api/health   (DB: ${db.dialect})`);
});

const shutdown = async () => {
  server.close();
  await db.close();
  process.exit(0);
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
