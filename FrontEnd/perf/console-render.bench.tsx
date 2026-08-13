/**
 * SC-03 방송 콘솔 리렌더 측정 하네스.
 *
 * 콘솔은 방송 내내(보통 3~6시간) 세컨드 모니터에 떠 있고, `useLiveSnapshot`이 3초마다
 * 스냅샷을 다시 받는다. 응답은 매번 새로 파싱한 객체라 값이 그대로여도 참조가 달라지고,
 * 그 결과 화면 전체가 3초마다 다시 렌더된다. 이 하네스는 그게 실제로 몇 번인지 센다.
 *
 * 재는 것:
 *   - 폴링 tick당 각 패널이 실제로 렌더된 횟수
 *   - 페이지 루트 commit 수와 React Profiler의 actualDuration 합
 *
 * 시나리오 셋을 나눠 재는 이유: 실제 방송에서는 `session.elapsedSeconds`·`currentViewers`가
 * 매 tick 바뀐다. 스냅샷 전체를 한 덩어리로 비교하는 최적화는 이 경우 아무 효과가 없으므로,
 * "완전 동일"과 "세션 필드만 변함"을 반드시 구분해서 봐야 한다.
 *
 * 실행: pnpm perf:console
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createElement, Profiler, type ComponentType, type ReactElement } from 'react'
import { render, act, cleanup } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom'
import type { LiveSnapshot } from '@renderer/types/console'

// ─────────────────────────────────────────────────────────────────────────────
// 렌더 카운터
// ─────────────────────────────────────────────────────────────────────────────

const renderCounts: Record<string, number> = {}

/**
 * 컴포넌트가 "실제로" 렌더된 횟수를 센다.
 *
 * 단순히 겉을 감싸면 안 된다 — 대상이 memo로 감싸져 있을 때 래퍼는 부모를 따라 매번
 * 렌더되고 안쪽 memo만 bail out하므로, 최적화를 넣어도 카운터가 그대로 올라간다.
 * 그래서 memo면 안쪽 구현을 꺼내 카운터를 그 안에 넣고 같은 compare로 다시 감싼다.
 */
const MEMO = Symbol.for('react.memo')

function track(name: string, exported: unknown): unknown {
  const asMemo = exported as { $$typeof?: symbol; type?: ComponentType; compare?: unknown }
  const isMemo = asMemo?.$$typeof === MEMO
  const inner = (isMemo ? asMemo.type : exported) as ComponentType<Record<string, unknown>>

  const Counted = (props: Record<string, unknown>): ReactElement => {
    renderCounts[name] = (renderCounts[name] ?? 0) + 1
    return createElement(inner, props)
  }
  Counted.displayName = `tracked(${name})`

  if (!isMemo) return Counted
  // React.memo를 다시 부르는 대신 원본과 같은 모양의 memo 객체를 만든다 —
  // compare 함수까지 그대로 가져와야 최적화의 실제 동작을 그대로 재현한다.
  return { $$typeof: MEMO, type: Counted, compare: asMemo.compare ?? null }
}

// vi.mock은 호이스팅되므로 경로를 변수로 넘길 수 없다. 8개를 그대로 편다.
vi.mock('@renderer/pages/console/components/ConsoleStatusBar', async (orig) => {
  const actual = (await orig()) as Record<string, unknown>
  return { ...actual, default: track('ConsoleStatusBar', actual.default) }
})
vi.mock('@renderer/pages/console/components/BriefingFeed', async (orig) => {
  const actual = (await orig()) as Record<string, unknown>
  return { ...actual, default: track('BriefingFeed', actual.default) }
})
vi.mock('@renderer/pages/console/components/SentimentPanel', async (orig) => {
  const actual = (await orig()) as Record<string, unknown>
  return { ...actual, default: track('SentimentPanel', actual.default) }
})
vi.mock('@renderer/pages/console/components/HighlightPanel', async (orig) => {
  const actual = (await orig()) as Record<string, unknown>
  return { ...actual, default: track('HighlightPanel', actual.default) }
})
vi.mock('@renderer/pages/console/components/TopViewerPanel', async (orig) => {
  const actual = (await orig()) as Record<string, unknown>
  return { ...actual, default: track('TopViewerPanel', actual.default) }
})
vi.mock('@renderer/pages/console/components/RepeatedQuestions', async (orig) => {
  const actual = (await orig()) as Record<string, unknown>
  return { ...actual, default: track('RepeatedQuestions', actual.default) }
})
vi.mock('@renderer/pages/console/components/ChatSummaryButton', async (orig) => {
  const actual = (await orig()) as Record<string, unknown>
  return { ...actual, default: track('ChatSummaryButton', actual.default) }
})
vi.mock('@renderer/pages/console/components/ChzzkTitleSection', async (orig) => {
  const actual = (await orig()) as Record<string, unknown>
  return { ...actual, default: track('ChzzkTitleSection', actual.default) }
})

