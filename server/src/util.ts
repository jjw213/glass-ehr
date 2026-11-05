import { randomUUID } from 'node:crypto';

export const newId = (prefix = '') => prefix + randomUUID().replace(/-/g, '').slice(0, 20).toUpperCase();

/** local calendar date 'YYYY-MM-DD' (server time zone, set TZ=Asia/Seoul) */
export function ymd(d = new Date()) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
export const addDays = (d: Date, n: number) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };

/** Monday of the week containing d */
export function monday(d = new Date()) {
  const x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  const wd = (x.getDay() + 6) % 7; // Mon=0
  return addDays(x, -wd);
}

/** weekdays between two YYYY-MM-DD dates, inclusive */
export function businessDays(from: string, to: string) {
  const a = new Date(from + 'T00:00:00'), b = new Date(to + 'T00:00:00');
  if (isNaN(+a) || isNaN(+b) || b < a) return 0;
  let n = 0;
  for (const d = new Date(a); d <= b; d.setDate(d.getDate() + 1)) if (d.getDay() % 6 !== 0) n++;
  return n;
}

export const hhmm = (iso: string) => {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

/** 'MM.DD' for list rows */
export const mmdd = (iso: string) => {
  const d = new Date(iso);
  return `${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`;
};
