/**
 * 앱 전역 라우트 경로 상수 — 단일 출처(single source of truth).
 * 새 화면을 추가하는 담당자는 여기에 경로를 추가하고 router.tsx에서 사용한다.
 */
export const PATHS = {
  login: '/login',
  platformConnect: '/platform-connect',
  dashboard: '/dashboard',
  schedule: '/schedule',
  sessionReport: '/session-report',
  clips: '/clips',
  growth: '/growth',
  portfolio: '/portfolio',
  matching: '/matching',
  console: '/console/:sessionId',
} as const

export type PathKey = keyof typeof PATHS
