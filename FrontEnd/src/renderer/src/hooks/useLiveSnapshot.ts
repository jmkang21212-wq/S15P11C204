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
  //
  // 로딩 판정은 lastGood이 아니라 "이 loader가 한 번이라도 완료됐는지"로 한다
  // (useCurrentSession과 같은 이유) — 조회가 계속 실패하면 lastGood이 null로 남아, 그걸
  // 로딩으로 치면 3초마다 콘솔 전체가 스켈레톤으로 되돌아간다. 실패는 error로 따로 알린다.
  //
  // boolean이 아니라 loader를 기억하는 이유는 sessionId가 바뀔 때다(방송 시작 등). 그때는
  // 아직 아무것도 못 받은 새 세션이라 스켈레톤이 맞고, 3초 폴링(nonce만 증가)은 loader가
  // 그대로라 스켈레톤으로 돌아가지 않는다.
  //
  // loader를 객체로 감싸 두는 건 필수다 — setState에 함수를 그대로 넘기면 React가 값이
  // 아니라 업데이터로 해석해 loader(prev)를 실행해 버리고, 저장된 값이 영영 loader와
  // 같아지지 않아 렌더 루프가 된다.
  const [lastGood, setLastGood] = useState<LiveSnapshot | null>(null)
  const [settled, setSettled] = useState<{ loader: unknown } | null>(null)
  if (isOwner && !asyncLoading) {
    if (!error && data !== lastGood) setLastGood(data)
    if (settled?.loader !== loader) setSettled({ loader })
  }

  useEffect(() => {
    if (!isOwner || asyncLoading || error) return
    window.api?.snapshot?.publish(data ?? null)
  }, [isOwner, data, asyncLoading, error])

  useEffect(() => {
    if (!isOwner || sessionId === null) return
    const id = window.setInterval(refetch, POLL_INTERVAL_MS)
    // 창으로 돌아왔을 때 다음 tick을 기다리지 않고 한 번 당긴다. main의 전력·스로틀
    // 보호(main/power.ts)가 못 막는 경우 — 사용자가 직접 재운 절전, 최대 절전 — 에
    // 폴링이 밀렸다면 그 공백을 여기서 메운다(useCurrentSession과 같은 안전망).
    const onFocus = (): void => refetch()
    window.addEventListener('focus', onFocus)
    return () => {
      window.clearInterval(id)
      window.removeEventListener('focus', onFocus)
    }
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
      loading: asyncLoading && settled?.loader !== loader,
      error,
      refresh: refetch
    }
  }
  return { snapshot: followerSnapshot, loading: followerLoading, error: null, refresh: () => {} }
}
