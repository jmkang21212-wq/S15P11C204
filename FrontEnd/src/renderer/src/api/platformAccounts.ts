import { apiFetch } from './client'
import { OAUTH_RETURN_URI } from './config'
import type { PlatformAccount, PlatformProvider } from '@renderer/types/platformAccount'

/**
 * 플랫폼 연동 계정 조회·연동·해제 — `PlatformAccountController`.
 * 대시보드(SC-02)가 "지금 방송 시작" 가능 여부를 판단할 때, 세션 시작(POST /broadcast-sessions)이
 * 요구하는 platformAccountId를 구할 때 쓴다. 화면은 대부분 이 함수를 직접 부르지 않고
 * `hooks/usePlatformAccounts.ts`의 공유 저장소를 거친다(중복 조회·갱신 전파 때문).
 */

export async function fetchPlatformAccounts(): Promise<PlatformAccount[]> {
  const data = await apiFetch<{ items: PlatformAccount[] }>('/platform-accounts')
  return data.items
}

/**
 * 방송을 시작할 수 있는 연동 계정 하나. 없으면 null.
 * 연동 계정이 여러 개일 때는 CONNECTED인 첫 번째를 쓴다.
 * TODO(SC-02): 다중 연동 시 선택 UI
 */
export async function fetchPrimaryPlatformAccount(): Promise<PlatformAccount | null> {
  const accounts = await fetchPlatformAccounts()
  return accounts.find((a) => a.connectionStatus === 'CONNECTED') ?? null
}

export type PlatformLinkResult = { ok: true } | { ok: false; reason: 'CANCELLED' | string } // string = PlatformException의 오류 코드

/**
 * 플랫폼 연동 시작 → 인앱 OAuth 창 → 콜백 복귀까지 한 번에 처리한다.
 * 네이버 로그인(`api/auth.ts`의 `loginWithNaver`)과 흐름·IPC가 같다 — 인증된 회원에게
 * 채널을 붙이는 동작이라 authorize URL 자체를 Access Token으로 받아와야 하므로 302가 아니다.
 */
export async function startPlatformLink(platform: PlatformProvider): Promise<PlatformLinkResult> {
  const { authorizeUrl } = await apiFetch<{ authorizeUrl: string }>(
    `/platform-accounts/${platform.toLowerCase()}/authorize` +
      `?returnUri=${encodeURIComponent(OAUTH_RETURN_URI)}`
  )
  const returned = await window.api.oauth.open(authorizeUrl, OAUTH_RETURN_URI)
  if (!returned) return { ok: false, reason: 'CANCELLED' }

  const params = new URL(returned).searchParams
  const error = params.get('error')
  if (error) return { ok: false, reason: error }
  if (params.get('status') !== 'connected') {
    return { ok: false, reason: 'PLATFORM_CONNECTION_FAILED' }
  }
  return { ok: true }
}

/** 연동 해제 — `DELETE /platform-accounts/{id}`, 204. */
export async function unlinkPlatformAccount(platformAccountId: number): Promise<void> {
  await apiFetch<void>(`/platform-accounts/${platformAccountId}`, { method: 'DELETE' })
}
