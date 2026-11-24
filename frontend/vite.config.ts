import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // dev: the browser talks to Vite (5173); /api goes on to the Node server (4000).
    // 공통코드(#11)는 Spring 백엔드(backend/, 8080)에 있다. 먼저 맞는 규칙이 쓰이므로 구체적인 경로를 위에 둔다.
    proxy: {
      '/api/codes': 'http://localhost:8080',
      '/api/code-groups': 'http://localhost:8080',
      // 조직(#12)·사원(#14)도 Spring 백엔드. 조직도 화면(#13)과 인사기록카드 화면(#15)이 쓴다.
      '/api/org': 'http://localhost:8080',
      '/api/employees': 'http://localhost:8080',
      // 권한(#19): 내 권한 조회와 관리자용 개인정보 조회 로그.
      '/api/auth': 'http://localhost:8080',
      // 로그인 화면 회사 선택·상단 법인 이름(#118·#120).
      '/api/enter-companies': 'http://localhost:8080',
      '/api/admin': 'http://localhost:8080',
      // 근태는 모두 Spring(#78): 근무유형·배정(#25 work-patterns·employees), 휴일(#26 holidays), 서면합의서(#27 agreements),
      // 출퇴근 기록(#28 punches, 홈·근태 화면 출퇴근 버튼), 일별 집계(#29 daily-summaries), 주 52시간(#31 weekly-hours),
      // 유연근무(#38 flex-work). 화면은 Node /api/attendance·/api/attendance/toggle 을 더 부르지 않는다.
      '/api/attendance': 'http://localhost:8080',
      // 퇴직 처리·사용증명서(#18)도 Spring. /api/employees/{id}/employment-certificates 는 위 /api/employees 규칙을 탄다.
      '/api/retirements': 'http://localhost:8080',
      '/api/employment-certificates': 'http://localhost:8080',
      // 인사발령(#59·#60)도 Spring. /api/employees/{id}/appointments 는 위 /api/employees 규칙을 탄다.
      '/api/appointments': 'http://localhost:8080',
      // 홈 대시보드 요약(#77). 로그인 직후 사용자·부서는 위 /api/auth·/api/org·/api/employees 를 쓰고,
      // Node /api/me·/api/depts·/api/dashboard 는 더 부르지 않는다. /api/bootstrap 은 아직 옮기지 않은 결재·휴가 등만 쓴다.
      '/api/dashboard': 'http://localhost:8080',
      // 연장·휴일근로 신청·특별연장근로 인가(#30)와 공통 결재함(Spring /api/approvals). 옛 전자결재 화면의 Node
      // /api/approvals(목록·'A-…' 문서 처리)와 겹치지 않도록 결재함 경로와 숫자 문서 번호만 Spring으로 보낸다.
      '/api/overtime-requests': 'http://localhost:8080',
      '/api/special-overtime-auths': 'http://localhost:8080',
      '^/api/approvals/(inbox|processed|mine|request-types|templates)(/|\\?|$)': 'http://localhost:8080',
      '^/api/approvals/\\d+(/|$)': 'http://localhost:8080',
      // 사내 메시지(#80): 대화방 목록·메시지 페이지·보내기·읽음. Node /api/threads 는 더 부르지 않는다.
      '/api/threads': 'http://localhost:8080',
      '/api': 'http://localhost:4000',
    },
    // ../shared (types shared with server/) lives outside frontend/
    fs: { allow: ['..'] },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    // 모바일 레이아웃 테스트(PeoplePage.test)가 global.css?raw 로 규칙을 읽는다. 기본값은 CSS를 빈 문자열로 바꾼다.
    css: { include: [/global\.css/] },
  },
});
