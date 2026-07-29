import { apiFetch } from './client'
import type { PlatformAccount } from '@renderer/types/platformAccount'

/**
 * 플랫폼 연동 계정 조회 — `GET /api/v1/platform-accounts` (PlatformAccountController).
 * 대시보드(SC-02)가 "지금 방송 시작" 가능 여부를 판단할 때, 세션 시작(POST /broadcast-sessions)이
 * 요구하는 platformAccountId를 구할 때 쓴다.
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
