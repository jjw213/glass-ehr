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
      '/api/admin': 'http://localhost:8080',
      // 근무유형·배정(#25)도 Spring. /api/attendance/toggle 등 나머지 근태 경로는 아직 Node 서버다.
      '/api/attendance/work-patterns': 'http://localhost:8080',
      '/api/attendance/employees': 'http://localhost:8080',
      // 근로자대표 서면합의서(#27)도 Spring.
      '/api/attendance/agreements': 'http://localhost:8080',
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
