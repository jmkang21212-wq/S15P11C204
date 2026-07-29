import { useCallback, useEffect, useState } from 'react'
import { fetchCurrentSession } from '@renderer/api/broadcastSessions'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import type { BroadcastSessionDetail } from '@renderer/types/broadcastSession'

/**
 * 진행 중(PREPARING·LIVE) 방송 세션 상태.
 *
 * 'owner'(메인 창)만 GET /broadcast-sessions/current를 직접 폴링한다. 미니·오버레이는
 * 별도 BrowserWindow라 authStore(메모리 전용)를 공유하지 못하고, Refresh Token이 1회용으로
 * 회전해 여러 창이 동시에 401→refresh를 시도하면 서로를 로그아웃시키기 때문이다.
 * 'follower'(미니·오버레이)는 fetch 없이 main의 IPC 릴레이(session:state)만 구독한다.
 *
 * mode는 호출부(페이지)마다 고정값으로 넘긴다는 전제다 — 훅 호출 순서를 일정하게 유지하려고
 * 두 갈래를 한 함수 안에서 항상 같이 실행하고, 쓰지 않는 쪽은 조건으로 무력화한다.
 *
 * TODO(realtime): 콘솔 WebSocket 도입 시 owner 갈래의 폴링만 소켓 구독으로 바꾸면 되고,
 *   릴레이 구조와 이 훅의 반환 형태는 그대로 재사용한다.
 */

const POLL_INTERVAL_MS = 15_000

export interface CurrentSessionContext {
  session: BroadcastSessionDetail | null
  loading: boolean
  error: Error | null
  refresh: () => void
}
type UseCurrentSessionResult = CurrentSessionContext

const hasSessionBridge = typeof window !== 'undefined' && Boolean(window.api?.session)

export function useCurrentSession(mode: 'owner' | 'follower' = 'owner'): UseCurrentSessionResult {
  const isOwner = mode === 'owner'

  // ── owner: 서버 폴링 ──
  const loader = useCallback(
    () => (isOwner ? fetchCurrentSession() : Promise.resolve(null)),
    [isOwner]
  )
  const { data, loading: asyncLoading, error, refetch } = useAsyncData(loader)

  // useAsyncData는 재조회 중 data를 null로 떨어뜨린다 — 폴링과 조합하면 LIVE 배너가
  // 15초마다 깜빡이므로, 마지막으로 성공한 값을 별도 state로 붙들어 그걸 반환한다.
  // effect가 아니라 렌더 중 조건부 setState로 조정한다 — React가 공식적으로 허용하는
  // "이전 렌더 값을 저장" 패턴이며, 다음 값이 같으면 다시 렌더하지 않는다.
  const [lastGood, setLastGood] = useState<BroadcastSessionDetail | null>(null)
  if (isOwner && !asyncLoading && !error && data !== lastGood) {
    setLastGood(data)
  }

  useEffect(() => {
    if (!isOwner || asyncLoading || error) return
    window.api?.session?.publish(data ?? null)
  }, [isOwner, data, asyncLoading, error])

  useEffect(() => {
    if (!isOwner) return
    const id = window.setInterval(refetch, POLL_INTERVAL_MS)
    const onFocus = (): void => refetch()
    window.addEventListener('focus', onFocus)
    return () => {
      window.clearInterval(id)
      window.removeEventListener('focus', onFocus)
    }
  }, [isOwner, refetch])

  // ── follower: main의 IPC 릴레이 구독 ──
  const [followerSession, setFollowerSession] = useState<BroadcastSessionDetail | null>(null)
  // 브릿지가 없으면(브라우저에서 직접 연 개발 환경) 영영 로딩 중으로 남지 않게 처음부터 false.
  const [followerLoading, setFollowerLoading] = useState(hasSessionBridge)

  useEffect(() => {
    if (isOwner || !window.api?.session) return
    let active = true
    window.api.session.getState().then((s) => {
      if (active) {
        setFollowerSession(s)
        setFollowerLoading(false)
      }
    })
    const off = window.api.session.onState((s) => setFollowerSession(s))
    return () => {
      active = false
      off()
    }
  }, [isOwner])

  if (isOwner) {
    return {
      session: lastGood,
      loading: asyncLoading && lastGood === null,
      error,
      refresh: refetch
    }
  }
  return { session: followerSession, loading: followerLoading, error: null, refresh: () => {} }
}
