/**
 * 실시간 스냅샷 릴레이(owner/follower) 요청량 측정.
 *
 * 방송 중에는 창이 최대 셋 뜬다 — 콘솔(메인)·미니 모드·OBS 오버레이. 셋 다 같은
 * `GET /broadcast-sessions/{id}/live-snapshot`을 보여주는데, 각자 폴링하면 서버가 받는
 * 요청이 창 수만큼 배로 늘고 창끼리 값도 어긋난다(3초 주기가 서로 어긋난 채 돈다).
 *
 * 그래서 콘솔 창만 owner로 폴링하고, 미니·오버레이는 main을 거친 IPC 릴레이
 * (`window.api.snapshot`)를 구독한다(hooks/useLiveSnapshot.ts). 이 하네스는 그 구조가
 * 실제로 요청을 몇 번으로 줄이는지, 그러면서 창끼리 같은 값을 보는지 확인한다.
 *
 * 대조군(창마다 독립 폴링)은 릴레이를 도입하기 전 구조를 재현한 것이다.
 *
 * 실행: pnpm perf:console (perf/ 전체가 같이 돈다)
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, act, cleanup } from '@testing-library/react'
import type { LiveSnapshot } from '@renderer/types/console'

let apiCalls = 0
let tick = 0

function snapshotAt(n: number): LiveSnapshot {
  const payload: LiveSnapshot = {
    session: {
      sessionId: 1,
      status: 'LIVE',
      startedAt: '2026-08-13T08:00:00.000Z',
      elapsedSeconds: 3600 + n * 3,
      currentViewers: 1240 + (n % 7)
    },
    services: { microphone: 'CONNECTED', stt: 'RUNNING', ai: 'RUNNING', collector: 'RUNNING' },
    briefings: Array.from({ length: 6 }, (_, i) => ({
      briefingId: i + 1,
      briefingType: i % 2 === 0 ? 'RISK' : 'PROMISE',
      message: `브리핑 메시지 ${i + 1}`,
      createdAt: '2026-08-13T08:30:00.000Z'
    })),
    sentiment: {
      windowSec: 180,
      analyzedChat: 420,
      engagement: 0.18,
      dominant: 'JOY',
      distribution: { JOY: 0.42, EXCITEMENT: 0.2, SUPPORT: 0.15, NEUTRAL: 0.23 }
    },
    highlight: {
      phase: 'IDLE',
      z: 1.2,
      zEnter: 2.5,
      engagement: 0.12,
      windowChats: 88,
      uniqueChatters: 41,
      skipReason: null,
      warmupUntil: null,
      engageWindowSec: 60,
      detectedCount: 2,
      lastDetectedAt: '2026-08-13T08:40:00.000Z'
    },
    repeatedQuestions: Array.from({ length: 4 }, (_, i) => ({
      questionId: i + 1,
      text: `반복 질문 ${i + 1}`,
      type: 'FAQ' as const,
      askers: 12 - i,
      messages: 20 - i,
      reasked: false,
      firstAskedAt: '2026-08-13T08:10:00.000Z',
      lastAskedAt: '2026-08-13T08:50:00.000Z'
    })),
    topViewers: Array.from({ length: 5 }, (_, i) => ({
      viewerId: i + 1,
      nickname: `열성 시청자 ${i + 1}`,
      viewerAlias: `viewer-a${i}1c`,
      chatScore: 30 - i * 2,
      watchScore: 40 - i * 3,
      totalScore: 100 - i * 7
    })),
    lastEventId: `evt-${n}`
  }
  return JSON.parse(JSON.stringify(payload)) as LiveSnapshot
}

vi.mock('@renderer/api/console', () => ({
  getLiveSnapshot: async () => {
    apiCalls += 1
    return snapshotAt(tick)
  }
}))

/** main(IPC)의 릴레이를 대신하는 최소 버스 — publish한 값을 구독자에게 그대로 넘긴다. */
function createSnapshotBus(): {
  publish: (s: LiveSnapshot | null) => void
  getState: () => Promise<LiveSnapshot | null>
  onState: (cb: (s: LiveSnapshot | null) => void) => () => void
} {
  let state: LiveSnapshot | null = null
  const subscribers = new Set<(s: LiveSnapshot | null) => void>()
  return {
    publish: (s) => {
      state = s
      for (const cb of subscribers) cb(s)
    },
    getState: async () => state,
    onState: (cb) => {
      subscribers.add(cb)
      return () => subscribers.delete(cb)
    }
  }
}

