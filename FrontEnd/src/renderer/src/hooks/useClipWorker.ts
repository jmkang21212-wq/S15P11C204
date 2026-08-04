import { useEffect } from 'react'
import { fetchClipJobs, reportClipFailure, uploadClip } from '@renderer/api/clips'

/**
 * 하이라이트 클립 생성 파이프라인.
 *
 * 서버가 작업을 밀어주지 않고 앱이 가져가는 구조라(CLIP_SPEC.md), 두 시점에 깨운다:
 *   1. 앱 부팅 시 1회 (`useClipWorker`, AppLayout에 마운트)
 *   2. 리포트가 PROCESSING을 벗어나는 걸 감지했을 때 (SessionReportDetailPage)
 *
 * 두 트리거가 겹칠 수 있어 모듈 레벨 `running`으로 동시 실행을 막는다 — 5개를
 * 병렬로 인코딩하면 방송 직후 PC가 멎는다. 반드시 하나씩 순차 처리한다.
 */

let running: Promise<void> | null = null

/** main IPC(clip:cut) 응답 타입 — window.api.clip.cut의 반환 형태와 같다. */
type CutResult =
  | { ok: true; clip: Uint8Array; thumbnail: Uint8Array }
  | { ok: false; permanent: boolean; reason: string }

async function processJobs(): Promise<void> {
  const jobs = await fetchClipJobs().catch(() => [])

  for (const job of jobs) {
    let result: CutResult
    try {
      result = await window.api.clip.cut({
        recordingPath: job.recordingPath,
        clipStartSec: job.clipStartSec,
        clipDurationSec: job.clipDurationSec
      })
    } catch (e) {
      // ffmpeg IPC 자체가 죽은 경우도 실패 보고 대상이다 — 조용히 넘기면 서버가
      // 이 작업을 영원히 PENDING으로 들고 있는다.
      await reportClipFailure(job.highlightId, {
        reason: e instanceof Error ? e.message : String(e),
        permanent: false
      }).catch(() => undefined)
      continue
    }

    try {
      if (result.ok) {
        await uploadClip(job.highlightId, result.clip, result.thumbnail)
      } else {
        await reportClipFailure(job.highlightId, {
          reason: result.reason,
          permanent: result.permanent
        })
      }
    } catch {
      // 업로드·실패 보고 자체가 네트워크 문제로 실패했다면 다음 트리거 때 이 작업이
      // 다시 내려온다(서버가 PENDING으로 유지) — 여기서 더 재시도하지 않는다.
    }
  }
}

/** 여러 트리거가 겹쳐도 한 번에 하나의 파이프라인만 돈다. */
export function triggerClipWorker(): void {
  if (running) return
  running = processJobs().finally(() => {
    running = null
  })
}

/** 부팅 시 1회 클립 작업을 확인한다. AppLayout에 마운트한다. */
export function useClipWorker(): void {
  useEffect(() => {
    triggerClipWorker()
  }, [])
}
