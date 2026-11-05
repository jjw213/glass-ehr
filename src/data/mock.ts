/* Sample data for the in-browser demo API (src/api/mock.ts).
   The server seeds the same people and documents into Oracle (server/src/db/seed.ts). */
import type { Approval, Dashboard, Dept, Employee, LeaveRequest, Me, Payslip, PayLine, Thread, WorkStatus } from '../api/types';

export const DEPTS: Dept[] = [
  { id: 'mgmt', name: '경영지원', color: '#f59e0b', lead: '박서연', desc: '재무 · 총무 · 법무' },
  { id: 'hr', name: '인사', color: '#ef4444', lead: '김지훈', desc: '채용 · 평가 · 보상' },
  { id: 'dev', name: '개발', color: '#3b82f6', lead: '이도윤', desc: '플랫폼 · 앱 · 인프라' },
  { id: 'design', name: '디자인', color: '#a855f7', lead: '최하은', desc: '프로덕트 · 브랜드' },
  { id: 'sales', name: '영업', color: '#22c55e', lead: '정민준', desc: '국내 · 해외 영업' },
  { id: 'mkt', name: '마케팅', color: '#ec4899', lead: '한지우', desc: '퍼포먼스 · 콘텐츠' },
];

const NAMES = ['김지훈', '박서연', '이도윤', '최하은', '정민준', '한지우', '윤서준', '장예린', '임하준', '오지아', '강시우', '조유나', '신도현', '배수아', '류건우', '문채원', '송태윤', '홍다인', '권민재', '양소율', '황준서', '안지민', '전하린', '고은호'];
const POSITIONS = ['사원', '주임', '대리', '과장', '차장'];
const STATUSES: WorkStatus[] = ['근무중', '근무중', '근무중', '재택', '근무중', '외근', '근무중', '휴가'];
const DEPT_ORDER = ['hr', 'mgmt', 'dev', 'design', 'sales', 'mkt'];
const ROMAN = ['jh.kim', 'sy.park', 'dy.lee', 'he.choi', 'mj.jung', 'jw.han', 'sj.yoon', 'yr.jang', 'hj.lim', 'ja.oh', 'sw.kang', 'yn.jo', 'dh.shin', 'sa.bae', 'gw.ryu', 'cw.moon', 'ty.song', 'di.hong', 'mj.kwon', 'sy.yang', 'js.hwang', 'jm.ahn', 'hr.jeon', 'eh.ko'];

export const EMPLOYEES: Employee[] = NAMES.map((name, i) => {
  const lead = DEPTS.find((d) => d.lead === name);
  return {
    id: `E${1001 + i}`,
    name,
    dept: lead ? lead.id : DEPT_ORDER[i % DEPT_ORDER.length],
    position: lead ? '팀장' : POSITIONS[(i * 7) % 5],
    email: `${ROMAN[i]}@glasshr.co`,
    phone: `010-${String(2100 + i * 37).padStart(4, '0')}-${String(4300 + i * 91).slice(-4)}`,
    joined: `${2019 + (i % 7)}-${String(((i * 5) % 12) + 1).padStart(2, '0')}-${String(((i * 11) % 27) + 1).padStart(2, '0')}`,
    status: STATUSES[i % STATUSES.length],
    location: i % 3 === 0 ? '본사 7층' : i % 3 === 1 ? '본사 8층' : '판교 오피스',
  };
});

export const ME: Me = { id: 'E1001', loginId: 'hr', name: '김지훈', dept: 'hr', position: '팀장', role: 'HR', email: 'jh.kim@glasshr.co', annualLeave: 17 };

