export type Bind = string | number | Date | null;
export type Binds = Record<string, Bind>;
/** Rows always come back with lower-case column names, whatever the database. */
export type Row = Record<string, unknown>;

export type Dialect = 'oracle' | 'sqlite';

export interface Db {
  readonly dialect: Dialect;
  /** SELECT → rows */
  query<T = Row>(sql: string, binds?: Binds): Promise<T[]>;
  /** INSERT / UPDATE / DELETE / DDL → affected row count */
  exec(sql: string, binds?: Binds): Promise<number>;
  /** run fn inside one transaction (commit on success, rollback on throw) */
  tx<T>(fn: (db: Db) => Promise<T>): Promise<T>;
  dropTableIfExists(table: string): Promise<void>;
  close(): Promise<void>;
}

/** Oracle returns DATE/TIMESTAMP as Date, SQLite as ISO text. Normalise to ISO string. */
export function toIso(v: unknown): string | null {
  if (v == null) return null;
  if (v instanceof Date) return v.toISOString();
  return String(v);
}

export const lowerKeys = (r: Record<string, unknown>): Row =>
  Object.fromEntries(Object.entries(r).map(([k, v]) => [k.toLowerCase(), v]));
