import oracledb from 'oracledb';
import type { Binds, Db, Row } from './types';
import { lowerKeys } from './types';

/**
 * Oracle adapter (node-oracledb, Thin mode — no Instant Client needed).
 * Works with Oracle Database 12.1 and later, including 19c, 21c XE and 23ai Free.
 */
export async function createOracleDb(cfg: { user: string; password: string; connectString: string; poolMax?: number }): Promise<Db> {
  oracledb.outFormat = oracledb.OUT_FORMAT_OBJECT;
  oracledb.fetchAsString = [oracledb.CLOB];

  const pool = await oracledb.createPool({
    user: cfg.user,
    password: cfg.password,
    connectString: cfg.connectString,
    poolMin: 1,
    poolMax: cfg.poolMax ?? 8,
    poolIncrement: 1,
  });

  const run = async (conn: oracledb.Connection, sql: string, binds: Binds, autoCommit: boolean) =>
    conn.execute(sql, binds as oracledb.BindParameters, { autoCommit });

  const make = (getConn: () => Promise<oracledb.Connection>, release: (c: oracledb.Connection) => Promise<void>, autoCommit: boolean): Db => ({
    dialect: 'oracle',
    async query<T = Row>(sql: string, binds: Binds = {}) {
      const conn = await getConn();
      try {
        const r = await run(conn, sql, binds, false);
        return ((r.rows ?? []) as Record<string, unknown>[]).map(lowerKeys) as T[];
      } finally { await release(conn); }
    },
    async exec(sql: string, binds: Binds = {}) {
      const conn = await getConn();
      try {
        const r = await run(conn, sql, binds, autoCommit);
        return r.rowsAffected ?? 0;
      } finally { await release(conn); }
    },
    async tx<T>(fn: (db: Db) => Promise<T>) {
      const conn = await pool.getConnection();
      const inner = make(async () => conn, async () => {}, false);
      try {
        const out = await fn(inner);
        await conn.commit();
        return out;
      } catch (e) {
        await conn.rollback();
        throw e;
      } finally {
        await conn.close();
      }
    },
    async dropTableIfExists(table: string) {
      try {
        const conn = await getConn();
        try { await conn.execute(`DROP TABLE ${table} CASCADE CONSTRAINTS PURGE`); } finally { await release(conn); }
      } catch (e) {
        // ORA-00942: table or view does not exist
        if (!String((e as Error).message).includes('ORA-00942')) throw e;
      }
    },
    async close() { await pool.close(5); },
  });

  return make(() => pool.getConnection(), (c) => c.close(), true);
}
