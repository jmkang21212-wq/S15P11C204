import { API_BASE_URL, OAUTH_RETURN_URI } from './config'
import { apiFetch } from './client'
import { authStore } from '@renderer/auth/authStore'
import type { AuthTokenResponse } from '@renderer/types/auth'

/**
 * Refresh Token 쿠키로 세션을 복구한다 (Access Token + 스트리머 정보 저장).
 * @returns 성공 여부
 */
export async function refreshSession(): Promise<boolean> {
  try {
    const data = await apiFetch<AuthTokenResponse>('/auth/refresh', {
      method: 'POST',
      auth: false
    })
    authStore.set({ accessToken: data.accessToken, streamer: data.streamer })
    return true
  } catch {
    return false
  }
}

/** 로그아웃 — 서버 Refresh Token 폐기 후 로컬 상태 초기화. */
export async function logout(): Promise<void> {
  try {
    await apiFetch<void>('/auth/logout', { method: 'POST' })
  } finally {
    authStore.clear()
  }
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
