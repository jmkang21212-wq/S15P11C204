import '@renderer/assets/globals.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import WebApp from './WebApp'

/**
 * 웹 빌드 진입점.
 *
 * Electron 쪽(`renderer/src/main.tsx`)과 달리 부팅 시 세션 복구를 하지 않는다 —
 * 공개 페이지는 인증이 필요 없고(BE `permitAll`), 브라우저에서 refresh 쿠키를 건드리면
 * 앱 세션의 1회용 Refresh Token을 무효화할 수 있다.
 */
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <WebApp />
  </StrictMode>
)
