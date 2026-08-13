import { resolve } from 'path'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

/**
 * 렌더러 측정·테스트용 설정.
 *
 * electron.vite.config.ts와 합치지 않는다 — 그쪽은 main/preload/renderer 세 환경을
 * 한 번에 빌드하는 구조라 테스트 러너가 얹히면 서로의 옵션에 끌려다닌다.
 *
 * 실행: pnpm perf:console
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@renderer': resolve(__dirname, 'src/renderer/src')
    }
  },
  test: {
    environment: 'jsdom',
    include: ['perf/**/*.{bench,test}.tsx'],
    // 측정은 프로세스를 독점해야 값이 흔들리지 않는다.
    fileParallelism: false,
    testTimeout: 120_000
  }
})
