import { useSyncExternalStore } from 'react'
import type { Streamer } from '@renderer/types/auth'

/**
 * 인증 상태 저장소.
 * Access Token은 보안상 localStorage가 아니라 메모리에만 보관한다.
 * (Refresh Token은 HttpOnly 쿠키로 백엔드가 관리)
 */
interface AuthState {
  accessToken: string | null
  streamer: Streamer | null
}

let state: AuthState = { accessToken: null, streamer: null }
const listeners = new Set<() => void>()

function emit(): void {
  for (const listener of listeners) listener()
}

export const authStore = {
  get: (): AuthState => state,
  getToken: (): string | null => state.accessToken,
  set: (next: Partial<AuthState>): void => {
    state = { ...state, ...next }
    emit()
  },
  clear: (): void => {
    state = { accessToken: null, streamer: null }
    emit()
  },
  subscribe: (listener: () => void): (() => void) => {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  }
}

/** 로그인 상태를 구독하는 훅. */
export function useAuth(): AuthState {
  return useSyncExternalStore(authStore.subscribe, authStore.get)
}
