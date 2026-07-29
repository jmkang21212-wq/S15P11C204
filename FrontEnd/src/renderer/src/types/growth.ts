/**
 * SC-09 성장 리포트 도메인 타입.
 *
 * 출처: `weekly/2026_0720-0724/설계자료/API 명세서.md`
 *   §11.2 `GET /growth-reports?periodType&from&to`
 *   §11.3 `GET /growth-goals?status=ACTIVE`
 */

export type PeriodType = 'WEEKLY' | 'MONTHLY'

export type MetricType = 'AVG_VIEWERS' | 'BROADCASTS_PER_WEEK' | 'TOTAL_CHAT' | 'BROADCAST_MINUTES'

export type GoalStatus = 'ACTIVE' | 'ACHIEVED' | 'CANCELLED'

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
}
