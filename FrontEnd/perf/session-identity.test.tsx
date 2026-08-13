/**
 * `useCurrentSession`의 참조 유지 검증.
 *
 * 이 훅의 반환값은 ConsoleLayout이 Outlet context로 콘솔에 내려준다. 응답이 매번 새 객체라
 * 값이 그대로여도 참조가 달라지면, 3초 폴링을 아무리 다듬어도 15초마다 콘솔이 다시 렌더된다.
 * 콘솔 하네스(console-render.bench.tsx)는 이 context를 고정 객체로 두므로 이 경로를 재지
 * 못한다 — 그래서 여기서 따로 확인한다.
 *
 * 실행: pnpm perf:console
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, act, cleanup } from '@testing-library/react'
import type { BroadcastSessionDetail } from '@renderer/types/broadcastSession'

let current: BroadcastSessionDetail
vi.mock('@renderer/api/broadcastSessions', () => ({
  // 실제 fetch처럼 매번 새로 파싱된 객체를 준다.
  fetchCurrentSession: async () => JSON.parse(JSON.stringify(current)) as BroadcastSessionDetail
}))

function baseSession(): BroadcastSessionDetail {
  return {
    sessionId: 1,
    status: 'LIVE',
    title: '오늘도 달립니다',
    startedAt: '2026-08-13T08:00:00.000Z',
    endedAt: null,
    platformSessions: [
      {
        platform: 'CHZZK',
        channelName: '테스트 채널',
        status: 'LIVE'
      }
    ]
  } as unknown as BroadcastSessionDetail
}

beforeEach(() => {
  current = baseSession()
  vi.useFakeTimers()
  Object.defineProperty(window, 'api', {
    configurable: true,
    value: { session: { publish: () => {}, getState: async () => null, onState: () => () => {} } }
  })
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

/** owner 모드로 훅을 구독하며 매 렌더의 session 참조를 모은다. */
async function observe(ticks: number, mutate?: (n: number) => void): Promise<unknown[]> {
  const { useCurrentSession } = await import('@renderer/hooks/useCurrentSession')
  const seen: unknown[] = []

  function Probe(): null {
    const { session } = useCurrentSession('owner')
    seen.push(session)
    return null
  }

  await act(async () => {
    render(<Probe />)
  })
  await act(async () => {
    await vi.advanceTimersByTimeAsync(50)
  })

  const settled = seen[seen.length - 1]
  seen.length = 0
  seen.push(settled)

  for (let i = 0; i < ticks; i++) {
    mutate?.(i + 1)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(15_000)
    })
  }
  return seen
}

describe('useCurrentSession — 값이 그대로면 참조도 그대로', () => {
  it('응답이 동일한 동안에는 같은 객체를 돌려준다', async () => {
    const seen = await observe(5)
    const first = seen[0]
    expect(first).not.toBeNull()
    for (const s of seen) expect(s === first).toBe(true)
  })

  it('값이 바뀌면 새 객체를 돌려주고 바뀐 값이 반영된다', async () => {
    const seen = await observe(3, (n) => {
      current.title = `제목 ${n}`
    })
    const last = seen[seen.length - 1] as BroadcastSessionDetail
    expect(last.title).toBe('제목 3')
    expect(seen[seen.length - 1] === seen[0]).toBe(false)
  })
})
