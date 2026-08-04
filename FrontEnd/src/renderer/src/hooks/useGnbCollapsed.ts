import { useSyncExternalStore } from 'react'

/**
 * GNB 펼침/접힘 상태.
 * 화면(레이아웃)이 아니라 사용자가 토글로 직접 결정한다 — 앱 전체가 하나의 상태를 공유한다.
 * authStore와 동일한 패턴(모듈 스코프 state + listener set)을 쓴다.
 * 세션 메모리에만 두므로 앱을 재시작하면 기본값(펼침)으로 돌아온다.
 */
let collapsed = false
const listeners = new Set<() => void>()

function emit(): void {
  for (const listener of listeners) listener()
}

export const gnbCollapsedStore = {
  get: (): boolean => collapsed,
  toggle: (): void => {
    collapsed = !collapsed
    emit()
  },
  subscribe: (listener: () => void): (() => void) => {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  }
}

/** GNB 펼침/접힘 상태를 구독하는 훅. */
export function useGnbCollapsed(): { collapsed: boolean; toggle: () => void } {
  const value = useSyncExternalStore(gnbCollapsedStore.subscribe, gnbCollapsedStore.get)
  return { collapsed: value, toggle: gnbCollapsedStore.toggle }
}
