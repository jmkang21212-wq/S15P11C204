import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarDays } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import SessionCard, { SessionCardSkeleton } from './components/SessionCard'
import { fetchEndedSessions } from '@renderer/api/sessionReport'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { PATHS } from '@renderer/routes/paths'

/**
 * SC-06 세션 리포트 — 목록.
 * 종료된 방송을 카드 그리드로 보여주고, 선택하면 해당 세션의 리포트 상세로 이동한다.
 */
export default function SessionReportListPage(): React.JSX.Element {
  const navigate = useNavigate()
  const loader = useCallback(() => fetchEndedSessions(), [])
  const { data, loading, error, refetch } = useAsyncData(loader)

  const openReport = (sessionId: number): void => {
    navigate(PATHS.sessionReportDetail.replace(':sessionId', String(sessionId)))
  }

  return (
    <div className="mx-auto flex max-w-[1400px] flex-col gap-6 p-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-[18px] font-bold tracking-[-0.02em]">세션 리포트</h1>
        <p className="text-xs text-muted-foreground">종료된 방송의 분석 결과를 확인하세요.</p>
      </header>

      {loading && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <SessionCardSkeleton key={i} />
          ))}
        </div>
      )}

      {!loading && error && (
        <ErrorState message="세션 목록을 불러오지 못했습니다." onRetry={refetch} />
      )}

      {!loading && !error && data?.length === 0 && (
        <EmptyState
          icon={CalendarDays}
          title="아직 종료된 방송이 없어요"
          description="방송을 마치면 분석 리포트가 여기에 쌓입니다."
          action={
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => navigate(PATHS.dashboard)}
            >
              대시보드로 가기
            </Button>
          }
        />
      )}

      {!loading && !error && data && data.length > 0 && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {data.map((session) => (
            <SessionCard key={session.sessionId} session={session} onClick={openReport} />
          ))}
        </div>
      )}
    </div>
  )
}
