/**
 * 휴일 달력 API (#26). 백엔드: backend/ HolidayController (Spring). 응답 형식은 backend HolidayDtos 와 같다.
 *
 * - 법정공휴일·대체공휴일(관공서의 공휴일에 관한 규정 제2조·제3조)은 근로기준법 제55조 제2항에 따른 유급휴일이다.
 *   이 날짜들은 화면·에이전트가 만들지 않고, 사람이 넣은 CSV(출처·검증일 포함)를 불러와서만 등록한다.
 * - 휴일 대체(제55조 제2항 단서)는 근로자대표 서면합의 문서를 반드시 연결해야 한다. 최종 검사는 서버가 한다.
 */
import { call } from './http';
import { ApiError } from './types';

export type HolidayKind = 'PUBLIC' | 'SUBSTITUTE' | 'COMPANY' | 'REPLACEMENT';
export const KIND_LABEL: Record<HolidayKind, string> = {
  PUBLIC: '법정공휴일', SUBSTITUTE: '대체공휴일', COMPANY: '회사휴일', REPLACEMENT: '대체된 휴일',
};
export const isStatutory = (k: HolidayKind) => k === 'PUBLIC' || k === 'SUBSTITUTE';

export interface HolidayRef { holidayId: number; date: string; name: string }
export interface Holiday {
  holidayId: number;
  date: string;
  name: string;
  kind: HolidayKind;
  kindLabel: string;
  paid: boolean;
  /** 실제로 쉬는 날. 휴일 대체로 근로일이 된 원래 공휴일은 false */
  effective: boolean;
  replacedBy: HolidayRef | null;
  replaces: HolidayRef | null;
  agreementId: number | null;
  agreementTitle: string | null;
  sourceRef: string | null;
  verifiedDate: string | null;
  note: string | null;
}
export type RepType = 'MAJORITY_UNION' | 'MAJORITY_REP';
export const REP_TYPE_LABEL: Record<RepType, string> = { MAJORITY_UNION: '과반수 노동조합', MAJORITY_REP: '근로자 과반수 대표자' };
export interface AgreementInput {
  title: string; repType: RepType; signedDate: string; effectiveFrom: string; effectiveTo: string | null;
  documentRef: string; note: string | null;
}
export interface Agreement extends AgreementInput { agreementId: number; kind: 'HOLIDAY_SUBSTITUTION' }
export interface CompanyHolidayInput { date: string; name: string; paid: boolean; note: string | null }
export interface SubstitutionInput { originalHolidayId: number; substituteDate: string; agreementId: number | null; note: string | null }
export interface ImportResult { inserted: number; skipped: number; rows: Holiday[] }

export interface HolidayApi {
  year(year: number): Promise<Holiday[]>;
  importCsv(csv: string): Promise<ImportResult>;
  createCompany(input: CompanyHolidayInput): Promise<Holiday>;
  substitute(input: SubstitutionInput): Promise<Holiday>;
  remove(holidayId: number): Promise<void>;
  agreements(): Promise<Agreement[]>;
  createAgreement(input: AgreementInput): Promise<Agreement>;
}

export const httpHolidayApi: HolidayApi = {
  year: (y) => call('GET', `/attendance/holidays?year=${y}`),
  importCsv: (csv) => call('POST', '/attendance/holidays/import', csv, { 'Content-Type': 'text/csv; charset=utf-8' }),
  createCompany: (i) => call('POST', '/attendance/holidays/company', i),
  substitute: (i) => call('POST', '/attendance/holidays/substitutions', i),
  remove: (id) => call('DELETE', `/attendance/holidays/${id}`),
  agreements: () => call('GET', '/attendance/agreements'),
  createAgreement: (i) => call('POST', '/attendance/agreements', i),
};

// ---- 공휴일 CSV (backend PublicHolidayCsv 와 같은 형식) ----

export const CSV_COLUMNS = ['날짜', '이름', '종류', '출처', '검증일'] as const;
export interface CsvRow { line: number; date: string; name: string; kind: HolidayKind; source: string; verified: string }

function splitLine(line: string): string[] {
  const out: string[] = [];
  let cur = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') {
      if (quoted && line[i + 1] === '"') { cur += '"'; i++; } else quoted = !quoted;
    } else if (c === ',' && !quoted) { out.push(cur); cur = ''; } else cur += c;
  }
  out.push(cur);
  return out;
}

/** 실제 달력에 있는 YYYY-MM-DD 인지 */
export function isIsoDate(s: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return false;
  const [y, m, d] = s.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  return dt.getUTCFullYear() === y && dt.getUTCMonth() === m - 1 && dt.getUTCDate() === d;
}

