/**
 * 클립 생성/업로드 도메인 타입 — `ClipController` (`/api/v1/clip-jobs`,
 * `/api/v1/highlights/{highlightId}/clip*`, CLIP_SPEC.md §6.4~6.6).
 */

/**
 * 앱이 ffmpeg에 그대로 넣을 수 있는 형태의 작업.
 * `clipStartSec`은 송출 지연 보정까지 서버가 끝낸 값이다 — 클라이언트에서 다시
 * 계산하지 않는다.
 */
export interface ClipJob {
  highlightId: number
  sessionId: number
  sessionTitle: string
  /** 스트리머 PC의 OBS 녹화본 경로. */
  recordingPath: string
  clipStartSec: number
  clipDurationSec: number
}

export interface ClipUploadResult {
  clipUrl: string
  thumbnailUrl: string
}

export interface ClipFailureInput {
  /** 진단용 원인. 서버가 200자에서 자른다. */
  reason?: string
  /**
   * 재시도해도 결과가 같은 실패인지. true면 즉시 포기(SKIPPED),
   * false면 시도 횟수만 늘고 3회째 포기한다.
   */
  permanent: boolean
}