const months = Array.from({ length: 12 }, (_, i) => new Date(new Date().getFullYear(), new Date().getMonth() - 11 + i, 1).getMonth() + 1);
export const DASHBOARD: Dashboard = {
  headcount: 128, hiredThisMonth: 7, presentToday: 117, onLeaveToday: 9,
  trend: months.map((m, i) => ({ m: `${m}월`, hire: [2, 4, 3, 1, 5, 3, 2, 4, 6, 3, 5, 7][i], leave: [1, 0, 2, 1, 1, 2, 1, 3, 1, 2, 1, 1][i] })),
  schedule: [
    { date: '', time: '09:30', title: '인사팀 주간 스탠드업', tag: '회의', color: '#60a5fa' },
    { date: '', time: '11:00', title: '개발팀 신규 입사자 오리엔테이션', tag: '온보딩', color: '#4ade80' },
    { date: '', time: '14:00', title: '3분기 평가 보정 회의', tag: '평가', color: '#f59e0b' },
    { date: '', time: '16:30', title: '복리후생 제도 개편 검토', tag: '보상', color: '#f472b6' },
  ],
  notices: [
    { id: 'n1', title: '연말정산 일정 안내', date: '11.03', pinned: true },
    { id: 'n2', title: '사내 건강검진 예약 오픈', date: '11.01', pinned: false },
    { id: 'n3', title: '판교 오피스 주차 정책 변경', date: '10.28', pinned: false },
  ],
};

export const LEAVES: LeaveRequest[] = [
  { id: 'L3', type: '연차', from: '2026-11-20', to: '2026-11-21', days: 2, reason: '가족 여행', status: '대기' },
  { id: 'L2', type: '반차', from: '2026-10-29', to: '2026-10-29', days: 0.5, reason: '병원 방문', status: '승인' },
  { id: 'L1', type: '연차', from: '2026-10-17', to: '2026-10-17', days: 1, reason: '개인 일정', status: '승인' },
];

export const WEEK_HOURS = [{ d: '월', h: 8.5 }, { d: '화', h: 9.2 }, { d: '수', h: 8.8 }, { d: '목', h: 9.0 }, { d: '금', h: 8.4 }];

function makePayslip(month: string, payMonth: string, payDate: string, overtime: number, bonus = 0): Payslip {
  const earnings: PayLine[] = [
    { label: '기본급', amount: 4_200_000 }, { label: '직책수당', amount: 300_000 },
    { label: '식대 (비과세)', amount: 200_000 }, { label: '연장근로수당', amount: overtime },
  ];
  if (bonus) earnings.push({ label: '성과급', amount: bonus });
  const taxable = earnings.filter((e) => !e.label.includes('비과세')).reduce((s, e) => s + e.amount, 0);
  const r10 = (n: number) => Math.floor(n / 10) * 10;
  const health = r10(taxable * 0.03545), tax = r10(taxable * 0.052);
  return {
    month, payMonth, payDate, earnings,
    deductions: [
      { label: '국민연금', amount: r10(taxable * 0.045) }, { label: '건강보험', amount: health },
      { label: '장기요양보험', amount: r10(health * 0.1295) }, { label: '고용보험', amount: r10(taxable * 0.009) },
      { label: '소득세', amount: tax }, { label: '지방소득세', amount: r10(tax * 0.1) },
    ],
  };
}
export const PAYSLIPS: Payslip[] = [
  makePayslip('2026년 10월', '2026-10', '2026-10-24', 412_000, 800_000),
  makePayslip('2026년 9월', '2026-09', '2026-09-25', 286_000),
  makePayslip('2026년 8월', '2026-08', '2026-08-25', 198_000),
  makePayslip('2026년 7월', '2026-07', '2026-07-24', 350_000),
  makePayslip('2026년 6월', '2026-06', '2026-06-25', 120_000),
];

