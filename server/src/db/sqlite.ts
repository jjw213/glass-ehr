import { DatabaseSync } from 'node:sqlite';
import type { Binds, Db, Row } from './types';
import { lowerKeys } from './types';

/**
 * Local stand-in for development and tests only (Node 22.13+ built-in SQLite).
 * The app's SQL is written in the subset both Oracle and SQLite understand,
 * so the same queries run here and on Oracle.
 */
export function createSqliteDb(file: string): Db {
  const db = new DatabaseSync(file);
  db.exec('PRAGMA foreign_keys = ON');

  /**
   * Mimic Oracle's strictness so tests catch what Oracle would reject:
   * every :bind must be supplied (ORA-01008) and must not be a reserved word (ORA-01745).
   * (Extra binds are already rejected by node:sqlite, like Oracle's ORA-01036.)
   */
  const check = (sql: string, b: Binds) => {
    const names = new Set([...sql.replace(/'[^']*'/g, '').matchAll(/(?<![:\w]):([a-zA-Z]\w*)/g)].map((m) => m[1]));
    for (const n of names) {
      if (!(n in b)) throw new Error(`bind :${n} 값이 없어요 (Oracle ORA-01008)`);
      if (ORACLE_RESERVED.has(n.toUpperCase())) throw new Error(`bind 이름 :${n} 은 Oracle 예약어예요 (ORA-01745)`);
    }
  };

  const norm = (b: Binds) =>
    Object.fromEntries(Object.entries(b).map(([k, v]) => [k, v instanceof Date ? v.toISOString() : v]));

  let depth = 0;
  const self: Db = {
    dialect: 'sqlite',
    async query<T = Row>(sql: string, binds: Binds = {}) {
      check(sql, binds);
      return (db.prepare(sql).all(norm(binds)) as Record<string, unknown>[]).map(lowerKeys) as T[];
    },
    async exec(sql: string, binds: Binds = {}) {
      if (!Object.keys(binds).length && /^\s*(create|drop|alter)\b/i.test(sql)) { db.exec(sql); return 0; }
      check(sql, binds);
      return Number(db.prepare(sql).run(norm(binds)).changes);
    },
    async tx<T>(fn: (d: Db) => Promise<T>) {
      if (depth > 0) return fn(self);
      depth++;
      db.exec('BEGIN');
      try { const out = await fn(self); db.exec('COMMIT'); return out; }
      catch (e) { db.exec('ROLLBACK'); throw e; }
      finally { depth--; }
    },
    async dropTableIfExists(table: string) { db.exec(`DROP TABLE IF EXISTS ${table}`); },
    async close() { db.close(); },
  };
  return self;
}

/** Oracle SQL reserved words (cannot be used as unquoted identifiers or bind names). */
export const ORACLE_RESERVED = new Set(`ACCESS ADD ALL ALTER AND ANY AS ASC AUDIT BETWEEN BY CHAR CHECK CLUSTER COLUMN COMMENT
COMPRESS CONNECT CREATE CURRENT DATE DECIMAL DEFAULT DELETE DESC DISTINCT DROP ELSE EXCLUSIVE EXISTS FILE FLOAT FOR FROM
GRANT GROUP HAVING IDENTIFIED IMMEDIATE IN INCREMENT INDEX INITIAL INSERT INTEGER INTERSECT INTO IS LEVEL LIKE LOCK LONG
MAXEXTENTS MINUS MLSLABEL MODE MODIFY NOAUDIT NOCOMPRESS NOT NOWAIT NULL NUMBER OF OFFLINE ON ONLINE OPTION OR ORDER PCTFREE
PRIOR PRIVILEGES PUBLIC RAW RENAME RESOURCE REVOKE ROW ROWID ROWNUM ROWS SELECT SESSION SET SHARE SIZE SMALLINT START
SUCCESSFUL SYNONYM SYSDATE TABLE THEN TO TRIGGER UID UNION UNIQUE UPDATE USER VALIDATE VALUES VARCHAR VARCHAR2 VIEW
WHENEVER WHERE WITH`.split(/\s+/));