/** 형식 검사(서버가 다시 검사한다). 오류는 문자열로 돌려준다. */
export function parseHolidayCsv(text: string): CsvRow[] | string {
  const body = text.charCodeAt(0) === 0xfeff ? text.slice(1) : text;
  if (!body.trim()) return '공휴일 자료가 비어 있어요';
  const lines = body.split(/\r?\n/);
  const header = splitLine(lines[0]).map((s) => s.trim());
  const missing = CSV_COLUMNS.filter((c) => !header.includes(c));
  if (missing.length) return `머리글에 열이 없어요: ${missing.join(', ')} (필요한 열: ${CSV_COLUMNS.join(',')})`;
  const at = (cells: string[], col: string) => (cells[header.indexOf(col)] ?? '').trim();
  const rows: CsvRow[] = [];
  const seen = new Set<string>();
  for (let i = 1; i < lines.length; i++) {
    if (!lines[i].trim()) continue;
    const line = i + 1;
    const cells = splitLine(lines[i]);
    const date = at(cells, '날짜');
    const name = at(cells, '이름');
    const kindText = at(cells, '종류');
    const source = at(cells, '출처');
    const verified = at(cells, '검증일');
    if (!isIsoDate(date)) return `${line}번째 줄: 날짜는 YYYY-MM-DD로 써 주세요: ${date}`;
    if (!name || name.length > 100) return `${line}번째 줄: 이름은 1~100자로 써 주세요`;
    const kind = kindText === KIND_LABEL.PUBLIC ? 'PUBLIC' : kindText === KIND_LABEL.SUBSTITUTE ? 'SUBSTITUTE' : null;
    if (!kind) return `${line}번째 줄: 종류는 '법정공휴일' 또는 '대체공휴일'만 쓸 수 있어요: ${kindText}`;
    if (!source || source.length > 500) return `${line}번째 줄: 출처(근거 법령·링크)를 1~500자로 써 주세요`;
    if (!isIsoDate(verified)) return `${line}번째 줄: 검증일은 YYYY-MM-DD로 써 주세요: ${verified}`;
    if (seen.has(`${date}|${name}`)) return `${line}번째 줄: 같은 날짜·이름이 이미 있어요: ${date} ${name}`;
    seen.add(`${date}|${name}`);
    rows.push({ line, date, name, kind, source, verified });
  }
  return rows.length ? rows : '공휴일 자료에 행이 없어요';
}

// ---- 데모용 가짜 백엔드 ----

/**
 * 브라우저 안의 가짜 백엔드(VITE_API=mock). 공휴일 데이터는 넣지 않는다(사람이 넣는 자료). 데모에서는
 * CSV 를 불러와야 공휴일이 생긴다. 회사휴일 1건만 예시로 둔다. 서버와 같은 주요 규칙(서면합의 필수, 근로일로만 대체)을 흉내 낸다.
 */
export const seoulToday = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date());

