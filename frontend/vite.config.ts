import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
const backend = 'http://127.0.0.1:8080';
const proxy = { '/api': backend, '/login': backend, '/logout': backend };

export default defineConfig({
  plugins: [react()],
  server: { proxy },
  preview: { proxy },
});
