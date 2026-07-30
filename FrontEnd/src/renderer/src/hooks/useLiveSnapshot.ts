import { useEffect, useState } from 'react'
import { getLiveSnapshot } from '@renderer/api/console'
import type { LiveSnapshot } from '@renderer/types/console'

/** 백엔드가 폴링을 전제로 설계한 주기(2~3초). */
const POLL_INTERVAL_MS = 2500

interface UseLiveSnapshotResult {
  data: LiveSnapshot | null
  /** 첫 응답 전에만 true — 폴링 갱신 때 화면이 깜빡이지 않게 한다. */
  loading: boolean
  error: Error | null
}

/**
 * 실시간 콘솔 스냅샷을 주기적으로 폴링한다 (명세 10.6, WebSocket 대신 폴링).
 *
 * 실패해도 직전 data를 유지한다. 방송 중 한 번의 조회 실패로 화면이 비면
 * 스트리머는 방송이 끊긴 것으로 오해한다.
 */
export function useLiveSnapshot(sessionId: number): UseLiveSnapshotResult {
  const [data, setData] = useState<LiveSnapshot | null>(null)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let active = true
    let timer: number | null = null

    const poll = async (): Promise<void> => {
      try {
        const snapshot = await getLiveSnapshot(sessionId)
        if (!active) return
        setData(snapshot)
        setError(null)
      } catch (e) {
        if (!active) return
        setError(e instanceof Error ? e : new Error('콘솔 상태를 불러오지 못했어요'))
      } finally {
        // 응답이 온 뒤에 다음 호출을 예약한다 — 느린 응답이 쌓이지 않게.
        if (active) timer = window.setTimeout(() => void poll(), POLL_INTERVAL_MS)
      }
    }

    void poll()

    return () => {
      active = false
      if (timer !== null) window.clearTimeout(timer)
    }
  }, [sessionId])

  // 첫 응답(성공이든 실패든)이 오기 전까지만 로딩이다. 별도 state를 두지 않아
  // effect에서 동기 setState를 하지 않는다.
  return { data, loading: data === null && error === null, error }
}
