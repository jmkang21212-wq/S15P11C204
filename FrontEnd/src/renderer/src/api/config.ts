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
 * 공개 포트폴리오·디렉터리를 서빙하는 웹 오리진. 스킴 없이 호스트만 둔다 —
 * `https://`는 주소를 복사하는 쪽에서 붙인다.
 *
 * 앱은 이 값으로 "공개 주소"를 만들어 보여주고 복사시킨다. 웹 빌드(`src/web`)를 올린
 * 도메인과 같아야 그 주소가 실제로 열린다.
 *
 * 기본값을 배포 도메인으로 둔 이유: dev 모드에는 .env가 없어 이 기본값이 그대로 쓰이는데,
 * 예전 목업 값(소유하지 않은 도메인)을 남겨 두면 개발 중에는 열리지 않는 주소가 보이고
 * "배포 설정이 반영되지 않았다"로 오해하게 된다.
 */
export const PUBLIC_WEB_ORIGIN = env.VITE_PUBLIC_WEB_ORIGIN ?? 'i15c204.p.ssafy.io'

/**
 * 음성 스트리밍용 AI 서버 WebSocket (AI/FRONTEND_API.md).
 * CSP connect-src에 이 주소가 있어야 연결된다.
 */
export const AI_WS_URL = env.VITE_AI_WS_URL ?? 'ws://localhost:8000/ws/audio'
