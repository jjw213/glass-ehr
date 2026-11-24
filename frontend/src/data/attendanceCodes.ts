/**
 * 데모(VITE_API=mock)용 근태코드 시드(#143). 백엔드 V038__attendance_code.sql 과 같게 유지한다
 * (src/api/attendanceCodes.test.ts 가 SQL 파일과 비교한다).
 * 법정 기준값(legal_rate)은 데모에 두지 않는다(값은 서버 기준값 테이블에서만 읽는다). 데모 화면은 '기준값 미등록'으로 보인다.
 */
import type { AttendanceCode, LegalValue } from '../api/attendanceCodes';
import type { CodeGroup } from '../api/codes';

export const ATTENDANCE_CODE_GROUPS: CodeGroup[] = [
  { groupCode: 'LEAVE_TYPE', name: '휴가 종류', description: '연차·법정휴가·회사 약정휴가·무급휴가. 근태관리 > 근태코드 관리에서 편집', sortOrder: 70, active: true, system: true, editPage: 'attendanceCodes' },
  { groupCode: 'ATTENDANCE_TYPE', name: '근태 구분', description: '출장·외근·재택·교육 등 근무로 인정되는 구분. 근태관리 > 근태코드 관리에서 편집', sortOrder: 80, active: true, system: true, editPage: 'attendanceCodes' },
];

/** 기본 코드별 법정 기준값 항목(백엔드 AttendanceCodeService.LEGAL_ITEMS 와 같은 키·이름). 데모는 값 없음. */
const legal = (...items: [string, string][]): LegalValue[] =>
  items.map(([itemCode, label]) => ({ itemCode, label, value: null, unit: 'DAYS', effectiveFrom: null }));
const LEGAL: Record<string, LegalValue[]> = {
  ANNUAL: legal(['ANNUAL_LEAVE_BASE_DAYS', '기본 일수'], ['ANNUAL_LEAVE_MONTHLY_DAYS', '1년 미만 매월 발생'], ['ANNUAL_LEAVE_MAX_DAYS', '최대 일수']),
  MATERNITY: legal(['MATERNITY_LEAVE_DAYS', '휴가 일수'], ['MATERNITY_LEAVE_MULTIPLE_DAYS', '휴가 일수(다태아)'],
    ['MATERNITY_LEAVE_PAID_DAYS', '유급 일수'], ['MATERNITY_LEAVE_MULTIPLE_PAID_DAYS', '유급 일수(다태아)']),
  SPOUSE_MATERNITY: legal(['SPOUSE_MATERNITY_LEAVE_DAYS', '휴가 일수'], ['SPOUSE_MATERNITY_LEAVE_PAID_DAYS', '유급 일수']),
  MENSTRUAL: legal(['MENSTRUAL_LEAVE_DAYS_PER_MONTH', '월 일수']),
  FAMILY_CARE: legal(['FAMILY_CARE_LEAVE_DAYS_PER_YEAR', '연간 일수'], ['FAMILY_CARE_LEAVE_PAID_DAYS', '유급 일수']),
  INFERTILITY_TREATMENT: legal(['INFERTILITY_TREATMENT_LEAVE_DAYS_PER_YEAR', '연간 일수'], ['INFERTILITY_TREATMENT_LEAVE_PAID_DAYS', '유급 일수']),
};

const SPOUSE_LAW = '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의2';
const FAMILY_LAW = '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제22조의2 제2항';
const INFERTILITY_LAW = '남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률 제18조의3';

const leave = (
  code: string, name: string, description: string, sortOrder: number, system: boolean, processType: string,
  d: { halfDay?: boolean; paid: boolean | null; proof: boolean; gender?: 'F' | 'M'; basis?: string },
): AttendanceCode => ({
  groupCode: 'LEAVE_TYPE', code, name, description, sortOrder, active: true, system, processType, inUse: false, usageCount: 0,
  leave: {
    unitDay: true, unitHalfDay: d.halfDay ?? false, unitHour: false, paid: d.paid, proofRequired: d.proof,
    annualLimitDays: null, genderLimit: d.gender ?? null, legalBasis: d.basis ?? null, legalValues: system ? LEGAL[code] ?? [] : [],
  },
});
const att = (code: string, name: string, sortOrder: number): AttendanceCode => ({
  groupCode: 'ATTENDANCE_TYPE', code, name, description: null, sortOrder, active: true, system: false, processType: code,
  inUse: false, usageCount: 0, leave: null,
});

export const ATTENDANCE_CODES: AttendanceCode[] = [
  leave('ANNUAL', '연차', '근로기준법 제60조 연차 유급휴가', 10, true, 'ANNUAL', { halfDay: true, paid: true, proof: false, basis: '근로기준법 제60조' }),
  leave('MATERNITY', '출산전후휴가', '근로기준법 제74조', 20, true, 'STATUTORY', { paid: null, proof: true, gender: 'F', basis: '근로기준법 제74조' }),
  leave('SPOUSE_MATERNITY', '배우자 출산휴가', SPOUSE_LAW, 30, true, 'STATUTORY', { paid: null, proof: true, basis: SPOUSE_LAW }),
  leave('MENSTRUAL', '생리휴가', '근로기준법 제73조', 40, true, 'STATUTORY', { paid: false, proof: false, gender: 'F', basis: '근로기준법 제73조(무급)' }),
  leave('FAMILY_CARE', '가족돌봄휴가', FAMILY_LAW, 50, true, 'STATUTORY', { paid: null, proof: false, basis: FAMILY_LAW }),
  leave('INFERTILITY_TREATMENT', '난임치료휴가', INFERTILITY_LAW, 60, true, 'STATUTORY', { paid: null, proof: true, basis: INFERTILITY_LAW }),
  leave('CONDOLENCE', '경조사', '회사 약정휴가 예시', 110, false, 'COMPANY', { paid: true, proof: true }),
  leave('SICK', '병가', '회사 약정휴가 예시(무급·증빙 필요)', 120, false, 'COMPANY', { paid: false, proof: true }),
  leave('FOUNDATION_DAY', '창립기념일', '회사 약정휴가 예시', 130, false, 'COMPANY', { paid: true, proof: false }),
  att('BUSINESS_TRIP', '출장', 10),
  att('OUTSIDE_WORK', '외근', 20),
  att('REMOTE', '재택근무', 30),
  att('EDUCATION', '교육', 40),
];
