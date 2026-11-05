/** npm run db:sql → writes sql/schema.oracle.sql (for DBAs / SQL Developer) */
import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { ddl, TABLES_IN_DROP_ORDER } from '../db/schema';

const out = [
  '-- Glass HR schema for Oracle Database (generated from server/src/db/schema.ts — edit that file, not this one)',
  '-- Run as the application schema user, e.g. in SQL Developer or SQLcl.',
  '',
  '-- drop (ignore ORA-00942 errors on a fresh schema)',
  ...TABLES_IN_DROP_ORDER.map((t) => `-- DROP TABLE ${t} CASCADE CONSTRAINTS PURGE;`),
  '',
  ...ddl('oracle').map((s) => s + ';\n'),
].join('\n');
const file = resolve(import.meta.dirname, '..', '..', 'sql', 'schema.oracle.sql');
writeFileSync(file, out);
console.log('wrote', file);
