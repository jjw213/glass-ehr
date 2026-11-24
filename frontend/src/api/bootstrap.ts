/**
 * 로그인 직후 화면 데이터(#77). 사용자(/api/auth/me + 사원 목록의 본인 행)·부서(/api/org/tree)·홈 요약(/api/dashboard/summary)·
 * 메시지 대화방(/api/threads, #80)은 Spring 에서 받고, 아직 옮기지 않은 결재·휴가·근태 토글·급여명세는 Node /api/bootstrap 에서 받는다.
 * 급여명세는 급여 모듈 전까지 쓰지 않는다(#81: payslips=null → 급여 화면 '준비 중').
 * Node 응답의 me·depts·employees·dashboard·threads·payslips 는 쓰지 않는다(연차 일수만 휴가 모듈을 옮길 때까지 Node 값을 쓴다).
 */
import { flattenDepts, seoulToday, type DeptNode, type EmployeeSummary, type OrgTree } from './employees';
import {
  canSeeDashboard, DEPT_COLORS, menuRole,
  type AppData, type AuthMe, type Bootstrap, type DashboardSummary, type Dept, type EnterCompany, type MessageThread,
  type SessionUser,
} from './types';

type Get = <T>(path: string) => Promise<T>;

/** Spring 조직 트리 → 화면 부서 목록(정렬 순서대로, 색은 순서대로 돌려 쓴다). desc 는 상위 경로. */
export function toDepts(roots: DeptNode[]): Dept[] {
  return flattenDepts(roots).map((d, i) => ({
    id: String(d.deptId), name: d.name, color: DEPT_COLORS[i % DEPT_COLORS.length], desc: d.path, lead: '',
  }));
}

/**
 * 로그인 사용자. 사원과 연결되지 않은 계정(관리자 등)이거나 목록에 본인이 없으면 이름 대신 로그인 아이디.
 * 법인 이름은 사용 중 법인 목록에서 찾고, 없으면 코드를 보여 준다(#120).
 */
export function toSessionUser(auth: AuthMe, self: EmployeeSummary | undefined, annualLeave: number, companies: EnterCompany[] = []): SessionUser {
  return {
    loginId: auth.loginId, empId: auth.empId,
    name: self?.name ?? auth.loginId,
    dept: self?.currentDeptId != null ? String(self.currentDeptId) : '',
    role: menuRole(auth.roles), roles: auth.roles, permissions: auth.permissions,
    enterCd: auth.enterCd,
    enterName: companies.find((c) => c.enterCd === auth.enterCd)?.enterName ?? auth.enterCd,
    homeEnterCd: auth.homeEnterCd,
    annualLeave,
  };
}

export async function loadAppData(get: Get): Promise<AppData> {
  // 사원·부서·요약·메시지는 서버가 세션의 현재 법인(#120 전환 포함) 조건으로 거른다. 화면은 법인 코드를 보내지 않는다.
  const [node, auth, tree, threads, companies] = await Promise.all([
    get<Bootstrap>('/bootstrap'),
    get<AuthMe>('/auth/me'),
    get<OrgTree>(`/org/tree?date=${encodeURIComponent(seoulToday())}`),
    get<MessageThread[]>('/threads'),
    get<EnterCompany[]>('/enter-companies'),
  ]);
  // 사원 목록은 권한에 맞게 서버가 거른다(일반 사원은 본인만). 요약은 볼 권한이 있을 때만 부른다(없으면 403).
  const [list, dashboard] = await Promise.all([
    auth.empId == null ? Promise.resolve<EmployeeSummary[]>([]) : get<EmployeeSummary[]>('/employees'),
    canSeeDashboard(auth.permissions) ? get<DashboardSummary>('/dashboard/summary') : Promise.resolve(null),
  ]);
  const self = list.find((e) => e.empId === auth.empId);
  // Node 의 attendance·threads 는 쓰지 않는다. 출퇴근은 Spring /api/attendance/punches(#78), 메시지는 Spring /api/threads(#80).
  // Node 의 payslips 도 쓰지 않는다. 급여명세서는 급여 모듈에서 새로 만든다(#81).
  const { approvals, leaves } = node;
  return {
    me: toSessionUser(auth, self, node.me.annualLeave, companies),
    depts: toDepts(tree.roots),
    // 결재는 아직 Node 의 hr·mgmt 같은 ID를 쓰므로 Spring 부서와 별도로 조회에 보존한다.
    legacyDepts: node.depts,
    dashboard,
    approvals, leaves, payslips: null, threads, companies,
  };
}
