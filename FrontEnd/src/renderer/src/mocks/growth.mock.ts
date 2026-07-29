import type {
  GrowthGoal,
  GrowthReport,
  GrowthSeriesPoint,
  GrowthSummaryMetrics,
  PeriodType
} from '@renderer/types/growth'

/**
 * SC-09 성장 리포트 목 데이터.
 *
 * 백엔드 `GET /growth-reports`·`GET /growth-goals`(명세서 §11.2·§11.3)가 미구현이라 화면 개발용으로 둔다.
 * 실연동 후에는 `api/growth.ts`의 `USE_MOCK`만 끄면 되고 이 파일은 그대로 남겨 둔다.
 */

/** 가장 최근 주간 구간 — 명세서 §11.2 예시와 같은 주에 맞췄다. */
const LAST_WEEK_START = '2026-07-14'
/** 가장 최근 월간 구간 */
const LAST_MONTH = { year: 2026, month: 7 }

/** 렌더마다 값이 흔들리지 않도록 난수 대신 결정적 노이즈를 쓴다. */
function noiseAt(index: number, scale: number): number {
  return (Math.sin(index * 1.9) * 0.6 + Math.sin(index * 0.7) * 0.4) * scale
}

function toISODate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

/** 최근 구간이 마지막에 오도록 오름차순 구간 목록을 만든다. */
function buildPeriods(periodType: PeriodType, count: number): { start: string; end: string }[] {
  const periods: { start: string; end: string }[] = []

  if (periodType === 'WEEKLY') {
    const lastStart = new Date(`${LAST_WEEK_START}T00:00:00Z`)
    for (let i = count - 1; i >= 0; i -= 1) {
      const start = new Date(lastStart)
      start.setUTCDate(start.getUTCDate() - i * 7)
      const end = new Date(start)
      end.setUTCDate(end.getUTCDate() + 6)
      periods.push({ start: toISODate(start), end: toISODate(end) })
    }
    return periods
  }

  for (let i = count - 1; i >= 0; i -= 1) {
    const start = new Date(Date.UTC(LAST_MONTH.year, LAST_MONTH.month - 1 - i, 1))
    // 다음 달 0일 = 이번 달 마지막 날
    const end = new Date(Date.UTC(start.getUTCFullYear(), start.getUTCMonth() + 1, 0))
    periods.push({ start: toISODate(start), end: toISODate(end) })
  }
  return periods
}

/**
 * 우상향 추세 + 중간 한 번 꺾이는 구간을 만든다.
 * 방송 시간은 마지막 구간을 일부러 떨어뜨려 KPI 화살표가 증가/감소 두 방향 모두 나오게 한다.
 */
function buildSeries(periodType: PeriodType, count: number): GrowthSeriesPoint[] {
  const periods = buildPeriods(periodType, count)
  const monthly = periodType === 'MONTHLY'
  // 월간은 한 구간이 대략 4~5주치라 규모를 키운다.
  const bulk = monthly ? 4.3 : 1

  return periods.map((period, i) => {
    // 전체 구간의 60~70% 지점에서 한 번 꺾인다.
    const slump = i === Math.floor(count * 0.6) || i === Math.floor(count * 0.6) + 1 ? -190 : 0
    const last = i === count - 1

    return {
      periodStart: period.start,
      periodEnd: period.end,
      avgViewers: Math.round(1450 + i * 62 + slump + noiseAt(i, 70)),
      totalChat: Math.round((30_000 + i * 1_620 + slump * 12 + noiseAt(i, 1_500)) * bulk),
      broadcastMinutes: Math.round((1_150 + i * 20 + noiseAt(i, 45) + (last ? -95 : 0)) * bulk)
    }
  })
}

/** 마지막 구간과 그 직전 구간에서 실제로 계산해 시계열과 어긋나지 않게 한다. */
function buildSummary(series: GrowthSeriesPoint[]): GrowthSummaryMetrics {
  const last = series[series.length - 1]
  const prev = series[series.length - 2]

  const rate = (current: number | null, before: number | null): number | null =>
    current == null || before == null || before === 0
      ? null
      : Number(((current - before) / before).toFixed(4))

  return {
    avgViewers: last.avgViewers,
    avgViewersChangeRate: rate(last.avgViewers, prev.avgViewers),
    totalChat: last.totalChat,
    totalChatChangeRate: rate(last.totalChat, prev.totalChat),
    broadcastMinutes: last.broadcastMinutes,
    broadcastMinutesChangeRate: rate(last.broadcastMinutes, prev.broadcastMinutes)
  }
}

/** 주간 12구간 / 월간 6구간 */
const PERIOD_COUNT: Record<PeriodType, number> = { WEEKLY: 12, MONTHLY: 6 }

export function buildMockGrowthReport(periodType: PeriodType): GrowthReport {
  const series = buildSeries(periodType, PERIOD_COUNT[periodType])
  return {
    periodType,
    from: series[0].periodStart,
    to: series[series.length - 1].periodEnd,
    summaryMetrics: buildSummary(series),
    series
  }
}

/**
 * 성장 목표 3건 — 진행 중 / 초과 달성 / 크게 미달을 모두 담아
 * 달성률 바와 상태 배지를 한 화면에서 확인할 수 있게 했다.
 * 마지막 목표는 `actions`를 비워 "제안 없음" 상태도 확인한다.
 */
export const MOCK_GROWTH_GOALS: GrowthGoal[] = [
  {
    growthGoalId: 21,
    metricType: 'AVG_VIEWERS',
    targetValue: 2500,
    unit: 'VIEWERS',
    periodStart: '2026-07-01',
    periodEnd: '2026-09-30',
    status: 'ACTIVE',
    currentValue: 2138,
    progressRate: 0.8552,
    actions: [
      {
        growthActionId: 91,
        title: '롤토체스 편성 확대',
        content: '롤토체스 편성을 주 2회로 늘리세요. 최근 6주 평균 유입이 42% 높았습니다.'
      },
      {
        growthActionId: 92,
        title: '오프닝 30분 밀도 높이기',
        content: '방송 시작 직후 유입이 정체됩니다. 오늘의 목표를 먼저 알리는 코너를 넣어 보세요.'
      }
    ]
  },
  {
    growthGoalId: 22,
    metricType: 'TOTAL_CHAT',
    targetValue: 45_000,
    unit: 'MESSAGES',
    periodStart: '2026-07-01',
    periodEnd: '2026-09-30',
    status: 'ACHIEVED',
    currentValue: 48_200,
    progressRate: 1.0711,
    actions: [
      {
        growthActionId: 93,
        title: '참여형 코너를 정기화하기',
        content:
          '시청자 참여 대회가 있던 주에 채팅이 2.4배로 늘었습니다. 격주 고정 편성을 검토해 보세요.'
      }
    ]
  },
  {
    growthGoalId: 23,
    metricType: 'BROADCAST_MINUTES',
    targetValue: 3_200,
    unit: 'MINUTES',
    periodStart: '2026-07-01',
    periodEnd: '2026-09-30',
    status: 'ACTIVE',
    currentValue: 1_290,
    progressRate: 0.4031,
    actions: []
  }
]
