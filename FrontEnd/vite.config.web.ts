import { resolve } from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * 공개 페이지(SC-10 공개 뷰 · SC-11 디렉터리)의 웹 빌드.
 *
 * `electron.vite.config.ts`와 나눠 둔다 — 그쪽 renderer 산출물은 Electron이 `file://`로
 * 읽는 형태라 웹 서버에 그대로 올릴 수 없고, 설정을 합치면 앱 빌드가 웹 사정에 끌려간다.
 * 이 파일을 건드려도 `npm run build`(Electron)는 영향을 받지 않는다.
 *
 * 산출물은 `out/web`이며, 서버는 SPA fallback이 필요하다:
 *   location / { try_files $uri /index.html; }
 * BrowserRouter를 쓰기 때문이다(`src/web/WebApp.tsx` 참고).
 */
export default defineConfig({
  root: resolve('src/web'),
  // envDir이 root라 src/web/.env만 읽는다 — Electron 쪽 env와 섞이지 않는다.
  resolve: {
    alias: {
      '@renderer': resolve('src/renderer/src')
    }
  },
  plugins: [react(), tailwindcss()],
  build: {
    outDir: resolve('out/web'),
    emptyOutDir: true
  },
  server: {
    // Electron 개발 서버(5173)와 겹치지 않게 다른 포트를 쓴다.
    port: 5273,
    /*
      개발 중에도 API를 같은 오리진으로 부르게 프록시한다. 이러면 CORS·쿠키 설정이
      개발과 배포에서 갈라지지 않아, "로컬에선 되는데 배포하면 막히는" 종류의 차이가 없다.
    */
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 클립 썸네일·파일은 Spring이 정적으로 서빙한다(배포 nginx도 같은 경로를 프록시).
      '/clips': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
