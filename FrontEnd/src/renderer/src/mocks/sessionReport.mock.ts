import type {
  ReportHighlight,
  ReportInsight,
  ReportSegment,
  ReportSession,
  SessionReport,
  SessionReportProcessing,
  ViewerTimelinePoint
} from '@renderer/types/report'
import type { BroadcastSessionListItem } from '@renderer/types/broadcastSession'

/**
 * SC-06 세션 리포트 목 데이터.
 *
 * 백엔드 `GET /broadcast-sessions/{sessionId}/report`가 미구현이라 화면 개발용으로 둔다.
 * 값과 구조는 API 명세서 §11.1 예시를 기준으로 했다.
 * 실연동 후에는 `api/sessionReport.ts`의 `USE_MOCK`만 끄면 되고 이 파일은 그대로 남겨 둔다.
 */

const MINUTE_MS = 60_000

/** 방송 시작 기준 분 단위 앵커 (분, 시청자 수). 사이는 선형 보간한다. */
const VIEWER_ANCHORS: ReadonlyArray<readonly [number, number]> = [
  [0, 1820],
  [20, 1950],
  [46, 1880],
  [70, 2400],
  [90, 3842], // 최고 동시 시청자
  [100, 2600],
  [120, 2300],
  [150, 2250],
  [165, 1350], // 이탈 구간 종료
  [185, 1250],
  [208, 1180]
]

/** 렌더마다 값이 흔들리지 않도록 난수 대신 결정적 노이즈를 쓴다. */
function noiseAt(minute: number): number {
  return Math.sin(minute * 1.7) * 45 + Math.sin(minute * 0.43) * 70
}

function interpolate(minute: number): number {
  const last = VIEWER_ANCHORS[VIEWER_ANCHORS.length - 1]
  if (minute >= last[0]) return last[1]

  for (let i = 0; i < VIEWER_ANCHORS.length - 1; i += 1) {
    const [m0, v0] = VIEWER_ANCHORS[i]
    const [m1, v1] = VIEWER_ANCHORS[i + 1]
    if (minute >= m0 && minute <= m1) {
      const t = (minute - m0) / (m1 - m0)
      return v0 + (v1 - v0) * t
    }
  }
  return VIEWER_ANCHORS[0][1]
}

/**
 * 1분 간격 시청자 추이를 만든다.
 * 앵커가 세그먼트(유입 46~96분, 채팅 피크 87분, 이탈 150~165분)와 맞물리도록 잡혀 있어
 * 차트 위 마커가 실제 변곡점에 놓인다.
 */
function buildViewerTimeline(
  startedAt: string,
  durationMin: number,
  scale = 1
): ViewerTimelinePoint[] {
  const start = new Date(startedAt).getTime()
  const points: ViewerTimelinePoint[] = []

  for (let minute = 0; minute <= durationMin; minute += 1) {
    const value = (interpolate(minute) + noiseAt(minute)) * scale
    points.push({
      measuredAt: new Date(start + minute * MINUTE_MS).toISOString(),
      viewerCount: Math.max(0, Math.round(value))
    })
  }
  return points
}

const SEGMENTS: ReportSegment[] = [
  {
    reportSegmentId: 401,
    startMs: 46 * MINUTE_MS,
    endMs: 96 * MINUTE_MS,
    segmentType: 'CHAT_SURGE'
  },
  {
    reportSegmentId: 402,
    startMs: 87 * MINUTE_MS,
    endMs: 91 * MINUTE_MS,
    segmentType: 'CHAT_SURGE'
  },
  {
    reportSegmentId: 403,
    startMs: 150 * MINUTE_MS,
    endMs: 165 * MINUTE_MS,
    segmentType: 'CHAT_SURGE'
  },
  {
    reportSegmentId: 404,
    startMs: 120 * MINUTE_MS,
    endMs: 122 * MINUTE_MS,
    segmentType: 'CHAT_SURGE'
  }
]

const INSIGHTS: ReportInsight[] = [
  {
    insightId: 501,
    title: '자정 게임 전환을 미리 예고하기',
    content:
      '2시간 30분 지점에서 별도 안내 없이 롤토체스에서 수다로 전환하면서 15분간 시청자가 40% 빠졌습니다. 전환 10분 전에 채팅으로 예고하면 이탈을 줄일 수 있습니다.'
  },
  {
    insightId: 502,
    title: '초반 40분의 밀도를 높이기',
    content:
      '방송 시작 후 46분까지 시청자 수가 거의 늘지 않았습니다. 오프닝 직후에 오늘의 목표나 이벤트를 먼저 배치해 보세요.'
  },
  {
    insightId: 503,
    title: '채팅 피크 구간을 클립으로 활용하기',
    content:
      '1시간 27분 지점의 역전 장면에서 분당 채팅이 평소의 6배로 치솟았습니다. 이 구간을 숏폼으로 편집하면 유입 효율이 높습니다.'
  }
]