const line = (names: string[], status: Approval['status']) => names.map((name, i) => ({
  name,
  status: status === '승인' ? 'APPROVED' as const : status === '반려' && i === names.length - 1 ? 'REJECTED' as const : i < names.length - 1 ? 'APPROVED' as const : 'WAIT' as const,
}));
const doc = (a: Omit<Approval, 'line' | 'canDecide'> & { names: string[] }): Approval => {
  const { names, ...rest } = a;
  return { ...rest, line: line(names, a.status), canDecide: a.status === '대기' };
};
export const APPROVALS: Approval[] = [
  doc({ id: 'A-2611', kind: '휴가', title: '연차 사용 신청 (11/13~11/14)', requester: '윤서준', dept: 'hr', date: '11.04', amount: null, body: '가족 행사로 이틀간 연차를 사용하고자 합니다. 진행 중인 업무는 인수인계 완료했습니다.', names: ['이도윤', '김지훈'], status: '대기' }),
  doc({ id: 'A-2610', kind: '지출', title: '디자인팀 폰트 라이선스 갱신', requester: '최하은', dept: 'design', date: '11.04', amount: 1_480_000, body: '연간 웹폰트 라이선스(5석) 갱신 비용입니다. 기존 계약이 11/30 만료됩니다.', names: ['최하은', '박서연', '김지훈'], status: '대기' }),
  doc({ id: 'A-2608', kind: '출장', title: '부산 고객사 미팅 출장', requester: '정민준', dept: 'sales', date: '11.03', amount: 420_000, body: '11/18 부산 고객사 정기 미팅 참석. KTX 왕복 및 숙박 1박 포함.', names: ['정민준', '김지훈'], status: '대기' }),
  doc({ id: 'A-2605', kind: '채용', title: '백엔드 개발자 2명 채용 요청', requester: '이도윤', dept: 'dev', date: '11.02', amount: null, body: '결제 시스템 개편 프로젝트 인력 충원 요청입니다. 경력 3~7년, 1월 입사 목표.', names: ['이도윤', '김지훈'], status: '대기' }),
  doc({ id: 'A-2601', kind: '교육', title: '퍼포먼스 마케팅 외부 교육 신청', requester: '한지우', dept: 'mkt', date: '10.30', amount: 650_000, body: '12월 2일간 진행되는 외부 교육 과정 수강 신청입니다.', names: ['한지우', '김지훈'], status: '대기' }),
  doc({ id: 'A-2597', kind: '지출', title: '10월 팀 회식비 정산', requester: '강시우', dept: 'mkt', date: '10.28', amount: 312_000, body: '10월 마케팅팀 월간 회식비 정산 요청입니다.', names: ['한지우', '김지훈'], status: '승인' }),
  doc({ id: 'A-2590', kind: '휴가', title: '병가 신청 (10/21)', requester: '조유나', dept: 'sales', date: '10.21', amount: null, body: '몸살로 하루 병가를 신청합니다.', names: ['정민준', '김지훈'], status: '승인' }),
  doc({ id: 'A-2588', kind: '출장', title: '해외 전시회 참관 출장', requester: '신도현', dept: 'sales', date: '10.18', amount: 3_900_000, body: '전시회 참관 목적의 4박 5일 출장 신청입니다.', names: ['정민준', '김지훈', '박서연'], status: '반려' }),
];

export const THREADS: Thread[] = [
  { id: 't1', name: '박서연', dept: 'mgmt', preview: '일정표는 11/10까지 확정하면 될 것 같아요', unread: 2, msgs: [
    { from: 'them', text: '지훈님, 연말정산 공지 초안 공유드려요.', time: '17:42' },
    { from: 'them', text: '일정표는 11/10까지 확정하면 될 것 같아요. 확인 부탁드립니다!', time: '17:43' },
  ] },
  { id: 't2', name: '이도윤', dept: 'dev', preview: '네 오늘 중으로 정리해서 드릴게요.', unread: 1, msgs: [
    { from: 'them', text: '백엔드 채용 요청 건 결재 올렸습니다.', time: '15:10' },
    { from: 'me', text: '확인했어요. JD 초안도 같이 보내주실 수 있을까요?', time: '15:22' },
    { from: 'them', text: '네 오늘 중으로 정리해서 드릴게요.', time: '15:24' },
  ] },
  { id: 't3', name: '최하은', dept: 'design', preview: '감사합니다 🙏', unread: 0, msgs: [
    { from: 'me', text: '온보딩 키트 디자인 너무 좋네요!', time: '어제' },
    { from: 'them', text: '감사합니다 🙏', time: '어제' },
  ] },
  { id: 't4', name: '정민준', dept: 'sales', preview: '출장 일정 변경 가능할까요?', unread: 0, msgs: [
    { from: 'them', text: '부산 출장 일정이 하루 당겨질 수도 있는데, 변경 가능할까요?', time: '월' },
  ] },
];
