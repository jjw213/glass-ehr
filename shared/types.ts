/**
 * API contract shared by the frontend (src/) and the server (server/).
 * Change a shape here and both sides get a type error until they agree.
 */

export type DeptId = string;
export type Role = 'EMPLOYEE' | 'HR' | 'ADMIN';
export type WorkStatus = '근무중' | '휴가' | '외근' | '재택' | '퇴사';
export type ApprovalStatus = '대기' | '승인' | '반려';
export type ApprovalKind = '휴가' | '지출' | '출장' | '채용' | '교육';
export type LeaveType = '연차' | '반차' | '병가' | '경조사';

export interface Me {
  id: string;
  loginId: string;
  name: string;
  dept: DeptId;
  position: string;
  role: Role;
  email: string;
  annualLeave: number;
}

export interface Dept { id: DeptId; name: string; color: string; desc: string; lead: string }

export interface Employee {
  id: string; name: string; dept: DeptId; position: string; email: string; phone: string;
  joined: string; status: WorkStatus; location: string;
}

export interface ApprovalStep { name: string; status: 'WAIT' | 'APPROVED' | 'REJECTED' }
export interface Approval {
  id: string; kind: ApprovalKind; title: string;
  requester: string; dept: DeptId; date: string;
  amount: number | null; body: string;
  line: ApprovalStep[];
  status: ApprovalStatus;
  /** true when it is the signed-in user's turn to approve */
  canDecide: boolean;
}

export interface LeaveRequest {
  id: string; type: LeaveType; from: string; to: string; days: number; reason: string; status: ApprovalStatus;
}
export interface NewLeave { type: LeaveType; from: string; to: string; reason: string }

export interface Attendance {
  /** ISO timestamps */
  clockIn: string | null;
  clockOut: string | null;
  /** Mon–Fri hours of the current week (today included, live part excluded) */
  week: { d: string; h: number }[];
}

export interface PayLine { label: string; amount: number }
export interface Payslip { month: string; payMonth: string; payDate: string; earnings: PayLine[]; deductions: PayLine[] }

export interface Msg { from: 'me' | 'them'; text: string; time: string }
export interface Thread { id: string; name: string; dept: DeptId; preview: string; unread: number; msgs: Msg[] }

export interface TrendPoint { m: string; hire: number; leave: number }
export interface ScheduleItem { date: string; time: string; title: string; tag: string; color: string }
export interface Notice { id: string; title: string; date: string; pinned: boolean }

export interface Dashboard {
  headcount: number;
  hiredThisMonth: number;
  presentToday: number;
  onLeaveToday: number;
  /** last 12 months, oldest first */
  trend: TrendPoint[];
  schedule: ScheduleItem[];
  notices: Notice[];
}

/** Everything the app needs right after login, in one request. */
export interface Bootstrap {
  me: Me;
  depts: Dept[];
  employees: Employee[];
  dashboard: Dashboard;
  approvals: Approval[];
  leaves: LeaveRequest[];
  attendance: Attendance;
  payslips: Payslip[];
  threads: Thread[];
}

export interface ApiError { error: string }
