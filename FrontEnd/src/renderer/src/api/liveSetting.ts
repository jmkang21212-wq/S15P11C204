import { apiFetch } from './client'

/**
 * 플랫폼 방송 설정(치지직 방송 제목 등) — `PlatformAccountController`의
 * `GET/PATCH /platform-accounts/{id}/live-setting`.
 *
 * 이 값은 캘린더 일정 제목과 별개인 "치지직에 실제로 표시되는 방송 제목"이다.
 * 백엔드가 치지직 Open API를 경유하므로(토큰 갱신 포함) 대시보드 집계와 분리해 호출한다.
 *
 * 오류 코드로 재연동 필요 여부를 구분한다:
 * - `PLATFORM_REAUTH_REQUIRED` — 토큰 만료·폐기, 재연동 필요
 * - `PLATFORM_SCOPE_INSUFFICIENT` — 방송 설정 권한이 없는 구 토큰, 재연동으로 새 권한 승인 필요
 */

export interface LiveSettingCategory {
  categoryType: string | null
  categoryId: string | null
  categoryValue: string | null
  posterImageUrl: string | null
}

export interface LiveSetting {
  defaultLiveTitle: string
  category: LiveSettingCategory | null
  tags: string[] | null
}

/** 재연동으로 해소되는 오류 코드인지 판별한다. */
export function isRelinkRequired(code: string | undefined): boolean {
  return code === 'PLATFORM_REAUTH_REQUIRED' || code === 'PLATFORM_SCOPE_INSUFFICIENT'
}

export async function fetchLiveSetting(platformAccountId: number): Promise<LiveSetting> {
  return apiFetch<LiveSetting>(`/platform-accounts/${platformAccountId}/live-setting`)
}

export async function updateLiveSetting(
  platformAccountId: number,
  defaultLiveTitle: string
): Promise<LiveSetting> {
  return apiFetch<LiveSetting>(`/platform-accounts/${platformAccountId}/live-setting`, {
    method: 'PATCH',
    body: JSON.stringify({ defaultLiveTitle })
  })
}
