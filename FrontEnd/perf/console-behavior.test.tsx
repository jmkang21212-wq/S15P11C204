/**
 * 방송 콘솔 갱신 동작 검증.
 *
 * 리렌더 최적화(참조 유지 + memo)는 잘못 걸면 화면이 조용히 갱신되지 않는 버그가 된다.
 * 렌더 횟수만 세는 하네스(console-render.bench.tsx)로는 그걸 잡지 못하므로, 여기서는
 * "3초 폴링으로 값이 바뀌면 화면 텍스트가 실제로 바뀌는가"를 DOM으로 확인한다.
 *
 * 실행: pnpm perf:console
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, act, cleanup, screen } from '@testing-library/react'
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom'
import type { LiveSnapshot } from '@renderer/types/console'

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

let current: LiveSnapshot
vi.mock('@renderer/api/console', () => ({
  // 실제 fetch처럼 매번 새로 파싱된 객체를 준다 — 참조 동일성이 우연히 생기지 않게.
  getLiveSnapshot: async () => JSON.parse(JSON.stringify(current)) as LiveSnapshot
}))

function baseSnapshot(): LiveSnapshot {
  return {
    session: {
      sessionId: 1,
      status: 'LIVE',
      startedAt: '2026-08-13T08:00:00.000Z',
      elapsedSeconds: 3600,
      currentViewers: 1240
    },
    services: { microphone: 'CONNECTED', stt: 'RUNNING', ai: 'RUNNING', collector: 'RUNNING' },
    briefings: [
      {
        briefingId: 1,
        briefingType: 'RISK',
        message: '첫 번째 브리핑',
        createdAt: '2026-08-13T08:30:00.000Z'
      }
    ],
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
    repeatedQuestions: [
      {
        questionId: 1,
        text: '처음 질문',
        type: 'FAQ',
        askers: 12,
        messages: 20,
        reasked: false,
        firstAskedAt: '2026-08-13T08:10:00.000Z',
        lastAskedAt: '2026-08-13T08:50:00.000Z'
      }
    ],
    topViewers: [
      {
        viewerId: 1,
        nickname: '처음시청자',
        viewerAlias: 'viewer-a11c',
        chatScore: 30,
        watchScore: 40,
        totalScore: 100
      }
    ],
    lastEventId: 'evt-1'
  }
}

beforeEach(() => {
  current = baseSnapshot()
  vi.useFakeTimers()
  /*
   * 시계를 고정한다. 콘솔은 마운트 시각을 브리핑 병합 컷오버로 쓰므로(lib/briefings.ts),
   * 실제 시각으로 두면 픽스처의 createdAt이 컷오버보다 미래가 되어 브리핑이 정상적으로
   * 걸러진다 — 앱은 맞게 동작하는데 테스트만 실패한다.
   */
  vi.setSystemTime(new Date('2026-08-13T09:00:00.000Z'))
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

/** 폴링 한 tick을 흘린다. */
async function poll(): Promise<void> {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(3000)
  })
}

async function mountConsole(): Promise<void> {
  const { default: BroadcastConsolePage } =
    await import('@renderer/pages/console/BroadcastConsolePage')
  await act(async () => {
    render(
      <MemoryRouter initialEntries={['/console/1']}>
        <Routes>
          <Route element={<Outlet context={{ session: null, loading: false }} />}>
            <Route path="/console/:sessionId" element={<BroadcastConsolePage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    )
  })
  await act(async () => {
    await vi.advanceTimersByTimeAsync(50)
  })
}

describe('방송 콘솔 — 폴링으로 바뀐 값이 화면에 반영된다', () => {
  it('첫 조회 결과가 화면에 나온다', async () => {
    await mountConsole()
    expect(screen.getByText('1,240')).toBeDefined()
    expect(screen.getByText('처음 질문')).toBeDefined()
    expect(screen.getByText('처음시청자')).toBeDefined()
    expect(screen.getByText('첫 번째 브리핑')).toBeDefined()
  })

  it('시청자 수가 바뀌면 상태줄이 갱신된다', async () => {
    await mountConsole()
    current.session.currentViewers = 2580
    await poll()
    expect(screen.getByText('2,580')).toBeDefined()
    expect(screen.queryByText('1,240')).toBeNull()
  })

  it('열성 시청자가 바뀌면 목록이 갱신된다', async () => {
    await mountConsole()
    current.topViewers[0].nickname = '바뀐시청자'
    await poll()
    expect(screen.getByText('바뀐시청자')).toBeDefined()
    expect(screen.queryByText('처음시청자')).toBeNull()
  })

  it('반복 질문이 바뀌면 목록이 갱신된다', async () => {
    await mountConsole()
    current.repeatedQuestions[0].text = '바뀐 질문'
    await poll()
    expect(screen.getByText('바뀐 질문')).toBeDefined()
    expect(screen.queryByText('처음 질문')).toBeNull()
  })

  it('브리핑이 추가되면 피드에 나온다', async () => {
    await mountConsole()
    current.briefings.unshift({
      briefingId: 2,
      briefingType: 'RISK',
      message: '새로 온 브리핑',
      createdAt: '2026-08-13T08:55:00.000Z'
    })
    await poll()
    expect(screen.getByText('새로 온 브리핑')).toBeDefined()
    expect(screen.getByText('첫 번째 브리핑')).toBeDefined()
  })

  it('여러 tick 동안 값이 그대로면 화면도 그대로다', async () => {
    await mountConsole()
    /*
     * 경과 시간은 서버 값이 아니라 로컬 1초 티커라 계속 흐른다(ConsoleStatusBar).
     * 그건 의도된 동작이므로 비교에서 뺀다.
     */
    const withoutElapsed = (): string =>
      (document.body.textContent ?? '').replace(/\d+:\d{2}:\d{2}/g, 'ELAPSED')

    const before = withoutElapsed()
    for (let i = 0; i < 5; i++) await poll()
    expect(withoutElapsed()).toBe(before)
  })
})