const HIGHLIGHTS: ReportHighlight[] = [
  {
    highlightId: 71,
    triggerId: 402,
    startMs: 87 * MINUTE_MS,
    endMs: 87 * MINUTE_MS + 41_000,
    clipUrl: null,
    thumbnailUrl: null,
    clipStatus: 'PENDING'
  },
  {
    highlightId: 72,
    triggerId: 402,
    startMs: 89 * MINUTE_MS,
    endMs: 89 * MINUTE_MS + 55_000,
    clipUrl: null,
    thumbnailUrl: null,
    clipStatus: 'PENDING'
  },
  {
    highlightId: 73,
    triggerId: 404,
    startMs: 120 * MINUTE_MS,
    endMs: 120 * MINUTE_MS + 68_000,
    clipUrl: null,
    thumbnailUrl: null,
    clipStatus: 'PENDING'
  },
  {
    highlightId: 74,
    triggerId: 401,
    startMs: 62 * MINUTE_MS,
    endMs: 62 * MINUTE_MS + 52_000,
    clipUrl: null,
    thumbnailUrl: null,
    clipStatus: 'PENDING'
  }
]

const SESSIONS: Record<number, ReportSession> = {
  9001: {
    sessionId: 9001,
    title: '심야 수다방 & 롤토체스',
    startedAt: '2026-07-20T13:04:00Z',
    endedAt: '2026-07-20T16:32:00Z'
  },
  9002: {
    sessionId: 9002,
    title: '신작 로그라이크 첫 플레이',
    startedAt: '2026-07-24T10:00:00Z',
    endedAt: '2026-07-24T13:12:00Z'
  },
  9003: {
    sessionId: 9003,
    title: '구독자 100명 기념 Q&A',
    startedAt: '2026-07-22T11:30:00Z',
    endedAt: '2026-07-22T13:38:00Z'
  },
  9004: {
    sessionId: 9004,
    title: '즉흥 노래방 스트리밍',
    startedAt: '2026-07-19T12:10:00Z',
    endedAt: '2026-07-19T14:02:00Z'
  },
  9005: {
    sessionId: 9005,
    title: '주말 아침 저챗 + 뉴스 읽기',
    startedAt: '2026-07-18T23:05:00Z',
    endedAt: '2026-07-19T01:40:00Z'
  },
  9006: {
    sessionId: 9006,
    title: '시청자 참여 마리오카트 대회',
    startedAt: '2026-07-16T09:00:00Z',
    endedAt: '2026-07-16T12:26:00Z'
  }
}

/** 기준 리포트(9001) — 명세서 §11.1 예시 값 */
const BASE_REPORT: SessionReport = {
  reportId: 301,
  status: 'COMPLETED',
  session: SESSIONS[9001],
  summary:
    '롤토체스 구간에서 시청자 유입이 집중되었습니다. 1시간 27분 지점의 역전 장면에서 채팅이 가장 활발했고, 2시간 30분 이후 게임 전환 과정에서 이탈이 발생했습니다. 전반적으로 긍정 반응이 우세했습니다.',
  metrics: {
    avgViewers: 2105,
    peakViewers: 3842,
    totalChat: 48_200,
    positiveRatio: 0.64,
    neutralRatio: 0.27,
    negativeRatio: 0.09
  },
  viewerTimeline: buildViewerTimeline(SESSIONS[9001].startedAt, 208),
  segments: SEGMENTS,
  insights: INSIGHTS,
  highlights: HIGHLIGHTS,
  generatedAt: '2026-07-20T16:35:00Z'
}

/** 분석 진행 중(202 Accepted) 응답 */
const PROCESSING_REPORT: SessionReportProcessing = {
  reportId: 302,
  status: 'PROCESSING',
  session: SESSIONS[9002],
  progress: 0.42
}

/**
 * AI 요약 실패 — 집계 지표만 있는 PARTIAL 응답.
 * 요약·개선 제안·하이라이트가 모두 비어 빈 상태 UI를 함께 확인할 수 있다.
 */
