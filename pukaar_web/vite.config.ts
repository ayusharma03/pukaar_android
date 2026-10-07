/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  // Opens the browser when the dashboard is ready.
  server: { port: 5173, strictPort: true, open: true },
  // MapLibre 6 loads its worker from a file next to it; pre-bundling breaks that path in dev.
  optimizeDeps: { exclude: ['maplibre-gl'] },
  test: { environment: 'node' },
});
