/**
 * 인사기록카드 화면의 개인정보 표시 규칙 (#15). 규칙 정리는 docs/employee-screen-design.md 3장.
 *
 * - 주민등록번호: 화면에는 서버가 준 마스킹 값만 보인다. 원문 보기는 인사·급여 담당만(docs/rrn-design.md 3장,
 *   backend RequestActor.canRevealRrn 과 같음 — 관리자는 원문 불가). 원문 조회는 서버가 매번 로그를 남긴다.
 * - 일반 직원은 다른 사원의 인적사항(생년월일·주소·이력·메모)과 근로계약·근태를 볼 수 없다
 *   (최소 노출 원칙, 개인정보 보호법 제3조로 보고 있으나 원문 재확인 필요 — docs/employee-screen-design.md 5장).
 *
 * 이 규칙은 화면 표시용이다. 서버 쪽 권한 검사는 백엔드 인증·권한 이슈에서 붙인다(docs/employee-design.md 5장).
 */
import type { Role } from '../api/types';

/** 화면이 아는 역할. 백엔드 RequestActor.Role 과 같다(급여담당 PAYROLL 포함). */
export type ViewerRole = Role | 'PAYROLL';

const HR_STAFF: ReadonlySet<ViewerRole> = new Set<ViewerRole>(['HR', 'PAYROLL', 'ADMIN']);
const RRN_REVEAL: ReadonlySet<ViewerRole> = new Set<ViewerRole>(['HR', 'PAYROLL']);
const REGISTER: ReadonlySet<ViewerRole> = new Set<ViewerRole>(['HR', 'ADMIN']);

/** 인적사항·근로계약·근태 요약을 그대로 볼 수 있는가 */
export const canViewPersonal = (r: ViewerRole) => HR_STAFF.has(r);
/** 주민등록번호 원문 보기 버튼을 보일 수 있는가 */
export const canRevealRrn = (r: ViewerRole) => RRN_REVEAL.has(r);
/** 신규 사원 등록 */
export const canRegisterEmployee = (r: ViewerRole) => REGISTER.has(r);
/** 사원 목록 내려받기(서버 권한 EMP_EXPORT: 관리자·인사·급여) */
export const canExportEmployees = (r: ViewerRole) => HR_STAFF.has(r);

export const HIDDEN = '비공개';

/** 생년월일은 전부 가린다(나이도 개인정보). */
export const maskBirthDate = (d: string | null) => (d ? '****-**-**' : '');

/** 주소는 첫 단위(시·도)만 남긴다. */
export const maskAddress = (a: string | null) => {
  if (!a || !a.trim()) return '';
  const first = a.trim().split(/\s+/)[0];
  return `${first} ***`;
};

/** 권한에 따라 개인정보 칸의 표시 문자열을 정한다. */
export function shownPersonal(role: ViewerRole, v: { birthDate: string; address: string; careerHistory: string | null; rrnMasked: string | null }) {
  if (canViewPersonal(role)) {
    return { birthDate: v.birthDate, address: v.address, careerHistory: v.careerHistory ?? '', rrn: v.rrnMasked ?? '미등록' };
  }
  return { birthDate: maskBirthDate(v.birthDate), address: maskAddress(v.address), careerHistory: HIDDEN, rrn: HIDDEN };
}
