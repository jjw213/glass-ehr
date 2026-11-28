/**
 * 그룹 관리자 법인 관리 API (#130). 백엔드: backend/ EnterCompanyAdminController (권한 ENTER_COMPANY_MANAGE).
 * 고객사는 세션에서 정한다(요청에 넣지 않는다). 새 법인 코드는 만들 법인의 코드라 본문으로 보낸다.
 */
import { call } from './http';
import { ApiError } from './types';

export interface AdminCompany { enterCd: string; enterName: string; bizRegNo: string; active: boolean; sortOrder: number }
export interface NewCompany {
  enterCd: string; enterName: string; bizRegNo: string; sourceEnterCd: string; adminLoginId: string; adminPassword: string;
}
/** copied: 표 이름 → 복사한 행 수. skippedTemplates: 특정 사원을 결재자로 지정해 복사하지 않은 결재 양식(신청 종류). */
export interface CreateCompanyResult {
  company: AdminCompany; copied: Record<string, number>; skippedTemplates: string[]; adminLoginId: string;
}

export interface EnterCompanyAdminApi {
  list(): Promise<AdminCompany[]>;
  create(input: NewCompany): Promise<CreateCompanyResult>;
  setActive(enterCd: string, active: boolean): Promise<AdminCompany>;
}

export const httpEnterCompanyAdminApi: EnterCompanyAdminApi = {
  list: () => call('GET', '/admin/enter-companies'),
  create: (input) => call('POST', '/admin/enter-companies', input),
  setActive: (enterCd, active) => call('PUT', `/admin/enter-companies/${encodeURIComponent(enterCd)}/active`, { active }),
};

const CODE = /^[A-Z0-9]{2,10}$/;
const BIZ = /^[0-9]{10}$/;

/** 입력 검사. 백엔드 EnterCompanyAdminService.create 와 같은 규칙(서버도 다시 검사한다). */
export function validateNewCompany(c: NewCompany): string | null {
  if (!CODE.test(c.enterCd.trim())) return '법인 코드는 영문 대문자·숫자 2~10자예요';
  const name = c.enterName.trim();
  if (name.length < 1 || name.length > 100) return '법인 이름은 1~100자로 입력해 주세요';
  if (!BIZ.test(c.bizRegNo.replace(/-/g, '').trim())) return '사업자등록번호는 숫자 10자리예요';
  if (!c.sourceEnterCd) return '복사할 원본 법인을 골라 주세요';
  const id = c.adminLoginId.trim();
  if (id.length < 1 || id.length > 50) return '관리자 아이디는 1~50자로 입력해 주세요';
  if (c.adminPassword.length < 8 || c.adminPassword.length > 200) return '관리자 초기 비밀번호는 8~200자로 입력해 주세요';
  return null;
}

/** 표 이름 → 화면 이름 */
export const COPY_LABELS: Record<string, string> = {
  common_code_group: '공통코드 그룹', common_code: '공통코드', attendance_leave_type: '휴가 종류',
  biz_site: '사업장', work_pattern: '근무유형', holiday: '공휴일·회사휴일', approval_template: '결재 양식',
};

/** 데모 모드·테스트용 메모리 구현. 법인 코드·사업자등록번호는 전체에서 유일(409). 로그인·보고 있는 법인은 중지 불가. */
export function createMockEnterCompanyAdminApi(current: () => string = () => 'C001'): EnterCompanyAdminApi {
  const companies: AdminCompany[] = [
    { enterCd: 'C001', enterName: '글래스HR(예시)', bizRegNo: '0000000001', active: true, sortOrder: 10 },
    { enterCd: 'C002', enterName: '글래스물류(예시)', bizRegNo: '0000000002', active: true, sortOrder: 20 },
  ];
  const find = (cd: string) => companies.find((c) => c.enterCd === cd);
  return {
    list: async () => companies.map((c) => ({ ...c })).sort((a, b) => a.sortOrder - b.sortOrder || a.enterCd.localeCompare(b.enterCd)),
    create: async (input) => {
      const err = validateNewCompany(input);
      if (err) throw new ApiError(400, err);
      if (!find(input.sourceEnterCd)) throw new ApiError(404, '복사할 원본 법인을 찾을 수 없어요');
      const bizRegNo = input.bizRegNo.replace(/-/g, '').trim();
      const enterCd = input.enterCd.trim();
      if (companies.some((c) => c.enterCd === enterCd || c.bizRegNo === bizRegNo)) {
        throw new ApiError(409, '이미 쓰는 법인 코드나 사업자등록번호예요');
      }
      const company: AdminCompany = {
        enterCd, enterName: input.enterName.trim(), bizRegNo, active: true,
        sortOrder: Math.max(0, ...companies.map((c) => c.sortOrder)) + 10,
      };
      companies.push(company);
      const copied = { common_code_group: 3, common_code: 12, attendance_leave_type: 4, biz_site: 1, work_pattern: 2, holiday: 15, approval_template: 2 };
      return { company: { ...company }, copied, skippedTemplates: [], adminLoginId: input.adminLoginId.trim() };
    },
    setActive: async (enterCd, active) => {
      const c = find(enterCd);
      if (!c) throw new ApiError(404, '법인을 찾을 수 없어요');
      if (!active && enterCd === current()) throw new ApiError(409, '로그인한 법인이나 지금 보고 있는 법인은 사용 중지할 수 없어요');
      c.active = active;
      return { ...c };
    },
  };
}

export const enterCompanyAdminApi = import.meta.env.VITE_API === 'mock' ? createMockEnterCompanyAdminApi() : httpEnterCompanyAdminApi;
