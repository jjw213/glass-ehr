/** 조직도 화면(#13)의 순수 계산. 화면 없이 테스트한다. */
import type { DeptNode, EmployeeSummary } from '../api/org';

/** Asia/Seoul 기준 오늘 'YYYY-MM-DD' (AGENTS.md: 날짜는 서울 기준). */
export const seoulToday = (now = new Date()) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);

/**
 * 기준일에 재직 중인지. 입사일 당일부터 퇴직일 당일까지 재직으로 본다
 * (docs/employee-design.md 2장, EmployeeDeptMemberChecker 와 같은 기준).
 */
export const isEmployedOn = (e: EmployeeSummary, date: string) => e.hireDate <= date && (e.retireDate === null || date <= e.retireDate);

/**
 * 기준일 부서별 재직자. 사원은 아직 현재 소속(currentDeptId)만 있어서, 과거 기준일에도 지금 소속 부서로 묶인다.
 * 인사발령 이슈에서 발령 이력(기준일 소속)으로 바꿔야 한다.
 */
export function membersByDept(employees: EmployeeSummary[], date: string): Map<number, EmployeeSummary[]> {
  const out = new Map<number, EmployeeSummary[]>();
  for (const e of employees) {
    if (e.currentDeptId === null || !isEmployedOn(e, date)) continue;
    const l = out.get(e.currentDeptId);
    if (l) l.push(e); else out.set(e.currentDeptId, [e]);
  }
  for (const l of out.values()) l.sort((a, b) => (a.empNo < b.empNo ? -1 : a.empNo > b.empNo ? 1 : 0));
  return out;
}

export interface Headcount { direct: number; total: number }

/** 부서별 인원 수. direct = 그 부서 소속, total = 하위부서까지 합친 수. */
export function headcounts(roots: DeptNode[], members: Map<number, EmployeeSummary[]>): Map<number, Headcount> {
  const out = new Map<number, Headcount>();
  const walk = (n: DeptNode): number => {
    const direct = members.get(n.deptId)?.length ?? 0;
    const total = direct + n.children.reduce((s, c) => s + walk(c), 0);
    out.set(n.deptId, { direct, total });
    return total;
  };
  roots.forEach(walk);
  return out;
}

export interface FlatDept { node: DeptNode; depth: number; path: string }

/** 위에서 아래로 펼친 목록(상위부서 선택 목록용). path = '경영지원본부 › 인사팀'. */
export function flatten(roots: DeptNode[]): FlatDept[] {
  const out: FlatDept[] = [];
  const walk = (n: DeptNode, depth: number, prefix: string) => {
    const path = prefix ? `${prefix} › ${n.name}` : n.name;
    out.push({ node: n, depth, path });
    n.children.forEach((c) => walk(c, depth + 1, path));
  };
  roots.forEach((r) => walk(r, 0, ''));
  return out;
}

export function findDept(roots: DeptNode[], id: number): DeptNode | undefined {
  for (const r of roots) {
    if (r.deptId === id) return r;
    const f = findDept(r.children, id);
    if (f) return f;
  }
  return undefined;
}

/** 자기 자신과 모든 하위부서 id. 이동할 상위부서 후보에서 뺀다(순환 방지, 서버도 다시 검사). */
export function selfAndDescendants(n: DeptNode): Set<number> {
  const s = new Set<number>();
  const walk = (x: DeptNode) => { s.add(x.deptId); x.children.forEach(walk); };
  walk(n);
  return s;
}

/** 'YYYY-MM-DD' 다음 날 (변경 적용일 최솟값 계산용). */
export function nextDay(date: string): string {
  const [y, m, d] = date.split('-').map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + 1));
  return t.toISOString().slice(0, 10);
}
