import { apiFetch } from './client'
import type { ClipFailureInput, ClipJob, ClipUploadResult } from '@renderer/types/clipJob'

/**
 * 클립 작업 조회·업로드·실패 보고 API — `ClipController` (CLIP_SPEC.md §6.4~6.6).
 * 서버가 작업을 밀어주지 않고 앱이 가져가는 구조라, 부팅 시 1회 + 리포트 완료 감지 시
 * `fetchClipJobs`를 부르는 게 호출부(useClipWorker)의 책임이다.
 */

/** 아직 클립이 없고 녹화본이 준비된 하이라이트 작업 목록. 없으면 빈 배열. */
export async function fetchClipJobs(): Promise<ClipJob[]> {
  const data = await apiFetch<{ jobs: ClipJob[] }>('/clip-jobs')
  return data.jobs
}

/**
 * 클립과 썸네일을 업로드한다. 파트 이름(`clip`/`thumbnail`)과 Content-Type을
 * 정확히 맞춰야 한다 — 서버가 검사해서 안 맞으면 `CLIP_INVALID_FILE`로 거부한다.
 * 이미 올린 하이라이트에 다시 올려도 200에 기존 URL이 온다(중복 업로드는 정상 흐름).
 */
export async function uploadClip(
  highlightId: number,
  clip: Uint8Array,
  thumbnail: Uint8Array
): Promise<ClipUploadResult> {
  // IPC로 넘어온 Uint8Array는 buffer 타입이 ArrayBufferLike(SharedArrayBuffer 포함)로
  // 잡혀 BlobPart와 안 맞을 수 있다 — new Uint8Array(...)로 감싸 구체 ArrayBuffer로 되돌린다.
  const form = new FormData()
  form.append('clip', new File([new Uint8Array(clip)], 'clip.mp4', { type: 'video/mp4' }))
  form.append(
    'thumbnail',
    new File([new Uint8Array(thumbnail)], 'thumbnail.jpg', { type: 'image/jpeg' })
  )

  return apiFetch<ClipUploadResult>(`/highlights/${highlightId}/clip`, {
    method: 'POST',
    body: form
  })
}

/**
 * 클립을 만들지 못했음을 보고한다. 이걸 안 보내면 서버가 영원히 PENDING으로 들고
 * 있다가 앱이 켜질 때마다 같은 작업을 다시 준다 — 실패는 반드시 보고한다.
 */
export async function reportClipFailure(
  highlightId: number,
  input: ClipFailureInput
): Promise<void> {
  await apiFetch<undefined>(`/highlights/${highlightId}/clip/failure`, {
    method: 'POST',
    body: JSON.stringify(input)
  })
}
