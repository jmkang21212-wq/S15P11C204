import { useEffect, useSyncExternalStore } from 'react'
import { fetchPlatformAccounts } from '@renderer/api/platformAccounts'
import { authStore } from '@renderer/auth/authStore'
import type { PlatformAccount } from '@renderer/types/platformAccount'

/**
 * 플랫폼 연동 계정 — GNB 하단·대시보드·연동 화면 셋이 공유하는 단일 저장소.
 * 각자 조회하면 화면 하나에 GET이 여러 번 나가고, 연동·해제 직후 다른 화면이
 * 갱신되지 않는다. `authStore`(useSyncExternalStore + 모듈 스코프 state)와 같은 패턴을 쓴다.
 */
interface PlatformAccountState {
  accounts: PlatformAccount[] | null
  loading: boolean
  error: Error | null
}

let state: PlatformAccountState = { accounts: null, loading: false, error: null }
const listeners = new Set<() => void>()
let inFlight: Promise<void> | null = null

function setState(next: Partial<PlatformAccountState>): void {
  state = { ...state, ...next }
  for (const listener of listeners) listener()
}

async function run(): Promise<void> {
  setState({ loading: true, error: null })
  try {
    const accounts = await fetchPlatformAccounts()
    setState({ accounts, loading: false })
  } catch (e) {
    setState({ error: e instanceof Error ? e : new Error(String(e)), loading: false })
  }
}

export const platformAccountStore = {
  get: (): PlatformAccountState => state,
  subscribe: (listener: () => void): (() => void) => {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  /** 캐시가 있으면 재조회하지 않는다. 동시 호출은 진행 중인 요청 하나로 합친다. */
  load: (): Promise<void> => {
    if (state.accounts !== null) return Promise.resolve()
    if (!inFlight) {
      inFlight = run().finally(() => {
        inFlight = null
      })
    }
    return inFlight
  },
  /** 캐시를 버리고 무조건 다시 조회한다 — 연동·해제 성공 직후에 호출. */
  refresh: (): Promise<void> => {
    inFlight = run().finally(() => {
      inFlight = null
    })
    return inFlight
  }
}

// 로그아웃(또는 401로 인한 강제 로그아웃)되면 이전 사용자의 채널 정보가 남지 않게 캐시를 비운다.
authStore.subscribe(() => {
  if (authStore.getToken() === null && state.accounts !== null) {
    setState({ accounts: null, loading: false, error: null })
  }
})

export function usePlatformAccounts(): PlatformAccountState {
  const snapshot = useSyncExternalStore(platformAccountStore.subscribe, platformAccountStore.get)
  useEffect(() => {
    void platformAccountStore.load()
  }, [])
  return snapshot
}

/** 방송 시작 가능 여부 판단에 쓰는 대표 계정 — CONNECTED인 첫 번째. */
export function usePrimaryPlatformAccount(): {
  account: PlatformAccount | null
  loading: boolean
  error: Error | null
} {
  const { accounts, loading, error } = usePlatformAccounts()
  return {
    account: accounts?.find((a) => a.connectionStatus === 'CONNECTED') ?? null,
    loading,
    error
  }
}
