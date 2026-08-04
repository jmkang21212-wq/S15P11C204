import { apiFetch } from './client'
import type {
  FinishRecordingInput,
  StartRecordingInput,
  UpdateRecordingBaseInput
} from '@renderer/types/recording'

/**
 * OBS 로컬 녹화 보고 API — `RecordingController` (CLIP_SPEC.md §6.1~6.3).
 * 전부 `204 No Content`. 세 엔드포인트 다 재시도가 정상 흐름이라 실패해도 조용히
 * 무시되도록 서버가 설계돼 있다(같은 세션에 시작을 두 번 보내도 에러가 아님 등).
 */

const BASE = '/broadcast-sessions'

/** 녹화 시작 보고. 같은 세션에 두 번 보내도 에러가 아니다 — 기준점만 갱신된다. */
export async function startRecording(sessionId: number, input: StartRecordingInput): Promise<void> {
  await apiFetch<undefined>(`${BASE}/${sessionId}/recording`, {
    method: 'POST',
    body: JSON.stringify(input)
  })
}

/** 시간 기준점 갱신(60초 주기). 녹화가 이미 종료됐으면 조용히 무시된다. */
export async function updateRecordingBase(
  sessionId: number,
  input: UpdateRecordingBaseInput
): Promise<void> {
  await apiFetch<undefined>(`${BASE}/${sessionId}/recording/base`, {
    method: 'PATCH',
    body: JSON.stringify(input)
  })
}

/**
 * 녹화 종료 보고. `status: 'COMPLETED'`를 보내야 그 세션의 클립 작업이 생긴다.
 * 녹화가 중간에 깨졌으면 `FAILED`를 보낸다 — 작업 자체가 만들어지지 않는다.
 */
export async function finishRecording(
  sessionId: number,
  input: FinishRecordingInput
): Promise<void> {
  await apiFetch<undefined>(`${BASE}/${sessionId}/recording`, {
    method: 'PATCH',
    body: JSON.stringify(input)
  })
}
