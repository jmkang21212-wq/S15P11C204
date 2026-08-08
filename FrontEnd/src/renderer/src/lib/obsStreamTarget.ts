import type { ObsStreamService } from '@renderer/types/obs'
import type { PlatformProvider } from '@renderer/types/platformAccount'

/**
 * OBS에 설정된 송출 대상을 LAIVE 플랫폼으로 판별한다.
 *
 * LAIVE는 `StartStream`으로 송출을 켜기만 하고 목적지는 정하지 않는다 — 목적지는 OBS
 * 설정이 소유한다. 반대 방향(LAIVE가 OBS 설정을 바꾸기)은 스트림 키가 필요해서 못 한다:
 * YouTube는 API로 키를 얻을 수 있지만 치지직은 우리 연동에 그 경로가 없다. 그래서 OBS를
 * 단일 진실로 두고 LAIVE가 따라간다.
 *
 * OBS는 두 형태로 답한다.
 *  - `rtmp_common` — 서비스 목록에서 고른 경우. `service`에 "YouTube - RTMP" 같은 이름이 온다
 *  - `rtmp_custom` — 사용자 지정 RTMP. `service`가 없고 `server`에 주소가 온다
 *
 * 그래서 둘을 한 문자열로 합쳐 훑는다. 서비스 이름은 OBS 버전·로케일에 따라 달라지므로
 * 정확한 일치를 기대하지 않고 키워드로 본다.
 */

const PATTERNS: { platform: PlatformProvider; pattern: RegExp }[] = [
  { platform: 'YOUTUBE', pattern: /youtube|youtu\.be/i },
  // 치지직은 서비스 목록에 없는 OBS 버전이 있어 사용자 지정 RTMP로 넣는 경우가 많다.
  // 그때 서버 주소가 naver 도메인이라 함께 본다.
  { platform: 'CHZZK', pattern: /chzzk|치지직|naver/i }
]

export interface ObsStreamTarget {
  /** 판별된 플랫폼. 알 수 없으면 null. */
  platform: PlatformProvider | null
  /**
   * 판별에 쓴 원본 값. 알 수 없을 때 화면에 그대로 보여준다 — 낯선 서비스 이름을 만나도
   * 사용자가 무엇이 설정돼 있는지는 알 수 있어야 한다.
   */
  label: string
}

export function resolveStreamTarget(settings: ObsStreamService): ObsStreamTarget {
  const haystack = [settings.service, settings.server].filter(Boolean).join(' ')
  const matched = PATTERNS.find((entry) => entry.pattern.test(haystack))
  return {
    platform: matched?.platform ?? null,
    // 서비스 이름이 있으면 그게 읽기 좋다. 사용자 지정이면 서버 주소를 보여준다.
    label: settings.service ?? settings.server ?? '알 수 없음'
  }
}
