import { API_BASE_URL, OAUTH_RETURN_URI } from './config'
import { apiFetch, refreshSession } from './client'
import { authStore } from '@renderer/auth/authStore'

// 세션 복구는 client.ts의 single-flight 구현 하나만 쓴다 (401 자동 갱신과 경로 공유).
export { refreshSession } from './client'

/** 로그아웃 — 서버 Refresh Token 폐기 후 로컬 상태 초기화. */
export async function logout(): Promise<void> {
  try {
    await apiFetch<void>('/auth/logout', { method: 'POST' })
  } finally {
    authStore.clear()
  }
}

/**
 * 개발용 계정 로그인 — 백엔드가 DEV_LOGIN_ENABLED=true일 때만 동작한다.
 * 네이버 인증 없이 고정 개발 계정의 Refresh Token 쿠키를 받고 refresh로 세션을 시작한다.
 * @returns 로그인 성공 여부
 */
export async function loginWithDevAccount(): Promise<boolean> {
  try {
    await apiFetch<void>('/auth/dev/login', { method: 'POST', auth: false })
  } catch {
    return false
  }
  return refreshSession()
}

/**
 * 네이버 OAuth 로그인.
 * 인앱 OAuth 창을 열어 인증을 마치면(쿠키가 앱 세션에 저장됨) refresh로 Access Token을 확보한다.
 * @returns 로그인 성공 여부 (사용자가 창을 닫으면 false)
 */
export async function loginWithNaver(): Promise<boolean> {
  const authorizeUrl =
    `${API_BASE_URL}/auth/oauth/naver/authorize` +
    `?returnUri=${encodeURIComponent(OAUTH_RETURN_URI)}`
  const returned = await window.api.oauth.open(authorizeUrl, OAUTH_RETURN_URI)
  if (!returned) return false
  return refreshSession()
}
