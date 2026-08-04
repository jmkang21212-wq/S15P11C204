/**
 * 앱 전역 라우트 경로 상수 — 단일 출처(single source of truth).
 * 새 화면을 추가하는 담당자는 여기에 경로를 추가하고 router.tsx에서 사용한다.
 */
export const PATHS = {
  login: '/login',
  platformConnect: '/platform-connect',
  dashboard: '/dashboard',
  schedule: '/schedule',
  sessionReport: '/session-report', // 종료 세션 목록(GNB 메뉴) — 실제 리포트는 sessionReportDetail
  sessionReportDetail: '/session-report/:sessionId',
  clips: '/clips',
  growth: '/growth',
  portfolio: '/portfolio', // 편집 화면(GNB 메뉴) — 공개 뷰는 publicPortfolio
  /** 인증 없이 보는 공개 포트폴리오. SC-11 비즈니스 매칭에서도 이 경로로 넘어온다. */
  publicPortfolio: '/p/:publicSlug',
  matching: '/matching', // 로그인 후 검색(GNB 메뉴) — 공개 디렉터리는 discover
  /** 인증 없이 보는 공개 스트리머 디렉터리 (streampilot.tv/discover). */
  discover: '/discover',
  console: '/console', // 방송 콘솔 진입(GNB 메뉴) — 실제 세션은 consoleSession로 라우팅
  consoleSession: '/console/:sessionId',
  consoleMini: '/console/mini', // 미니 모드 (추후 항상-위 별도 창이 로드)
  overlay: '/overlay' // OBS 브라우저 소스용 투명 오버레이
} as const

export type PathKey = keyof typeof PATHS
