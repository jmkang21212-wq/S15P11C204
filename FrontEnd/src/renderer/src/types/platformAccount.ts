/**
 * 플랫폼 연동 계정 타입 — `PlatformAccountController` (`/api/v1/platform-accounts`).
 * 출처: `platform/domain/PlatformAccount.java`, DB CHECK 절(`V1__baseline_schema.sql`).
 */

export type PlatformProvider = 'CHZZK' | 'SOOP'
export type PlatformConnectionStatus = 'CONNECTED' | 'DISCONNECTED' | 'REAUTH_REQUIRED'

export interface PlatformAccount {
  platformAccountId: number
  platform: PlatformProvider
  channelId: string
  channelName: string
  connectionStatus: PlatformConnectionStatus
  connectedAt: string
}
