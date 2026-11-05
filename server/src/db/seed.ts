/**
 * Sample data — same people and documents as the frontend demo (src/data/mock.ts).
 * Dates are generated relative to the day you run `npm run db:init`.
 * Every account's initial password is SEED_PASSWORD (default 1234). Change it before real use.
 */
import type { Db } from './types';
import { hashPassword } from '../auth';
import { addDays, monday, ymd } from '../util';

const DEPTS = [
  { id: 'mgmt', name: '경영지원', color: '#f59e0b', lead: '박서연', desc: '재무 · 총무 · 법무' },
  { id: 'hr', name: '인사', color: '#ef4444', lead: '김지훈', desc: '채용 · 평가 · 보상' },
  { id: 'dev', name: '개발', color: '#3b82f6', lead: '이도윤', desc: '플랫폼 · 앱 · 인프라' },
  { id: 'design', name: '디자인', color: '#a855f7', lead: '최하은', desc: '프로덕트 · 브랜드' },
  { id: 'sales', name: '영업', color: '#22c55e', lead: '정민준', desc: '국내 · 해외 영업' },
  { id: 'mkt', name: '마케팅', color: '#ec4899', lead: '한지우', desc: '퍼포먼스 · 콘텐츠' },
];
const NAMES = ['김지훈', '박서연', '이도윤', '최하은', '정민준', '한지우', '윤서준', '장예린', '임하준', '오지아', '강시우', '조유나', '신도현', '배수아', '류건우', '문채원', '송태윤', '홍다인', '권민재', '양소율', '황준서', '안지민', '전하린', '고은호'];
const LOGINS = ['hr', 'sy.park', 'dy.lee', 'he.choi', 'mj.jung', 'jw.han', 'sj.yoon', 'yr.jang', 'hj.lim', 'ja.oh', 'sw.kang', 'yn.jo', 'dh.shin', 'sa.bae', 'gw.ryu', 'cw.moon', 'ty.song', 'di.hong', 'mj.kwon', 'sy.yang', 'js.hwang', 'jm.ahn', 'hr.jeon', 'eh.ko'];
const EMAILS = ['jh.kim', ...LOGINS.slice(1)];
const POSITIONS = ['사원', '주임', '대리', '과장', '차장'];
const STATUSES = ['근무중', '근무중', '근무중', '재택', '근무중', '외근', '근무중', '휴가'];
const DEPT_ORDER = ['hr', 'mgmt', 'dev', 'design', 'sales', 'mkt'];
const BASE: Record<string, number> = { 사원: 3_200_000, 주임: 3_500_000, 대리: 3_900_000, 과장: 4_400_000, 차장: 5_000_000, 팀장: 4_200_000 };

const round10 = (n: number) => Math.floor(n / 10) * 10;
const at = (day: Date, h: number, m: number) => { const d = new Date(day); d.setHours(h, m, 0, 0); return d; };
const monthsAgo = (n: number, day = 1) => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth() - n, day); };

