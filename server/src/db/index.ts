import { config } from '../config';
import type { Db } from './types';

export async function openDb(): Promise<Db> {
  if (config.dbClient === 'sqlite') {
    const { createSqliteDb } = await import('./sqlite');
    return createSqliteDb(config.sqliteFile);
  }
  if (!config.oracle.user || !config.oracle.password) {
    throw new Error('ORACLE_USER / ORACLE_PASSWORD 가 비어 있어요. server/.env 를 확인하세요.');
  }
  const { createOracleDb } = await import('./oracle');
  return createOracleDb(config.oracle);
}

export type { Db } from './types';
