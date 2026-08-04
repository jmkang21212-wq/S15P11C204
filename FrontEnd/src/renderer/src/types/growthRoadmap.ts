/**
 * SC-09 목표 로드맵 타입 — `GrowthRoadmapController` (`/api/v1/growth-roadmap`).
 *
 * AI가 리포트·포트폴리오를 근거로 추천 목표를 만들고, 사용자가 채택하면 `GROWTH_GOALS`
 * 행이 생겨 기존 목표 카드로 넘어간다. 그래서 이 화면의 목표 로드맵은 두 갈래를 함께 그린다 —
 * 여기 정의된 추천(`GrowthGoalSuggestion`)과 `types/growth.ts`의 `GrowthGoal`.
 */

import type { MetricType, MetricUnit } from './growth'

/**
 * 로드맵 생성 진행 상태.
 * 데이터가 없으면 404가 아니라 `NOT_REQUESTED` + 빈 배열로 정상 응답이 온다.
 */
export type RoadmapStatus =
  'NOT_REQUESTED' | 'WAITING_FOR_DATA' | 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'

/** 근거로 쓴 리포트가 1건이면 INITIAL, 3건 이상 쌓이면 ESTABLISHED로 재생성된다. */
export type RoadmapQuality = 'INITIAL' | 'ESTABLISHED'

export type SuggestionStatus = 'SUGGESTED' | 'ACCEPTED' | 'DISMISSED' | 'SUPERSEDED'

export interface SuggestionAction {
  suggestionActionId: number
  displayOrder: number
  title: string
  content: string
}

export interface GrowthGoalSuggestion {
  suggestionId: number
  displayOrder: number
  metricType: MetricType
  targetValue: number
  unit: MetricUnit
  /** 4~12주(28~84일) 범위로 서버가 제한한다. */
  periodStart: string
  periodEnd: string
  title: string
  /** 추천 근거. "왜 이 목표인가"를 사용자에게 보여주는 값이다. */
  rationale: string
  status: SuggestionStatus
  /** 채택했을 때 만들어진 목표 ID. 채택 전에는 null. */
  acceptedGoalId: number | null
  actions: SuggestionAction[]
}

export interface GrowthRoadmap {
  roadmapId: number | null
  status: RoadmapStatus
  quality: RoadmapQuality | null
  /** 새 리포트가 쌓여 갱신할 여지가 있다는 뜻. 지금 추천이 못 쓰는 값이라는 뜻은 아니다. */
  stale: boolean
  promptVersion: string | null
  generatedAt: string | null
  sourceReportId: number | null
  /** `FAILED`일 때의 사유. */
  errorCode: string | null
  /** 서버가 정확히 3건을 만들고 지표가 서로 겹치지 않는다. 생성 전에는 빈 배열. */
  suggestions: GrowthGoalSuggestion[]
}

export type RoadmapGenerationStatus =
  'WAITING_FOR_DATA' | 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'SUPERSEDED'

/** POST /growth-roadmap/regenerate 응답 (202). 실제 생성은 비동기로 진행된다. */
export interface RoadmapGenerationRequest {
  generationId: number
  status: RoadmapGenerationStatus
  requestedAt: string
}
