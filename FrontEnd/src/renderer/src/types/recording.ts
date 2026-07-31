/**
 * OBS 로컬 녹화 보고 도메인 타입 — `RecordingController`
 * (`/api/v1/broadcast-sessions/{sessionId}/recording`, CLIP_SPEC.md §6.1~6.3).
 *
 * 서버 Instant는 Jackson 기본 직렬화로 ISO-8601 문자열로 오간다.
 */

/** BE `RecordingStatus` 중 종료 보고에 실제로 쓰이는 값만. RECORDING은 서버 내부 상태다. */
export type FinishedRecordingStatus = 'COMPLETED' | 'FAILED'

export interface StartRecordingInput {
  startedAt: string
  baseWallClock: string
  /** 녹화 시작 시점의 경과 시간(ms). 시작 직후라 보통 0에 가깝다. */
  baseTimecodeMs: number
}

/** 두 값은 같은 순간에 읽어야 한다 — 사이가 벌어지면 그만큼이 그대로 클립 위치 오차가 된다. */
export interface UpdateRecordingBaseInput {
  baseWallClock: string
  baseTimecodeMs: number
}

export interface FinishRecordingInput {
  endedAt: string
  /** 스트리머 PC의 최종 파일 경로. 서버는 해석하지 않고 클립 작업에 그대로 실어 돌려준다. */
  outputPath: string
  status: FinishedRecordingStatus
}
