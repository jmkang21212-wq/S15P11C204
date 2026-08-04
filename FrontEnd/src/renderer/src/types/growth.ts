/**
 * SC-09 성장 리포트 도메인 타입.
 *
 * 출처: `weekly/2026_0720-0724/설계자료/API 명세서.md`
 *   §11.2 `GET /growth-reports?periodType&from&to`
 *   §11.3 `GET /growth-goals?status=ACTIVE`
 */

export type PeriodType = 'WEEKLY' | 'MONTHLY'

export type MetricType = 'AVG_VIEWERS' | 'BROADCASTS_PER_WEEK' | 'TOTAL_CHAT' | 'BROADCAST_MINUTES'

/**
 * BE `GrowthMetricUnit`. `metricType`이 단위를 결정하므로 둘이 어긋난 값은 서버가 거부한다
 * (AVG_VIEWERS↔VIEWERS, BROADCASTS_PER_WEEK↔COUNT, TOTAL_CHAT↔MESSAGES, BROADCAST_MINUTES↔MINUTES).
 */
export type MetricUnit = 'VIEWERS' | 'COUNT' | 'MESSAGES' | 'MINUTES'

export type GoalStatus = 'ACTIVE' | 'ACHIEVED' | 'CANCELLED'

/**
 * PATCH /growth-goals/{id} 입력.
 *
 * 캘린더·약속의 `PatchValue`와 달리 여기서는 **보내지 않은 필드가 곧 "유지"** 다
 * (`GrowthGoal.update`가 null을 현재 값으로 대체한다). 그래서 상태만 바꿀 때는 status 하나만
 * 담으면 되고, 전부 생략하면 400이다.
 *
 * `CANCELLED`에서는 어떤 상태로도 나갈 수 없다 — 중단은 되돌릴 수 없는 종점이다.
 */
export interface UpdateGrowthGoalInput {
  metricType?: MetricType
  targetValue?: number
  unit?: MetricUnit
  periodStart?: string
  periodEnd?: string
  status?: GoalStatus
}

/**
 * 시계열에 실제로 담기는 지표.
 * `BROADCASTS_PER_WEEK`는 목표에만 쓰이고 `series`에는 없어 제외한다.
 */
export type SeriesMetric = 'avgViewers' | 'totalChat' | 'broadcastMinutes'

export interface GrowthSummaryMetrics {
  avgViewers: number
  /** 직전 구간 대비 증감률. 0.18 = +18% */
  avgViewersChangeRate: number
  totalChat: number
  totalChatChangeRate: number
  broadcastMinutes: number
  broadcastMinutesChangeRate: number
}

export interface GrowthSeriesPoint {
  periodStart: string
  periodEnd: string
  avgViewers: number
  totalChat: number
  broadcastMinutes: number
}

export interface GrowthReport {
  periodType: PeriodType
  from: string
  to: string
  summaryMetrics: GrowthSummaryMetrics
  series: GrowthSeriesPoint[]
}

export interface GrowthAction {
  growthActionId: number
  title: string
  content: string
}

export type GrowthActionGenerationStatus =
  'NOT_REQUESTED' | 'WAITING_FOR_DATA' | 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface GrowthActionGeneration {
  status: GrowthActionGenerationStatus
  stale: boolean
  generatedAt: string | null
  sourceReportId: number | null
  promptVersion: string | null
  errorCode: string | null
}

export interface GrowthActionGenerationRequest {
  generationId: number
  status: GrowthActionGenerationStatus
  requestedAt: string
}

export interface GrowthGoal {
  growthGoalId: number
  metricType: MetricType
  targetValue: number
  unit: string
  periodStart: string
  periodEnd: string
  status: GoalStatus
  /** REPORTS·BROADCAST_SESSIONS 집계로 계산되며 DB 컬럼이 아니다 (명세서 §11.3) */
  currentValue: number
  /** 0~1. 1을 넘을 수 있다(초과 달성) */
  progressRate: number
  /** GROWTH_ACTIONS에서 연결된 AI 다음 액션. 없을 수 있다. */
  actions: GrowthAction[]
  actionGeneration: GrowthActionGeneration
}
