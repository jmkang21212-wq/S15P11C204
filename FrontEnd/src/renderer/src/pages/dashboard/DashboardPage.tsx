import { useCallback } from 'react'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { getDashboard } from '@renderer/api/dashboard'
import { ErrorState } from '@renderer/components/common/StateView'
import UpcomingBroadcastCard from './components/UpcomingBroadcastCard'
import RecentSessionCard from './components/RecentSessionCard'
import PendingPromisesCard from './components/PendingPromisesCard'
import WeeklyMetricsCard from './components/WeeklyMetricsCard'
import RecentClipsCard from './components/RecentClipsCard'

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
 * 현재는 mock 데이터(getDashboard)로 렌더한다.
 */
export default function DashboardPage(): React.JSX.Element {
  const loader = useCallback(() => getDashboard(), [])
  const { data, loading, error, refetch } = useAsyncData(loader)

  // TODO: 인사말의 이름은 GET /streamers/me 프로필에서 가져오기
  const today = new Date().toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'long'
  })

  return (
    <div className="flex flex-col gap-4 p-8">
      <header>
        <h1 className="text-2xl font-bold tracking-tight">좋은 저녁이에요, 한별님</h1>
        <p className="mt-1 text-sm text-muted-foreground">{today}</p>
      </header>

      {loading && <DashboardSkeleton />}

      {!loading && (error || !data) && (
        <ErrorState message="대시보드를 불러오지 못했어요." onRetry={refetch} />
      )}

      {!loading && data && (
        <>
          <div className="grid grid-cols-2 gap-4">
            <UpcomingBroadcastCard broadcast={data.upcomingBroadcast} />
            <RecentSessionCard session={data.recentSession} />
            <PendingPromisesCard promises={data.pendingPromises} count={data.pendingPromiseCount} />
            <WeeklyMetricsCard metrics={data.weeklyMetrics} />
          </div>
          <RecentClipsCard highlights={data.recentHighlights} />
        </>
      )}
    </div>
  )
}
