import { useCallback, useState } from 'react'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { useAuth } from '@renderer/auth/authStore'
import { getDashboard } from '@renderer/api/dashboard'
import { ErrorState } from '@renderer/components/common/StateView'
import UpcomingBroadcastCard from './components/UpcomingBroadcastCard'
import RecentSessionCard from './components/RecentSessionCard'
import PendingPromisesCard from './components/PendingPromisesCard'
import WeeklyMetricsCard from './components/WeeklyMetricsCard'
import RecentClipsCard from './components/RecentClipsCard'
import type { DashboardData } from '@renderer/types/dashboard'

/** 로딩 중 표시할 간단한 스켈레톤. (실제 상태 UI는 API 연동 단계에서 보강) */
function DashboardSkeleton(): React.JSX.Element {
  return (
    <div className="grid grid-cols-2 gap-4">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="h-44 animate-pulse rounded-xl border bg-muted/50" />
      ))}
    </div>
  )
}

/**
 * SC-02 대시보드 — 방송 전 상태를 한눈에 파악하는 홈 화면.
 *
 * 카드 다섯 개가 한 번의 `GET /dashboard` 응답을 나눠 쓴다. 조각별로 null이 올 수 있어
 * (예정 방송 없음·끝낸 방송 없음·지난 주 데이터 없음) 각 카드가 자기 빈 상태를 그린다.
 */
export default function DashboardPage(): React.JSX.Element {
  const loader = useCallback(() => getDashboard(), [])
  const { data, loading, error, refetch } = useAsyncData(loader)

  /**
   * 마지막으로 성공한 응답을 붙들어 둔다.
   *
   * `useAsyncData`는 재조회가 시작되면 loading으로 돌아가는데, 그때마다 스켈레톤으로 바꾸면
   * 카드 하나의 버튼(약속 완료)이 화면 전체를 깜빡이게 만든다. 마지막 값을 그대로 보여주면
   * 새 응답이 도착하는 순간 필요한 카드만 조용히 바뀐다. 스켈레톤은 첫 로딩에만 쓴다.
   * (`useLiveSnapshot`이 3초 폴링에 같은 방식을 쓴다.)
   */
  const [lastGood, setLastGood] = useState<DashboardData | null>(null)
  if (!loading && !error && data !== lastGood) {
    setLastGood(data)
  }

  // 로그인 시 authStore에 이미 들어와 있는 값이라 별도 조회가 필요 없다(GNB 하단과 같은 소스).
  const { streamer } = useAuth()

  const today = new Date().toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })

  return (
    <div className="flex flex-col gap-4 p-8">
      <header>
        <h1 className="text-title font-bold tracking-[-0.02em]">
          좋은 저녁이에요, {streamer?.nickname ?? '스트리머'}님
        </h1>
        <p className="text-xs text-muted-foreground">{today}</p>
      </header>

      {/* 스켈레톤·에러는 보여줄 값이 아직 없을 때만. 재조회 중에는 이전 화면을 유지한다. */}
      {lastGood === null && loading && <DashboardSkeleton />}

      {lastGood === null && !loading && (
        <ErrorState message="대시보드를 불러오지 못했어요." onRetry={refetch} />
      )}

      {lastGood && (
        <>
          <div className="grid grid-cols-2 gap-4">
            <UpcomingBroadcastCard broadcast={lastGood.upcomingBroadcast} />
            <RecentSessionCard session={lastGood.recentSession} />
            <PendingPromisesCard
              promises={lastGood.pendingPromises}
              count={lastGood.pendingPromiseCount}
              onChanged={refetch}
            />
            <WeeklyMetricsCard metrics={lastGood.weeklyMetrics} />
          </div>
          <RecentClipsCard highlights={lastGood.recentHighlights} />
        </>
      )}
    </div>
  )
}