const PANELS = [
  'ConsoleStatusBar',
  'BriefingFeed',
  'ChzzkTitleSection',
  'SentimentPanel',
  'HighlightPanel',
  'ChatSummaryButton',
  'RepeatedQuestions',
  'TopViewerPanel'
]

// ─────────────────────────────────────────────────────────────────────────────
// 외부 의존 대체
//
// 음성 스트림(WS·AudioWorklet)과 OBS(IPC)는 폴링과 무관한 렌더 요인이라 고정값으로
// 묶는다. 이 하네스가 재려는 것은 3초 스냅샷 폴링이 만드는 렌더뿐이다.
// ─────────────────────────────────────────────────────────────────────────────

const OBS_STUB = Object.freeze({
  status: 'CONNECTED',
  streaming: true,
  recording: true,
  connect: async () => OBS_STUB,
  disconnect: async () => {},
  startStream: async () => {},
  stopStream: async () => {},
  startRecord: async () => {},
  stopRecord: async () => ({ outputPath: '' }),
  getRecordStatus: async () => ({ outputActive: true, outputDuration: 1000 })
})

/*
 * useAudioStream은 모킹하지 않고 실제 훅을 쓴다. 이 훅이 반환 객체의 참조를 유지하는지가
 * 상태줄 리렌더를 좌우하는데, 모킹해 버리면 정작 그 부분을 재지 못한다 — 처음에 고정
 * 객체로 스텁했다가 실제보다 유리한 수치가 나온 적이 있다.
 * WebSocket·AudioContext는 start()를 부를 때만 만들어지고 이 하네스는 부르지 않는다.
 */
vi.mock('@renderer/hooks/useObs', () => ({ useObs: () => OBS_STUB }))
vi.mock('@renderer/hooks/useRecordingReporter', () => ({ useRecordingReporter: () => {} }))

