/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Built into build/ because pom.xml copies that directory onto the jar's static root.
export default defineConfig({
    plugins: [react()],
    build: { outDir: 'build', sourcemap: false },
    server: {
        port: 3000,
        proxy: { '/api': 'http://localhost:8085' },
    },
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/test/setup.ts'],
        globals: true,
    },
});
