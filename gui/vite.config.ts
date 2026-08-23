import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    // The vendored components are the original's files, imports included, so the
    // original's alias is kept rather than rewriting them.
    alias: {
      '@': path.resolve(__dirname, 'src'),
      // The vendored components import react-i18next for their labels. Aliasing it to a
      // local shim keeps those files byte-identical to the original's, rather than editing
      // the imports out of the very files the appearance check compares.
      'react-i18next': path.resolve(__dirname, 'src/shims/react-i18next.ts'),
    },
  },
  server: { port: 5173 },
  preview: { port: 5174 },
});
