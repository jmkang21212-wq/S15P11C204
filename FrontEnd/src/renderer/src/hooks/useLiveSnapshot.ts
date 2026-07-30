import { useCallback, useEffect, useState } from 'react'
import { getLiveSnapshot } from '@renderer/api/console'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import type { LiveSnapshot } from '@renderer/types/console'

/**
 * 실시간 콘솔 스냅샷 — `GET /broadcast-sessions/{id}/live-snapshot`.
 *
 * `useCurrentSession`과 같은 이유로 owner/follower를 나눈다 — 미니·오버레이 창은 별도
 * BrowserWindow라 authStore(메모리 전용)를 공유하지 못해 인증된 조회를 할 수 없다.
 * 'owner'(콘솔 창)만 BE가 설계한 대로 2~3초 폴링하고, 'follower'는 fetch 없이 main의
 * IPC 릴레이(snapshot:state)만 구독한다.
 */
const POLL_INTERVAL_MS = 3_000

export interface LiveSnapshotResult {
  snapshot: LiveSnapshot | null
  loading: boolean
  error: Error | null
  refresh: () => void
}

const hasSnapshotBridge = typeof window !== 'undefined' && Boolean(window.api?.snapshot)

export function useLiveSnapshot(
  sessionId: number | null,
  mode: 'owner' | 'follower' = 'owner'
): LiveSnapshotResult {
  const isOwner = mode === 'owner'

  // ── owner: 서버 폴링 ──
  const loader = useCallback(
    () => (isOwner && sessionId !== null ? getLiveSnapshot(sessionId) : Promise.resolve(null)),
    [isOwner, sessionId]
  )
  const { data, loading: asyncLoading, error, refetch } = useAsyncData(loader)

  // useAsyncData는 재조회 중 data를 null로 떨어뜨린다 — 3초 폴링과 조합하면 화면 전체가
  // 계속 스켈레톤으로 깜빡이므로, 마지막 성공값을 렌더 중 조정으로 붙들어 그걸 반환한다.
  const [lastGood, setLastGood] = useState<LiveSnapshot | null>(null)
  if (isOwner && !asyncLoading && !error && data !== lastGood) {
    setLastGood(data)
  }

  useEffect(() => {
    if (!isOwner || asyncLoading || error) return
    window.api?.snapshot?.publish(data ?? null)
  }, [isOwner, data, asyncLoading, error])

  useEffect(() => {
    if (!isOwner || sessionId === null) return
    const id = window.setInterval(refetch, POLL_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [isOwner, sessionId, refetch])

  // ── follower: main의 IPC 릴레이 구독 ──
  const [followerSnapshot, setFollowerSnapshot] = useState<LiveSnapshot | null>(null)
  const [followerLoading, setFollowerLoading] = useState(hasSnapshotBridge)

  useEffect(() => {
    if (isOwner || !window.api?.snapshot) return
    let active = true
    window.api.snapshot.getState().then((s) => {
      if (active) {
        setFollowerSnapshot(s)
        setFollowerLoading(false)
      }
    })
    const off = window.api.snapshot.onState((s) => setFollowerSnapshot(s))
    return () => {
      active = false
      off()
    }
  }, [isOwner])

  if (isOwner) {
    return {
      snapshot: lastGood,
      loading: asyncLoading && lastGood === null,
      error,
      refresh: refetch
    }
  }
  return { snapshot: followerSnapshot, loading: followerLoading, error: null, refresh: () => {} }
}
