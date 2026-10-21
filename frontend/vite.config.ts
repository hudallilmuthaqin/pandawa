/// <reference types="vitest/config" />
import { defineConfig, loadEnv, type ProxyOptions } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

type ProxyEvents = { on(event: 'proxyReq', listener: (proxyReq: { removeHeader(name: string): void }) => void): void };

export default defineConfig(({ mode }) => {
  const proxy = {
    '/api': {
      target: loadEnv(mode, '.', '').VITE_API_PROXY || 'http://localhost:8080',
      changeOrigin: true,
      // Through the proxy the UI and API share one origin, so drop the browser's Origin header; otherwise the
      // backend's CORS check rejects requests from tunnel/LAN hostnames (403 "Invalid CORS request").
      // (@types/node is not installed, so the event API is typed locally.)
      configure: (proxy) =>
        (proxy as unknown as ProxyEvents).on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin')),
    },
  } satisfies Record<string, ProxyOptions>;
  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      host: true,
      proxy,
    },
    // `npm run preview` serves the production build (used for public access through Cloudflare Tunnel).
    preview: {
      port: 4173,
      host: '127.0.0.1',
      proxy,
      allowedHosts: ['.trycloudflare.com'],
    },
    build: {
      chunkSizeWarningLimit: 900,
    },
    test: {
      environment: 'node',
    },
  };
});