export async function seed(db: Db, password = process.env.SEED_PASSWORD ?? '1234') {
  const today = new Date();
  const pwHash = await hashPassword(password); // same initial password for everyone

  await db.tx(async (t) => {
    /* departments (lead filled after employees exist) */
    for (const [i, d] of DEPTS.entries()) {
      await t.exec('INSERT INTO HR_DEPTS (ID, NAME, COLOR, DESCR, SORT_ORDER) VALUES (:id, :n, :c, :d, :o)', { id: d.id, n: d.name, c: d.color, d: d.desc, o: i });
    }

    /* employees */
    const idByName = new Map<string, string>();
    for (const [i, name] of NAMES.entries()) {
      const lead = DEPTS.find((d) => d.lead === name);
      const dept = lead ? lead.id : DEPT_ORDER[i % DEPT_ORDER.length];
      const id = `E${1001 + i}`;
      idByName.set(name, id);
      // a few recent hires so the dashboard trend has data
      const joined = i % 4 === 3 ? ymd(monthsAgo((i / 4) % 7 | 0, 3 + (i % 20))) : `${2019 + (i % 6)}-${String(((i * 5) % 12) + 1).padStart(2, '0')}-${String(((i * 11) % 27) + 1).padStart(2, '0')}`;
      await t.exec(
        `INSERT INTO HR_EMPLOYEES (ID, LOGIN_ID, PASSWORD_HASH, NAME, DEPT_ID, POSITION, ROLE, EMAIL, PHONE, JOINED_ON, STATUS, LOCATION, ANNUAL_LEAVE)
         VALUES (:id, :login, :pw, :name, :dept, :pos, :role, :email, :phone, :joined, :status, :loc, 17)`,
        {
          id, login: LOGINS[i], pw: pwHash, name, dept,
          pos: lead ? '팀장' : POSITIONS[(i * 7) % 5],
          role: dept === 'hr' ? 'HR' : 'EMPLOYEE',
          email: `${EMAILS[i]}@glasshr.co`,
          phone: `010-${String(2100 + i * 37).padStart(4, '0')}-${String(4300 + i * 91).slice(-4)}`,
          joined, status: STATUSES[i % STATUSES.length],
          loc: i % 3 === 0 ? '본사 7층' : i % 3 === 1 ? '본사 8층' : '판교 오피스',
        });
    }
    // former employees (feed the "퇴사" side of the trend)
    for (let k = 0; k < 6; k++) {
      await t.exec(
        `INSERT INTO HR_EMPLOYEES (ID, LOGIN_ID, PASSWORD_HASH, NAME, DEPT_ID, POSITION, ROLE, EMAIL, JOINED_ON, LEFT_ON, STATUS)
         VALUES (:id, :login, :pw, :name, :dept, '사원', 'EMPLOYEE', :email, '2022-03-02', :left, '퇴사')`,
        { id: `E9${String(k + 1).padStart(3, '0')}`, login: `former${k + 1}`, pw: pwHash, name: `퇴사자${k + 1}`, dept: DEPT_ORDER[k], email: `former${k + 1}@glasshr.co`, left: ymd(monthsAgo(k * 2 % 11, 15)) });
    }
    for (const d of DEPTS) await t.exec('UPDATE HR_DEPTS SET LEAD_ID = :l WHERE ID = :id', { l: idByName.get(d.lead)!, id: d.id });
    const id = (n: string) => idByName.get(n)!;
    const me = id('김지훈');

    /* attendance: my week so far + everyone at work today */
    const mon = monday(today);
    const hours = [[8, 52, 18, 20], [8, 40, 18, 52], [9, 3, 18, 51], [8, 47, 17, 55], [8, 58, 17, 22]];
    for (let i = 0; i < 5; i++) {
      const day = addDays(mon, i);
      if (ymd(day) > ymd(today)) break;
      const [h1, m1, h2, m2] = hours[i];
      const isToday = ymd(day) === ymd(today);
      await t.exec('INSERT INTO HR_ATTENDANCE (ID, EMP_ID, WORK_DATE, CLOCK_IN, CLOCK_OUT) VALUES (:id, :e, :d, :i, :o)',
        { id: `T-${me}-${ymd(day)}`, e: me, d: ymd(day), i: at(day, h1, m1), o: isToday ? null : at(day, h2, m2) });
    }
    if (today.getDay() % 6 !== 0) {
      for (const [i, name] of NAMES.entries()) {
        if (i === 0 || STATUSES[i % STATUSES.length] === '휴가') continue;
        await t.exec('INSERT INTO HR_ATTENDANCE (ID, EMP_ID, WORK_DATE, CLOCK_IN) VALUES (:id, :e, :d, :i)',
          { id: `T-${id(name)}-${ymd(today)}`, e: id(name), d: ymd(today), i: at(today, 8, 30 + (i * 7) % 50) });
      }
    }

    /* approvals */
    const docs: { id: string; kind: string; title: string; by: string; ago: number; amount?: number; body: string; line: string[]; status: '대기' | '승인' | '반려' }[] = [
      { id: 'A-2611', kind: '휴가', title: '연차 사용 신청', by: '윤서준', ago: 0, body: '가족 행사로 이틀간 연차를 사용하고자 합니다. 진행 중인 배포 건은 임하준 님에게 인수인계 완료했습니다.', line: ['이도윤', '김지훈'], status: '대기' },
      { id: 'A-2610', kind: '지출', title: '디자인팀 폰트 라이선스 갱신', by: '최하은', ago: 0, amount: 1_480_000, body: '연간 웹폰트 라이선스(5석) 갱신 비용입니다. 기존 계약이 이번 달 말 만료됩니다.', line: ['최하은', '박서연', '김지훈'], status: '대기' },
      { id: 'A-2608', kind: '출장', title: '부산 고객사 미팅 출장', by: '정민준', ago: 1, amount: 420_000, body: '부산 고객사 정기 미팅 참석. KTX 왕복 및 숙박 1박 포함.', line: ['정민준', '김지훈'], status: '대기' },
      { id: 'A-2605', kind: '채용', title: '백엔드 개발자 2명 채용 요청', by: '이도윤', ago: 2, body: '결제 시스템 개편 프로젝트 인력 충원 요청입니다. 경력 3~7년, 1월 입사 목표.', line: ['이도윤', '김지훈', '박서연'], status: '대기' },
      { id: 'A-2601', kind: '교육', title: '퍼포먼스 마케팅 외부 교육 신청', by: '한지우', ago: 5, amount: 650_000, body: '다음 달 2일간 진행되는 외부 교육 과정 수강 신청입니다.', line: ['한지우', '김지훈'], status: '대기' },
      { id: 'A-2597', kind: '지출', title: '팀 회식비 정산', by: '강시우', ago: 7, amount: 312_000, body: '마케팅팀 월간 회식비 정산 요청입니다.', line: ['한지우', '김지훈'], status: '승인' },
      { id: 'A-2590', kind: '휴가', title: '병가 신청', by: '조유나', ago: 14, body: '몸살로 하루 병가를 신청합니다.', line: ['정민준', '김지훈'], status: '승인' },
      { id: 'A-2588', kind: '출장', title: '해외 전시회 참관 출장', by: '신도현', ago: 17, amount: 3_900_000, body: '전시회 참관 목적의 4박 5일 출장 신청입니다.', line: ['정민준', '김지훈', '박서연'], status: '반려' },
    ];
    for (const d of docs) {
      const meSeq = d.line.indexOf('김지훈') + 1;
      // pending docs wait on 김지훈; earlier steps are already approved
      const currentSeq = d.status === '대기' ? meSeq : d.line.length;
      await t.exec(
        `INSERT INTO HR_APPROVALS (ID, KIND, TITLE, REQUESTER_ID, BODY, AMOUNT, STATUS, CURRENT_SEQ, CREATED_AT)
         VALUES (:id, :k, :ti, :r, :b, :a, :s, :c, :at)`,
        { id: d.id, k: d.kind, ti: d.title, r: id(d.by), b: d.body, a: d.amount ?? null, s: d.status, c: currentSeq, at: at(addDays(today, -d.ago), 9 + (d.ago % 8), 10) });
      for (const [i, name] of d.line.entries()) {
        const seq = i + 1;
        const st = d.status === '대기' ? (seq < currentSeq ? 'APPROVED' : 'WAIT')
          : d.status === '승인' ? 'APPROVED'
          : seq < d.line.length ? 'APPROVED' : 'REJECTED';
        await t.exec('INSERT INTO HR_APPROVAL_STEPS (APPROVAL_ID, SEQ, APPROVER_ID, STATUS, ACTED_AT) VALUES (:a, :s, :p, :st, :at)',
          { a: d.id, s: seq, p: id(name), st, at: st === 'WAIT' ? null : at(addDays(today, -d.ago), 15, 0) });
      }
    }
    // 윤서준's leave is tied to A-2611, so approving the document approves the leave
    const nextMon = addDays(monday(today), 7);
    await t.exec(
      `INSERT INTO HR_LEAVES (ID, EMP_ID, LEAVE_TYPE, FROM_DATE, TO_DATE, DAYS, REASON, STATUS, APPROVAL_ID, CREATED_AT)
       VALUES ('L-SEED-1', :e, '연차', :f, :t, 2, '가족 행사', '대기', 'A-2611', :now)`,
      { e: id('윤서준'), f: ymd(addDays(nextMon, 3)), t: ymd(addDays(nextMon, 4)), now: today });
    await t.exec("UPDATE HR_APPROVALS SET TITLE = :ti WHERE ID = 'A-2611'",
      { ti: `연차 사용 신청 (${ymd(addDays(nextMon, 3)).slice(5).replace('-', '/')}~${ymd(addDays(nextMon, 4)).slice(5).replace('-', '/')})` });

    /* my past leaves */
    const myLeaves = [
      { ty: '연차', f: ymd(addDays(today, -18)), d: 1, r: '개인 일정', s: '승인' },
      { ty: '반차', f: ymd(addDays(today, -6)), d: 0.5, r: '병원 방문', s: '승인' },
    ];
    for (const [i, l] of myLeaves.entries()) {
      await t.exec(
        `INSERT INTO HR_LEAVES (ID, EMP_ID, LEAVE_TYPE, FROM_DATE, TO_DATE, DAYS, REASON, STATUS, CREATED_AT)
         VALUES (:id, :e, :ty, :f, :f, :d, :r, :s, :now)`,
        { id: `L-SEED-ME-${i}`, e: me, ty: l.ty, f: l.f, d: l.d, r: l.r, s: l.s, now: today });
    }

    /* payslips: last 5 months for everyone */
    for (const [i, name] of NAMES.entries()) {
      const lead = DEPTS.some((d) => d.lead === name);
      const pos = lead ? '팀장' : POSITIONS[(i * 7) % 5];
      for (let k = 1; k <= 5; k++) {
        const m = monthsAgo(k);
        const payMonth = `${m.getFullYear()}-${String(m.getMonth() + 1).padStart(2, '0')}`;
        const pid = `P-${id(name)}-${payMonth}`;
        const earnings: [string, number][] = [
          ['기본급', BASE[pos]], ['직책수당', lead ? 300_000 : 0], ['식대 (비과세)', 200_000],
          ['연장근로수당', [412_000, 286_000, 198_000, 350_000, 120_000][(k + i) % 5]],
        ];
        if (k === 1 && i % 2 === 0) earnings.push(['성과급', 800_000]);
        const taxable = earnings.filter(([l]) => !l.includes('비과세')).reduce((s, [, a]) => s + a, 0);
        const health = round10(taxable * 0.03545);
        const incomeTax = round10(taxable * 0.052);
        const deductions: [string, number][] = [
          ['국민연금', round10(taxable * 0.045)], ['건강보험', health], ['장기요양보험', round10(health * 0.1295)],
          ['고용보험', round10(taxable * 0.009)], ['소득세', incomeTax], ['지방소득세', round10(incomeTax * 0.1)],
        ];
        await t.exec('INSERT INTO HR_PAYSLIPS (ID, EMP_ID, PAY_MONTH, PAY_DATE) VALUES (:id, :e, :m, :d)',
          { id: pid, e: id(name), m: payMonth, d: `${payMonth}-25` });
        let seq = 1;
        for (const [label, amount] of earnings.filter(([, a]) => a > 0)) {
          await t.exec('INSERT INTO HR_PAYSLIP_ITEMS (PAYSLIP_ID, SEQ, ITEM_TYPE, LABEL, AMOUNT) VALUES (:p, :s, :ty, :l, :a)', { p: pid, s: seq++, ty: 'E', l: label, a: amount });
        }
        for (const [label, amount] of deductions) {
          await t.exec('INSERT INTO HR_PAYSLIP_ITEMS (PAYSLIP_ID, SEQ, ITEM_TYPE, LABEL, AMOUNT) VALUES (:p, :s, :ty, :l, :a)', { p: pid, s: seq++, ty: 'D', l: label, a: amount });
        }
      }
    }

    /* message threads with 김지훈 */
    const threads: { other: string; msgs: [from: 'me' | 'them', text: string, daysAgo: number, h: number, m: number, read: boolean][] }[] = [
      { other: '박서연', msgs: [['them', '지훈님, 연말정산 공지 초안 공유드려요.', 0, 17, 42, false], ['them', '일정표는 다음 주까지 확정하면 될 것 같아요. 확인 부탁드립니다!', 0, 17, 43, false]] },
      { other: '이도윤', msgs: [['them', '백엔드 채용 요청 건 결재 올렸습니다.', 0, 15, 10, true], ['me', '확인했어요. JD 초안도 같이 보내주실 수 있을까요?', 0, 15, 22, true], ['them', '네 오늘 중으로 정리해서 드릴게요.', 0, 15, 24, false]] },
      { other: '최하은', msgs: [['me', '온보딩 키트 디자인 너무 좋네요!', 1, 11, 5, true], ['them', '감사합니다 🙏', 1, 11, 9, true]] },
      { other: '정민준', msgs: [['them', '부산 출장 일정이 하루 당겨질 수도 있는데, 변경 가능할까요?', 2, 10, 30, true]] },
    ];
    for (const [i, th] of threads.entries()) {
      const tid = `TH-${i + 1}`;
      await t.exec('INSERT INTO HR_THREADS (ID, EMP_A, EMP_B) VALUES (:id, :a, :b)', { id: tid, a: me, b: id(th.other) });
      for (const [j, [from, text, ago, h, m, read]] of th.msgs.entries()) {
        const sent = at(addDays(today, -ago), h, m);
        await t.exec('INSERT INTO HR_MESSAGES (ID, THREAD_ID, SENDER_ID, BODY, SENT_AT, READ_AT) VALUES (:id, :t, :s, :b, :at, :r)',
          { id: `${tid}-M${j + 1}`, t: tid, s: from === 'me' ? me : id(th.other), b: text, at: sent, r: read ? sent : null });
      }
    }

    /* notices & events */
    const notices: [string, number, number][] = [['연말정산 일정 안내', 1, 1], ['사내 건강검진 예약 오픈', 3, 0], ['판교 오피스 주차 정책 변경', 7, 0]];
    for (const [i, [title, ago, pinned]] of notices.entries()) {
      await t.exec('INSERT INTO HR_NOTICES (ID, TITLE, POSTED_ON, PINNED) VALUES (:id, :t, :d, :p)', { id: `N${i + 1}`, t: title, d: ymd(addDays(today, -ago)), p: pinned });
    }
    const events: [number, string, string, string, string][] = [
      [0, '09:30', '인사팀 주간 스탠드업', '회의', '#60a5fa'], [0, '11:00', '개발팀 신규 입사자 오리엔테이션', '온보딩', '#4ade80'],
      [0, '14:00', '3분기 평가 보정 회의', '평가', '#f59e0b'], [0, '16:30', '복리후생 제도 개편 검토', '보상', '#f472b6'],
      [1, '10:00', '채용 면접 (백엔드)', '채용', '#a78bfa'], [3, '15:00', '전사 타운홀', '전사', '#60a5fa'],
    ];
    for (const [i, [ago, time, title, tag, color]] of events.entries()) {
      await t.exec('INSERT INTO HR_EVENTS (ID, EVENT_DATE, START_TIME, TITLE, TAG, COLOR) VALUES (:id, :d, :s, :t, :g, :c)',
        { id: `EV${i + 1}`, d: ymd(addDays(today, ago)), s: time, t: title, g: tag, c: color });
    }
  });
}
