import { createHashRouter, Navigate } from 'react-router-dom'
import AppLayout from '@renderer/layouts/AppLayout'
import ConsoleLayout from '@renderer/layouts/ConsoleLayout'
import { PATHS } from './paths'
import LoginPage from '@renderer/pages/auth/LoginPage'
import PlatformConnectPage from '@renderer/pages/auth/PlatformConnectPage'
import DashboardPage from '@renderer/pages/dashboard/DashboardPage'
import SessionReportListPage from '@renderer/pages/report/SessionReportListPage'
import SessionReportDetailPage from '@renderer/pages/report/SessionReportDetailPage'
import BroadcastConsolePage from '@renderer/pages/console/BroadcastConsolePage'
import MiniModePage from '@renderer/pages/console/MiniModePage'
import OverlayPage from '@renderer/pages/console/OverlayPage'
import ClipsPage from '@renderer/pages/clips/ClipsPage'
import GrowthReportPage from '@renderer/pages/growth/GrowthReportPage'
import SchedulePage from '@renderer/pages/schedule/SchedulePage'
import PortfolioEditPage from '@renderer/pages/portfolio/PortfolioEditPage'
import PortfolioPublicPage from '@renderer/pages/portfolio/PortfolioPublicPage'
import MatchingPage from '@renderer/pages/matching/MatchingPage'
import DiscoverPage from '@renderer/pages/matching/DiscoverPage'
import NotFoundPage from '@renderer/pages/NotFoundPage'

/**
 * 앱 라우터 정의.
 * Electron은 프로덕션에서 file://로 로드되므로 HashRouter(createHashRouter)를 사용한다.
 *
 * 새 화면 추가 방법:
 *   1) pages/ 아래에 페이지 컴포넌트 생성
 *   2) paths.ts에 경로 상수 추가
 *   3) 알맞은 레이아웃의 children에 { path, element } 한 줄 추가
 */
export const router = createHashRouter([
  // 진입점: 지금은 로그인으로 (추후 인증 가드를 붙여 대시보드로 변경 예정)
  { path: '/', element: <Navigate to={PATHS.login} replace /> },

  // ── 인증 / 온보딩: 레이아웃 없음 ──
  { path: PATHS.login, element: <LoginPage /> },
  { path: PATHS.platformConnect, element: <PlatformConnectPage /> },

  // ── 별도 창(레이아웃 없음): 미니 모드 · OBS 오버레이 ──
  { path: PATHS.consoleMini, element: <MiniModePage /> },
  { path: PATHS.overlay, element: <OverlayPage /> },
  // ── 공개 뷰: 인증도 GNB도 없다 (design.md §5.2). 브라우저 크롬은 페이지가 직접 씌운다 ──
  { path: PATHS.publicPortfolio, element: <PortfolioPublicPage /> },
  { path: PATHS.discover, element: <DiscoverPage /> },

  // ── 앱 화면: GNB 레이아웃 ──
  {
    element: <AppLayout />,
    children: [
      { path: PATHS.dashboard, element: <DashboardPage /> },
      { path: PATHS.sessionReport, element: <SessionReportListPage /> },
      { path: PATHS.sessionReportDetail, element: <SessionReportDetailPage /> },
      { path: PATHS.clips, element: <ClipsPage /> },
      { path: PATHS.growth, element: <GrowthReportPage /> },
      { path: PATHS.schedule, element: <SchedulePage /> },
      { path: PATHS.portfolio, element: <PortfolioEditPage /> },
      { path: PATHS.matching, element: <MatchingPage /> },

      // 아직 구현되지 않은 GNB 메뉴를 받는다. GNB는 남겨 두고 본문만 안내로 채운다.
      // 담당자가 위에 라우트를 추가하면 자연히 대체된다.
      { path: '*', element: <NotFoundPage /> },
    ],
  },

  // ── 방송 콘솔: 축소 레이아웃 ──
  {
    element: <ConsoleLayout />,
    children: [
      { path: PATHS.console, element: <BroadcastConsolePage /> },
      { path: PATHS.consoleSession, element: <BroadcastConsolePage /> },
    ],
  },
])
