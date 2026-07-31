import { useEffect } from 'react'
import { updateRecordingBase } from '@renderer/api/recording'
import type { ObsRecordStatus } from '@renderer/types/obs'

const INTERVAL_MS = 60_000

/**
 * 녹화 중 60초마다 시간 기준점(wall clock ↔ 녹화 경과시간)을 서버에 갱신한다.
 *
 * OBS 녹화가 프레임 드랍으로 실제 시각과 조금씩 어긋나는 드리프트가 있어, 가장 최근
 * 기준점을 써야 클립 컷 위치 오차가 쌓이지 않는다. `BroadcastConsolePage`에서만
 * 마운트한다 — 미니·오버레이 창은 별도 BrowserWindow라 authStore(메모리 전용 토큰)를
 * 공유하지 못해 인증된 조회를 할 수 없다(useCurrentSession·useLiveSnapshot과 같은 원칙).
 */
export function useRecordingReporter(
  sessionId: number | null,
  recording: boolean,
  getRecordStatus: () => Promise<ObsRecordStatus>
): void {
  useEffect(() => {
    if (sessionId === null || !recording) return

    const tick = (): void => {
      // baseWallClock과 baseTimecodeMs는 같은 순간에 읽어야 한다 — 사이가 벌어지면
      // 그만큼이 그대로 클립 위치 오차가 된다. IPC 왕복 직후를 "같은 순간"으로 본다.
      void getRecordStatus()
        .then((status) =>
          updateRecordingBase(sessionId, {
            baseWallClock: new Date().toISOString(),
            baseTimecodeMs: status.outputDuration
          })
        )
        // 실패해도 다음 60초 주기에 다시 시도한다 — 서버도 이 요청의 실패를 조용히 넘긴다
        // (녹화 종료가 먼저 처리되고 마지막 갱신이 뒤늦게 온 순서 역전이 정상 흐름이다).
        .catch(() => undefined)
    }

    const id = window.setInterval(tick, INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [sessionId, recording, getRecordStatus])
}