vi.mock('@renderer/api/promise', () => ({
  listPromises: async () => ({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  confirmPromise: async () => {},
  dismissPromise: async () => {}
}))
vi.mock('@renderer/api/liveSetting', () => ({
  fetchLiveSetting: async () => ({ title: '오늘도 달립니다', category: null }),
  updateLiveSetting: async () => {},
  isRelinkRequired: () => false
}))
vi.mock('@renderer/api/chatSummary', () => ({ createChatSummary: async () => null }))
vi.mock('@renderer/api/repeatedQuestions', () => ({ dismissRepeatedQuestion: async () => {} }))

// 스냅샷은 시나리오가 갈아끼운다.
let nextSnapshot: () => LiveSnapshot
vi.mock('@renderer/api/console', () => ({
  getLiveSnapshot: async () => nextSnapshot()
}))

// ─────────────────────────────────────────────────────────────────────────────
// 스냅샷 생성
// ─────────────────────────────────────────────────────────────────────────────

/** 실제 응답과 같이 매번 새로 파싱된 객체를 준다 — 참조 동일성이 생기지 않게. */
function freshSnapshot(tick: number, mutate: 'none' | 'session' | 'sentiment'): LiveSnapshot {
  const elapsed = mutate === 'none' ? 3600 : 3600 + tick * 3
  const viewers = mutate === 'none' ? 1240 : 1240 + (tick % 7)
  const joy = mutate === 'sentiment' ? 0.4 + (tick % 5) * 0.01 : 0.42

  const snapshot: LiveSnapshot = {
    session: {
      sessionId: 1,
      status: 'LIVE',
      startedAt: '2026-08-13T08:00:00.000Z',
      elapsedSeconds: elapsed,
      currentViewers: viewers
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
      distribution: { JOY: joy, EXCITEMENT: 0.2, SUPPORT: 0.15, NEUTRAL: 0.23 }
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
    lastEventId: 'evt-1'
  }
  // 실제 fetch가 하는 것과 같은 깊은 복사 — 리터럴이 남긴 참조를 끊는다.
  return JSON.parse(JSON.stringify(snapshot)) as LiveSnapshot
}

// ─────────────────────────────────────────────────────────────────────────────

const TICKS = 20

interface Result {
  scenario: string
  commits: number
  actualDurationMs: number
  panels: Record<string, number>
}

const results: Result[] = []

beforeEach(() => {
  for (const key of Object.keys(renderCounts)) delete renderCounts[key]
  vi.useFakeTimers()
  Object.defineProperty(window, 'api', {
    configurable: true,
    value: {
      openMiniWindow: () => {},
      closeMiniWindow: () => {},
      focusMainWindow: () => {},
      snapshot: { publish: () => {}, getState: async () => null, onState: () => () => {} },
      briefing: {
        publish: () => {},
        recent: async () => [],
        clear: () => {},
        onBriefing: () => () => {}
      },
      question: { requestDismiss: () => {}, onDismissRequest: () => () => {} },
      session: { publish: () => {}, getState: async () => null, onState: () => () => {} },
      obs: {
        getState: async () => ({ status: 'CONNECTED', streaming: true, recording: true }),
        onState: () => () => {},
        getCredentials: async () => null,
        getRecordStatus: async () => ({ outputActive: true, outputDuration: 1000 })
      }
    }
  })
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

async function measure(
  scenario: string,
  mutate: 'none' | 'session' | 'sentiment'
): Promise<Result> {
  let tick = 0
  nextSnapshot = () => freshSnapshot(tick, mutate)

  const { default: BroadcastConsolePage } =
    await import('@renderer/pages/console/BroadcastConsolePage')

  let commits = 0
  let actualDuration = 0

  const context = { session: null, loading: false }

  await act(async () => {
    render(
      <Profiler
        id="console"
        onRender={(_id, _phase, actual) => {
          commits += 1
          actualDuration += actual
        }}
      >
        <MemoryRouter initialEntries={['/console/1']}>
          <Routes>
            <Route element={<Outlet context={context} />}>
              <Route path="/console/:sessionId" element={<BroadcastConsolePage />} />
            </Route>
          </Routes>
        </MemoryRouter>
      </Profiler>
    )
  })

  // 첫 조회가 끝나 화면이 실제로 그려질 때까지 흘린다.
  await act(async () => {
    await vi.advanceTimersByTimeAsync(50)
  })

  // 마운트 비용은 빼고 폴링 tick만 센다.
  for (const key of Object.keys(renderCounts)) delete renderCounts[key]
  commits = 0
  actualDuration = 0

  for (let i = 0; i < TICKS; i++) {
    tick = i + 1
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000)
    })
  }

  return {
    scenario,
    commits,
    actualDurationMs: actualDuration,
    panels: Object.fromEntries(PANELS.map((p) => [p, renderCounts[p] ?? 0]))
  }
}

/*
 * 측정만 하지 않고 단언까지 건다. 이 화면은 memo와 참조 유지가 맞물려야 효과가 나는데,
 * 어느 한쪽만 무심코 풀려도(핸들러를 인라인으로 되돌리거나 `?? []`를 다시 쓰거나)
 * 화면은 멀쩡해 보이고 렌더 수만 조용히 8배로 돌아간다. 그걸 여기서 잡는다.
 */
describe(`방송 콘솔 3초 폴링 — ${TICKS} tick 동안의 리렌더`, () => {
  it('스냅샷이 완전히 동일하면 어떤 패널도 다시 렌더되지 않는다', async () => {
    const result = await measure('동일 payload', 'none')
    results.push(result)
    for (const panel of PANELS) expect(`${panel}=${result.panels[panel]}`).toBe(`${panel}=0`)
  })

  it('세션 필드(경과·시청자)만 바뀌면 상태줄만 렌더된다 — 실제 방송의 기본형', async () => {
    const result = await measure('세션 필드만 변경', 'session')
    results.push(result)
    expect(result.panels.ConsoleStatusBar).toBeLessThanOrEqual(TICKS)
    expect(result.panels.ConsoleStatusBar).toBeGreaterThan(0)
    for (const panel of PANELS.filter((p) => p !== 'ConsoleStatusBar')) {
      expect(`${panel}=${result.panels[panel]}`).toBe(`${panel}=0`)
    }
  })

  it('여론이 바뀌면 상태줄과 여론 패널만 렌더된다', async () => {
    const result = await measure('여론까지 변경', 'sentiment')
    results.push(result)
    expect(result.panels.SentimentPanel).toBeGreaterThan(0)
    const untouched = PANELS.filter((p) => p !== 'ConsoleStatusBar' && p !== 'SentimentPanel')
    for (const panel of untouched) expect(`${panel}=${result.panels[panel]}`).toBe(`${panel}=0`)
  })

  it('결과 출력', () => {
    const pad = (s: string | number, n: number): string => String(s).padStart(n)
    console.log(`\n=== 방송 콘솔 리렌더 (${TICKS} tick) ===`)
    const header = ['시나리오'.padEnd(18), pad('commit', 7), pad('render(ms)', 11)]
      .concat(PANELS.map((p) => pad(p.slice(0, 9), 10)))
      .join('')
    console.log(header)
    for (const r of results) {
      console.log(
        [r.scenario.padEnd(18), pad(r.commits, 7), pad(r.actualDurationMs.toFixed(1), 11)]
          .concat(PANELS.map((p) => pad(r.panels[p], 10)))
          .join('')
      )
    }
    const total = results.map((r) => Object.values(r.panels).reduce((a, b) => a + b, 0))
    console.log(`패널 렌더 합계: ${results.map((r, i) => `${r.scenario}=${total[i]}`).join(' · ')}`)
    expect(results).toHaveLength(3)
  })
})
