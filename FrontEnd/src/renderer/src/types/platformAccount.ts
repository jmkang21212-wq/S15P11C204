/**
 * 플랫폼 연동 계정 타입 — `PlatformAccountController` (`/api/v1/platform-accounts`).
 * 출처: `platform/domain/PlatformAccount.java`, DB CHECK 절(`V1__baseline_schema.sql`).
 */

export type PlatformProvider = 'CHZZK' | 'YOUTUBE'
export type PlatformConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'REAUTH_REQUIRED'

/** GNB·콘솔 방송 시작 폼처럼 연동 계정을 목록으로 보여줄 때 쓰는 표기. */
export const PLATFORM_LABEL: Record<PlatformProvider, string> = {
  CHZZK: '치지직',
  YOUTUBE: '유튜브'
}

export interface PlatformAccount {
  platformAccountId: number
  platform: PlatformProvider
  channelId: string
  channelName: string
  connectionStatus: PlatformConnectionStatus
  connectedAt: string
}
