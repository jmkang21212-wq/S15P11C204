import { apiFetch, ApiError } from './client'
import { MOCK_REPORTS } from '@renderer/mocks/sessionReport.mock'
import type {
  ReportHighlight,
  ReportSegment,
  SegmentType,
  SessionReportResult
} from '@renderer/types/report'

/**
 * SC-06 세션 리포트 데이터 소스 — `SessionReportController`
 * (`GET /broadcast-sessions/{sessionId}/report`).
 *
 * 종료 세션 목록 조회는 SC-07 클립 화면도 쓰므로 `api/broadcastSessions.ts`로 분리했다.
 */
const USE_MOCK = false

/** 목 응답에도 지연을 줘 로딩 스켈레톤이 실제로 보이게 한다. */
const MOCK_DELAY_MS = 400

/** 조회한 세션의 리포트가 없을 때 던지는 에러 코드. */
export const REPORT_NOT_FOUND = 'REPORT_NOT_FOUND'

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export async function fetchSessionReport(sessionId: number): Promise<SessionReportResult> {
  if (USE_MOCK) {
    await delay(MOCK_DELAY_MS)
    const report = MOCK_REPORTS[sessionId]
    if (!report) throw new Error(REPORT_NOT_FOUND)
    return report
  }

  try {
    // PROCESSING이면 BE가 202로 내려주지만 apiFetch는 2xx를 전부 성공으로 취급해 data를
    // 그대로 반환한다 — status 필드(PROCESSING/COMPLETED/…) 자체로 분기하면 된다.
    const raw = await apiFetch<RawSessionReportResult>(`/broadcast-sessions/${sessionId}/report`)
    return normalize(raw)
  } catch (e) {
    if (e instanceof ApiError && e.code === 'REPORT_NOT_FOUND') {
      throw new Error(REPORT_NOT_FOUND)
    }
    // REPORT_FAILED(리포트 생성 자체가 실패)는 BE가 500으로 던진다 — 명세상 있는 FAILED
    // 상태 응답과 달리 정상 페이로드가 없어, 여기서는 일반 에러로 흘려보낸다.
    throw e
  }
}

// ─── BE 응답(wire) 타입 — SessionReportController와 1:1, 이 파일 밖에서는 쓰지 않는다 ───
interface RawReportSession {
  sessionId: number
  title: string
  startedAt: string
  endedAt: string
}
interface RawReportMetrics {
  avgViewers: number
  peakViewers: number
  totalChat: number
  analyzedChat: number
  dominant: string
  /** 감정 코드 → 비율(합 1, analyzedChat이 0이면 전부 0). */
  distribution: Record<string, number>
}
interface RawReportInsight {
  insightId: number
  title: string
  content: string
}
interface RawReportHighlight {
  highlightId: number
  triggerId: number | null
  startMs: number
  endMs: number
  clipUrl: string | null
  thumbnailUrl: string | null
  clipStatus: ReportHighlight['clipStatus']
}
interface RawViewerTimelinePoint {
  measuredAt: string
  viewerCount: number
}
interface RawReportTrigger {
  triggerId: number
  triggerType: string
  startMs: number
  endMs: number
}
interface RawSessionReportProcessing {
  reportId: number
  status: 'PROCESSING'
  session: RawReportSession
  progress: number
}
interface RawSessionReport {
  reportId: number
  status: 'COMPLETED' | 'PARTIAL' | 'FAILED'
  session: RawReportSession
  summary: string | null
  metrics: RawReportMetrics
  viewerTimeline: RawViewerTimelinePoint[]
  triggers: RawReportTrigger[]
  insights: RawReportInsight[]
  highlights: RawReportHighlight[]
  generatedAt: string | null
}
type RawSessionReportResult = RawSessionReportProcessing | RawSessionReport

function normalize(raw: RawSessionReportResult): SessionReportResult {
  if (raw.status === 'PROCESSING') {
    return {
      reportId: raw.reportId,
      status: 'PROCESSING',
      session: raw.session,
      progress: raw.progress
    }
  }
  return {
    reportId: raw.reportId,
    status: raw.status,
    session: raw.session,
    summary: raw.summary,
    metrics: {
      avgViewers: raw.metrics.avgViewers,
      peakViewers: raw.metrics.peakViewers,
      totalChat: raw.metrics.totalChat,
      ...bucketSentiment(raw.metrics.distribution, raw.metrics.analyzedChat)
    },
    viewerTimeline: raw.viewerTimeline,
    segments: raw.triggers.map(toSegment),
    insights: raw.insights,
    highlights: raw.highlights.map(toHighlight),
    generatedAt: raw.generatedAt
  }
}

function toSegment(trigger: RawReportTrigger): ReportSegment {
  return {
    reportSegmentId: trigger.triggerId,
    startMs: trigger.startMs,
    endMs: trigger.endMs,
    // BE(TriggerType)는 현재 CHAT_SURGE 하나뿐이다.
    segmentType: trigger.triggerType as SegmentType
  }
}

function toHighlight(highlight: RawReportHighlight): ReportHighlight {
  return {
    highlightId: highlight.highlightId,
    triggerId: highlight.triggerId,
    startMs: highlight.startMs,
    endMs: highlight.endMs,
    clipUrl: highlight.clipUrl,
    thumbnailUrl: highlight.thumbnailUrl,
    clipStatus: highlight.clipStatus
  }
}

/**
 * BE의 감정 8종 분포를 화면의 긍정/중립/부정 3분할로 묶는다 — `SentimentBreakdown`이
 * 이 3분할 형태를 계속 쓰기로 했다(콘솔의 8종 표시와는 다른 화면 결정).
 * SURPRISE(놀람)는 방향성이 불분명해 중립 쪽에 둔다.
 *
 * analyzedChat이 0이면 BE가 전 라벨 0인 분포를 주는데, 그대로 두면 중립 비율이 1로
 * 계산돼(1-0-0) `SentimentBreakdown`의 "데이터 없음" 판정(`total<=0`)을 피해가 버린다.
 * 데이터가 없다는 뜻을 그대로 전달하기 위해 셋 다 0으로 둔다.
 */
const POSITIVE_LABELS = ['JOY', 'EXCITEMENT', 'SUPPORT']
const NEGATIVE_LABELS = ['SADNESS', 'ANGER']

function bucketSentiment(
  distribution: Record<string, number>,
  analyzedChat: number
): {
  positiveRatio: number
  neutralRatio: number
  negativeRatio: number
} {
  if (analyzedChat === 0) {
    return { positiveRatio: 0, neutralRatio: 0, negativeRatio: 0 }
  }
  const sumOf = (labels: string[]): number =>
    labels.reduce((total, label) => total + (distribution[label] ?? 0), 0)
  const positiveRatio = sumOf(POSITIVE_LABELS)
  const negativeRatio = sumOf(NEGATIVE_LABELS)
  const neutralRatio = Math.max(0, 1 - positiveRatio - negativeRatio)
  return { positiveRatio, neutralRatio, negativeRatio }
}