const PARTIAL_REPORT: SessionReport = {
  reportId: 303,
  status: 'PARTIAL',
  session: SESSIONS[9003],
  summary: null,
  metrics: {
    avgViewers: 1480,
    peakViewers: 2260,
    totalChat: 21_400,
    positiveRatio: 0.51,
    neutralRatio: 0.34,
    negativeRatio: 0.15
  },
  viewerTimeline: buildViewerTimeline(SESSIONS[9003].startedAt, 128, 0.7),
  segments: [],
  insights: [],
  highlights: [],
  generatedAt: '2026-07-22T13:41:00Z'
}

/** 분석 자체가 실패한 리포트 */
const FAILED_REPORT: SessionReport = {
  reportId: 304,
  status: 'FAILED',
  session: SESSIONS[9004],
  summary: null,
  metrics: {
    avgViewers: 0,
    peakViewers: 0,
    totalChat: 0,
    positiveRatio: 0,
    neutralRatio: 0,
    negativeRatio: 0
  },
  viewerTimeline: [],
  segments: [],
  insights: [],
  highlights: [],
  generatedAt: null
}

/** 기준 리포트를 다른 세션에 맞춰 변형한다. 목록에서 여러 카드를 눌러 볼 수 있게 하려는 용도. */
function deriveReport(
  reportId: number,
  session: ReportSession,
  scale: number,
  summary: string
): SessionReport {
  const durationMin = Math.round(
    (new Date(session.endedAt).getTime() - new Date(session.startedAt).getTime()) / MINUTE_MS
  )
  return {
    ...BASE_REPORT,
    reportId,
    session,
    summary,
    metrics: {
      avgViewers: Math.round(BASE_REPORT.metrics.avgViewers * scale),
      peakViewers: Math.round(BASE_REPORT.metrics.peakViewers * scale),
      totalChat: Math.round(BASE_REPORT.metrics.totalChat * scale),
      positiveRatio: 0.58,
      neutralRatio: 0.31,
      negativeRatio: 0.11
    },
    viewerTimeline: buildViewerTimeline(session.startedAt, durationMin, scale),
    highlights: HIGHLIGHTS.slice(0, 2),
    generatedAt: session.endedAt
  }
}

/** sessionId → 리포트. `api/sessionReport.ts`에서만 참조한다. */
export const MOCK_REPORTS: Record<number, SessionReport | SessionReportProcessing> = {
  9001: BASE_REPORT,
  9002: PROCESSING_REPORT,
  9003: PARTIAL_REPORT,
  9004: FAILED_REPORT,
  9005: deriveReport(
    305,
    SESSIONS[9005],
    0.82,
    '뉴스 읽기 코너에서 채팅 참여가 꾸준히 유지되었습니다. 후반부 잡담 구간의 몰입도가 다소 낮았습니다.'
  ),
  9006: deriveReport(
    306,
    SESSIONS[9006],
    1.24,
    '시청자 참여 대회 구간에서 최고 동시 시청자를 기록했습니다. 대기열 안내가 명확해 이탈이 적었습니다.'
  )
}

/** 종료된 세션 목록 — 명세서 §10.1 `GET /broadcast-sessions?status=ENDED` 대응 */
export const MOCK_ENDED_SESSIONS: BroadcastSessionListItem[] = [
  {
    ...SESSIONS[9002],
    category: '로그라이크',
    thumbnailUrl: null,
    status: 'ENDED',
    reportStatus: 'PROCESSING'
  },
  {
    ...SESSIONS[9003],
    category: '저챗',
    thumbnailUrl: null,
    status: 'ENDED',
    reportStatus: 'PARTIAL'
  },
  {
    ...SESSIONS[9001],
    category: '롤토체스',
    thumbnailUrl: null,
    status: 'ENDED',
    reportStatus: 'COMPLETED'
  },
  {
    ...SESSIONS[9004],
    category: '노래방',
    thumbnailUrl: null,
    status: 'ENDED',
    reportStatus: 'FAILED'
  },
  {
    ...SESSIONS[9005],
    category: '저챗',
    thumbnailUrl: null,
    status: 'ENDED',
    reportStatus: 'COMPLETED'
  },
  {
    ...SESSIONS[9006],
    category: '마리오카트',
    thumbnailUrl: null,
    status: 'ENDED',
    reportStatus: 'COMPLETED'
  }
]
