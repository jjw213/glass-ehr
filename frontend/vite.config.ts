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
      '/api': 'http://localhost:4000',
    },
    // ../shared (types shared with server/) lives outside frontend/
    fs: { allow: ['..'] },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
