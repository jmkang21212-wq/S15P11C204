import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import DiscoverPage from '@renderer/pages/matching/DiscoverPage'
import PortfolioPublicPage from '@renderer/pages/portfolio/PortfolioPublicPage'

/**
 * 웹 빌드의 라우터 — 공개 페이지 두 개만 담는다.
 *
 * 앱 전체를 웹에 올리지 않는 이유가 두 가지다. 로그인·콘솔·OBS 제어는 `window.api`
 * (preload 브릿지)가 없으면 아예 동작하지 않고, 동작하더라도 스트리머 전용 화면을
 * 브라우저에 노출할 이유가 없다.
 *
 * Electron 쪽(`routes/router.tsx`)은 프로덕션에서 `file://`로 로드되므로 HashRouter가
 * 필수지만, 여기서는 BrowserRouter를 쓴다 — 공유 링크가 `/p/abc`여야 하고
 * `/#/p/abc`면 어색하다. 대신 서버에 SPA fallback(`try_files … /index.html`)이 필요하다.
 *
 * 두 페이지에 `standalone`을 넘겨 가짜 브라우저 크롬을 걷어낸다. 진짜 브라우저 안에서는
 * 주소창이 두 개가 되고 "앱으로 돌아가기"가 갈 곳이 없다.
 *
 * `createBrowserRouter`(데이터 라우터) 대신 `BrowserRouter`를 쓴다. 여기에는 loader·
 * action·defer가 하나도 없는데 데이터 라우터를 참조하면 그 런타임 전체가 번들에 들어온다
 * (실측: react-router 88.3kB → 36.4kB, 초기 JS 435.6kB → 382.2kB). Electron 쪽 라우터는
 * 화면 수가 많고 사정이 달라 그대로 둔다.
 *
 * 두 페이지를 `lazy`로 나누는 것도 시도했다가 되돌렸다. 청크는 index가 실행된 뒤에야
 * 요청되므로 라운드트립이 한 번 더 붙는데, 느린 회선(Lighthouse slow 4G, RTT 150ms)에서
 * 이 지연이 줄인 용량(61kB ≈ 305ms)보다 커서 /discover의 FCP가 오히려 250ms 느려졌다
 * (7회 교차 측정 중 6회에서 재현).
 */
export default function WebApp(): React.JSX.Element {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/p/:publicSlug" element={<PortfolioPublicPage standalone />} />
        <Route path="/discover" element={<DiscoverPage standalone />} />
        {/* 그 밖의 주소는 디렉터리로 보낸다 — 공개 웹에서 유일하게 의미 있는 시작점이다. */}
        <Route path="*" element={<Navigate to="/discover" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
