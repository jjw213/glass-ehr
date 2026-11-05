import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // dev: the browser talks to Vite (5173); /api goes on to the Node server (4000)
    proxy: { '/api': 'http://localhost:4000' },
  },
});