export function createMockHolidayApi(today: () => string = seoulToday): HolidayApi {
  type Row = Omit<Holiday, 'effective' | 'replacedBy' | 'replaces' | 'agreementTitle' | 'kindLabel'> & { replacesId: number | null };
  let rows: Row[] = [];
  let agreements: Agreement[] = [];
  let nextId = 1;
  const y = Number(today().slice(0, 4));
  rows.push({ holidayId: nextId++, date: `${y}-05-10`, name: '창립기념일(예시)', kind: 'COMPANY', paid: true, agreementId: null, sourceRef: null, verifiedDate: null, note: '데모 예시', replacesId: null });
  const ref = (r: Row | undefined): HolidayRef | null => (r ? { holidayId: r.holidayId, date: r.date, name: r.name } : null);
  const view = (r: Row): Holiday => {
    const by = rows.find((x) => x.replacesId === r.holidayId);
    const { replacesId, ...rest } = r;
    return {
      ...rest, kindLabel: KIND_LABEL[r.kind], effective: !by, replacedBy: ref(by),
      replaces: ref(rows.find((x) => x.holidayId === replacesId)),
      agreementTitle: agreements.find((a) => a.agreementId === r.agreementId)?.title ?? null,
    };
  };
  const inEffect = (a: Agreement, d: string) => a.effectiveFrom <= d && (!a.effectiveTo || d < a.effectiveTo);
  return {
    async year(yy) { return rows.filter((r) => r.date.startsWith(`${yy}-`)).sort((a, b) => a.date.localeCompare(b.date) || a.holidayId - b.holidayId).map(view); },
    async importCsv(csv) {
      const parsed = parseHolidayCsv(csv);
      if (typeof parsed === 'string') throw new ApiError(400, parsed);
      const created: Row[] = [];
      let skipped = 0;
      for (const p of parsed) {
        const same = rows.find((r) => r.date === p.date && r.name === p.name);
        if (same) {
          if (same.kind !== p.kind) throw new ApiError(409, `${p.line}번째 줄: ${p.date} ${p.name}은(는) 이미 ${KIND_LABEL[same.kind]}(으)로 등록돼 있어요`);
          skipped++;
          continue;
        }
        created.push({ holidayId: nextId++, date: p.date, name: p.name, kind: p.kind, paid: true, agreementId: null, sourceRef: p.source, verifiedDate: p.verified, note: null, replacesId: null });
      }
      rows = [...rows, ...created];
      return { inserted: created.length, skipped, rows: created.map(view) };
    },
    async createCompany(i) {
      if (!isIsoDate(i.date)) throw new ApiError(400, '날짜를 입력해 주세요');
      if (!i.name.trim()) throw new ApiError(400, '휴일 이름을 입력해 주세요');
      if (rows.some((r) => r.date === i.date)) throw new ApiError(409, `${i.date}은(는) 이미 휴일 달력에 있어요`);
      const r: Row = { holidayId: nextId++, date: i.date, name: i.name.trim(), kind: 'COMPANY', paid: i.paid, agreementId: null, sourceRef: null, verifiedDate: null, note: i.note?.trim() || null, replacesId: null };
      rows = [...rows, r];
      return view(r);
    },
    async substitute(i) {
      if (i.agreementId === null) throw new ApiError(400, '근로자대표 서면합의 문서를 연결해 주세요(근로기준법 제55조 제2항 단서)');
      const a = agreements.find((x) => x.agreementId === i.agreementId);
      if (!a) throw new ApiError(400, `근로자대표 서면합의 문서가 없어요: ${i.agreementId}`);
      const o = rows.find((r) => r.holidayId === i.originalHolidayId);
      if (!o) throw new ApiError(404, `공휴일이 없어요: ${i.originalHolidayId}`);
      if (!isStatutory(o.kind)) throw new ApiError(400, '법정공휴일·대체공휴일만 휴일 대체할 수 있어요(근로기준법 제55조 제2항)');
      if (rows.some((r) => r.replacesId === o.holidayId)) throw new ApiError(409, '이미 다른 날로 대체됐어요');
      if (i.substituteDate === o.date) throw new ApiError(400, '대신 쉴 날은 원래 공휴일과 달라야 해요');
      if (rows.some((r) => r.date === i.substituteDate)) throw new ApiError(409, `${i.substituteDate}은(는) 이미 휴일이에요. 근로일로만 대체할 수 있어요`);
      if (!inEffect(a, o.date) || !inEffect(a, i.substituteDate)) throw new ApiError(422, '원래 공휴일과 대신 쉴 날이 모두 서면합의 유효기간 안이어야 해요');
      const earlier = o.date < i.substituteDate ? o.date : i.substituteDate;
      if (a.signedDate > earlier) throw new ApiError(422, '서면합의는 대체하는 날보다 먼저 체결돼 있어야 해요');
      const r: Row = { holidayId: nextId++, date: i.substituteDate, name: `${o.name} 대체`, kind: 'REPLACEMENT', paid: true, agreementId: a.agreementId, sourceRef: null, verifiedDate: null, note: i.note?.trim() || null, replacesId: o.holidayId };
      rows = [...rows, r];
      return view(r);
    },
    async remove(id) {
      const r = rows.find((x) => x.holidayId === id);
      if (!r) throw new ApiError(404, `휴일이 없어요: ${id}`);
      if (isStatutory(r.kind)) throw new ApiError(400, '법정공휴일·대체공휴일은 공휴일 자료로만 관리해요');
      const o = rows.find((x) => x.holidayId === r.replacesId);
      if (r.date <= today() || (o && o.date <= today())) throw new ApiError(409, '이미 지난 휴일은 지우거나 취소할 수 없어요');
      rows = rows.filter((x) => x !== r);
    },
    async agreements() { return structuredClone(agreements).sort((a, b) => b.signedDate.localeCompare(a.signedDate) || b.agreementId - a.agreementId); },
    async createAgreement(i) {
      if (!i.title.trim()) throw new ApiError(400, '합의서 제목을 입력해 주세요');
      if (!i.documentRef.trim()) throw new ApiError(400, '합의서 문서(파일 번호·보관 위치)를 입력해 주세요');
      if (!isIsoDate(i.signedDate) || !isIsoDate(i.effectiveFrom)) throw new ApiError(400, '체결일·효력 시작일을 입력해 주세요');
      if (i.effectiveTo && i.effectiveTo <= i.effectiveFrom) throw new ApiError(400, '합의 효력 종료일은 시작일보다 뒤여야 해요');
      const a: Agreement = { ...i, title: i.title.trim(), documentRef: i.documentRef.trim(), agreementId: agreements.length + 1, kind: 'HOLIDAY_SUBSTITUTION' };
      agreements = [...agreements, a];
      return structuredClone(a);
    },
  };
}

export const holidayApi: HolidayApi = import.meta.env.VITE_API === 'mock' ? createMockHolidayApi() : httpHolidayApi;
