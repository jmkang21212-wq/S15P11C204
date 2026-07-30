/**
 * API 연동 공통 설정.
 * 로컬 도커 백엔드는 http://localhost:8080 기준. 배포/환경별로 env로 덮어쓴다.
 */
const env = import.meta.env as Record<string, string | undefined>

/** 백엔드 오리진 (CSP connect-src와 일치해야 함) */
export const API_ORIGIN = env.VITE_API_ORIGIN ?? 'http://localhost:8080'

/** REST Base URL */
export const API_BASE_URL = `${API_ORIGIN}/api/v1`

/**
 * OAuth 복귀 주소. 백엔드가 콜백 후 이 주소로 리다이렉트하면 인앱 OAuth 창이 감지해 닫는다.
 * ⚠️ 백엔드 `laive.auth.allowedReturnUris`에 이 값(origin 포함)이 등록돼 있어야 CORS·복귀가 동작한다.
 */
export const OAUTH_RETURN_URI = env.VITE_OAUTH_RETURN_URI ?? 'http://localhost:5173/oauth/callback'

/**
 * 음성 스트리밍용 AI 서버 WebSocket (AI/FRONTEND_API.md).
 * CSP connect-src에 이 주소가 있어야 연결된다.
 */
export const AI_WS_URL = env.VITE_AI_WS_URL ?? 'ws://localhost:8000/ws/audio'
