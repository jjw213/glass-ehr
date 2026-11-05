/**
 * npm run db:init  → drops the HR_ tables, recreates them and loads sample data.
 * npm run db:init -- --schema-only  → tables only, no sample data.
 */
import { openDb } from '../db';
import { ddl, TABLES_IN_DROP_ORDER } from '../db/schema';
import { seed } from '../db/seed';

const schemaOnly = process.argv.includes('--schema-only');
const db = await openDb();
try {
  console.log(`DB: ${db.dialect}`);
  for (const t of TABLES_IN_DROP_ORDER) await db.dropTableIfExists(t);
  for (const stmt of ddl(db.dialect)) await db.exec(stmt);
  console.log(`✔ 테이블 ${TABLES_IN_DROP_ORDER.length}개 생성`);
  if (!schemaOnly) {
    await seed(db);
    const [r] = await db.query<{ n: number }>('SELECT COUNT(*) AS N FROM HR_EMPLOYEES');
    console.log(`✔ 샘플 데이터 입력 (직원 ${r.n}명). 로그인: hr / ${process.env.SEED_PASSWORD ?? '1234'}`);
  }
} finally {
  await db.close();
}
