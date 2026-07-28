import { useEffect, useState } from 'react'
import { getLiveSnapshot } from '@renderer/api/console'
import type { LiveSnapshot } from '@renderer/types/console'

interface UseLiveSnapshotResult {
  data: LiveSnapshot | null
  isLoading: boolean
  error: Error | null
}

/**
 * 방송 콘솔 초기 스냅샷 로딩 훅.
 * 현재는 mock을 반환한다. 실제 연동 시 이 훅 안에서 초기 스냅샷을 받고,
 * WebSocket 이벤트로 data를 갱신하도록 확장한다.
 */
export function useLiveSnapshot(sessionId: number): UseLiveSnapshotResult {
  const [data, setData] = useState<LiveSnapshot | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let active = true
    setIsLoading(true)
    getLiveSnapshot(sessionId)
      .then((result) => {
        if (active) setData(result)
      })
      .catch((e) => {
        if (active) setError(e instanceof Error ? e : new Error('스냅샷 로딩 실패'))
      })
      .finally(() => {
        if (active) setIsLoading(false)
      })
    return () => {
      active = false
    }
  }, [sessionId])

  return { data, isLoading, error }
}