const TICKS = 20
const SESSION_ID = 1

interface Result {
  label: string
  windows: number
  apiCalls: number
  /** 마지막 tick에서 창들이 실제로 같은 값을 보고 있었는지 */
  inSync: boolean
}

const results: Result[] = []

beforeEach(() => {
  apiCalls = 0
  tick = 0
  vi.useFakeTimers()
  Object.defineProperty(window, 'api', {
    configurable: true,
    value: { snapshot: createSnapshotBus() }
  })
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

/**
 * @param modes 창 구성. 'owner'는 직접 폴링, 'follower'는 IPC 릴레이 구독.
 */
async function measure(label: string, modes: ('owner' | 'follower')[]): Promise<Result> {
  const { useLiveSnapshot } = await import('@renderer/hooks/useLiveSnapshot')

  const seen: (LiveSnapshot | null)[] = modes.map(() => null)

  function Window({ index, mode }: { index: number; mode: 'owner' | 'follower' }): null {
    const { snapshot } = useLiveSnapshot(SESSION_ID, mode)
    seen[index] = snapshot
    return null
  }

  await act(async () => {
    render(
      <>
        {modes.map((mode, i) => (
          <Window key={i} index={i} mode={mode} />
        ))}
      </>
    )
  })
  await act(async () => {
    await vi.advanceTimersByTimeAsync(50)
  })

  for (let i = 0; i < TICKS; i++) {
    tick = i + 1
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000)
    })
  }

  const first = JSON.stringify(seen[0])
  const inSync = seen.every((s) => JSON.stringify(s) === first)
  return { label, windows: modes.length, apiCalls, inSync }
}

describe(`실시간 스냅샷 릴레이 — ${TICKS} tick 동안의 서버 요청 수`, () => {
  it('콘솔 1창 (릴레이가 필요 없는 기준점)', async () => {
    const r = await measure('콘솔만', ['owner'])
    results.push(r)
    expect(r.apiCalls).toBeGreaterThan(0)
  })

  it('콘솔 + 미니 — 대조군: 창마다 독립 폴링', async () => {
    results.push(await measure('콘솔+미니 (독립 폴링)', ['owner', 'owner']))
  })

  it('콘솔 + 미니 — 현재 설계: owner 1 + follower 1', async () => {
    const r = await measure('콘솔+미니 (릴레이)', ['owner', 'follower'])
    results.push(r)
    expect(r.inSync).toBe(true)
  })

  it('콘솔 + 미니 + 오버레이 — 대조군: 창마다 독립 폴링', async () => {
    results.push(await measure('3창 (독립 폴링)', ['owner', 'owner', 'owner']))
  })

  it('콘솔 + 미니 + 오버레이 — 현재 설계: owner 1 + follower 2', async () => {
    const r = await measure('3창 (릴레이)', ['owner', 'follower', 'follower'])
    results.push(r)
    // 창이 늘어도 요청 수는 콘솔 1창과 같아야 한다.
    expect(r.apiCalls).toBe(results[0].apiCalls)
    expect(r.inSync).toBe(true)
  })

  it('결과 출력', () => {
    const payloadBytes = Buffer.byteLength(JSON.stringify(snapshotAt(1)), 'utf-8')
    const perHour = (calls: number): number => Math.round((calls / TICKS) * 1200)

    console.log(`\n=== 스냅샷 릴레이 (${TICKS} tick · 3초 주기) ===`)
    console.log(
      '구성'.padEnd(24) +
        '창'.padStart(3) +
        'API 호출'.padStart(10) +
        '요청/시간'.padStart(11) +
        '전송/시간'.padStart(12)
    )
    for (const r of results) {
      const hourly = perHour(r.apiCalls)
      console.log(
        r.label.padEnd(24) +
          String(r.windows).padStart(3) +
          String(r.apiCalls).padStart(10) +
          String(hourly).padStart(11) +
          `${((hourly * payloadBytes) / 1024 / 1024).toFixed(1)}MB`.padStart(12)
      )
    }
    console.log(`스냅샷 페이로드 ${payloadBytes}B (이 하네스의 합성 응답 기준)`)
    expect(results).toHaveLength(5)
  })
})
