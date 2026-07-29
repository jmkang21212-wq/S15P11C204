import { useEffect, useState } from 'react'
import type { Briefing } from '@renderer/types/console'

/**
 * 콘솔 창이 받은 실시간 브리핑을 구독한다 (미니 창에서 사용).
 *
 * 오디오 스트림은 콘솔 창이 단독으로 소유하고, main이 그 결과를 중계한다.
 * main은 페이로드를 해석하지 않으므로 타입 확정은 여기서 한 번만 한다.
 */
export function useLiveBriefings(): Briefing[] {
  const [briefings, setBriefings] = useState<Briefing[]>([])

  useEffect(() => {
    let active = true

    const merge = (incoming: Briefing[]): void => {
      setBriefings((prev) => {
        // recent()와 onNew가 겹칠 수 있어 briefingId로 중복을 제거한다.
        const byId = new Map<number, Briefing>()
        for (const briefing of [...incoming, ...prev]) byId.set(briefing.briefingId, briefing)
        return [...byId.values()].sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        )
      })
    }

    // 구독을 먼저 걸어야 초기 조회 사이에 도착한 브리핑을 놓치지 않는다.
    const unsubscribe = window.api.briefing.onNew((briefing) => {
      if (active) merge([briefing as Briefing])
    })
    void window.api.briefing.recent().then((list) => {
      if (active) merge(list as Briefing[])
    })

    return () => {
      active = false
      unsubscribe()
    }
  }, [])

  return briefings
}
