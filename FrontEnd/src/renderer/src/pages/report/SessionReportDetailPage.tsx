import { useCallback, useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { MessageSquare, Sparkles, TrendingUp, Users } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ErrorState } from '@renderer/components/common/StateView'
import ReportHeader from './components/ReportHeader'
import MetricCard, { MetricCardSkeleton } from '@renderer/components/common/MetricCard'
import ViewerTimelineChart from './components/ViewerTimelineChart'
import SentimentBreakdown from './components/SentimentBreakdown'
import InsightList from './components/InsightList'
import HighlightList from './components/HighlightList'
import { PartialSummaryNotice, ReportProcessingCard } from './components/ReportStatusNotice'
import { fetchSessionReport, REPORT_NOT_FOUND } from '@renderer/api/sessionReport'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { triggerClipWorker } from '@renderer/hooks/useClipWorker'
import { openExternal } from '@renderer/lib/desktopBridge'
import { formatCount, formatPercent } from '@renderer/lib/format'
import { PATHS } from '@renderer/routes/paths'
import { isProcessing } from '@renderer/types/report'

/** 로딩 중 상세 화면의 골격을 유지하는 스켈레톤. */
function DetailSkeleton(): React.JSX.Element {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Skeleton className="h-6 w-64" />
        <Skeleton className="h-3 w-80" />
      </div>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, i) => (
          <MetricCardSkeleton key={i} />
        ))}
      </div>
      <Skeleton className="h-85 w-full rounded-xl" />
    </div>
  )
}

/**
 * SC-06 세션 리포트 — 상세. "방송 직후 5분 복기"용 화면.
 *
 * 리포트 상태에 따라 화면이 갈린다.
 *   PROCESSING → 진행 카드로 전체 대체, 10초 간격으로 자동 새로고침
 *   PARTIAL    → AI 요약 자리에 앰버 배너, 지표·차트는 정상 렌더
 *   FAILED     → 에러 상태
 */
export default function SessionReportDetailPage(): React.JSX.Element {
  const navigate = useNavigate()
  const { sessionId } = useParams<{ sessionId: string }>()
  const numericSessionId = Number(sessionId)

  const loader = useCallback(() => fetchSessionReport(numericSessionId), [numericSessionId])
  const { data, loading, error, refetch } = useAsyncData(loader)

  // 분석 진행 중이면 수동 새로고침만으로는 불편하니 10초마다 자동으로 다시 조회한다.
  useEffect(() => {
    if (!data || !isProcessing(data)) return
    const id = window.setInterval(refetch, 10_000)
    return () => window.clearInterval(id)
  }, [data, refetch])

  // 리포트가 PROCESSING을 벗어나는 순간 = 하이라이트가 막 확정된 시점이다.
  // 이 화면엔 클립 완료를 알려주는 별도 push가 없어, 폴링으로 그 전환을 감지해
  // 클립 워커를 깨운다. FAILED로 끝나도 호출 자체는 무해하다(그 세션엔 작업이 없을 뿐).
  const wasProcessing = useRef(false)
  useEffect(() => {
    if (!data) return
    if (isProcessing(data)) {
      wasProcessing.current = true
      return
    }
    if (wasProcessing.current) {
      wasProcessing.current = false
      triggerClipWorker()
    }
  }, [data])

  const goBack = (): void => {
    navigate(PATHS.sessionReport)
  }
  /** SC-07 클립 화면으로. 보고 있던 세션을 그대로 물고 간다. */
  const goClips = (): void => {
    navigate(`${PATHS.clips}?sessionId=${numericSessionId}`)
  }
  /** 클립을 기본 브라우저로 연다. */
  const playClip = (clipUrl: string): void => openExternal(clipUrl)

  if (loading) {
    return (
      <div className="mx-auto max-w-350 p-8">
        <DetailSkeleton />
      </div>
    )
  }

  if (error || !data) {
    const notFound = error?.message === REPORT_NOT_FOUND
    return (
      <div className="mx-auto max-w-350 p-8">
        <ErrorState
          title={notFound ? '리포트를 찾을 수 없어요' : '리포트를 불러오지 못했어요'}
          message={
            notFound ? '삭제되었거나 아직 생성되지 않은 세션입니다.' : '잠시 후 다시 시도해 주세요.'
          }
          onRetry={notFound ? undefined : refetch}
        />
        {notFound && (
          <div className="flex justify-center">
            <button
              type="button"
              onClick={goBack}
              className="text-xs font-medium text-primary underline-offset-4 hover:underline"
            >
              세션 리포트 목록으로
            </button>
          </div>
        )}
      </div>
    )
  }

  // 분석 진행 중 — 지표가 아직 없으므로 화면 전체를 진행 카드로 대체한다.
  if (isProcessing(data)) {
    return (
      <div className="mx-auto flex max-w-350 flex-col gap-6 p-8">
        <ReportHeader
          session={data.session}
          status={data.status}
          generatedAt={null}
          onBack={goBack}
          onRefresh={refetch}
          onOpenClips={goClips}
        />
        <ReportProcessingCard session={data.session} progress={data.progress} onRefresh={refetch} />
      </div>
    )
  }

  if (data.status === 'FAILED') {
    return (
      <div className="mx-auto flex max-w-350 flex-col gap-6 p-8">
        <ReportHeader
          session={data.session}
          status={data.status}
          generatedAt={data.generatedAt}
          onBack={goBack}
          onRefresh={refetch}
          onOpenClips={goClips}
        />
        <ErrorState
          title="분석에 실패했어요"
          message="이 방송은 분석 중 오류가 발생해 리포트를 만들지 못했습니다."
          onRetry={refetch}
        />
      </div>
    )
  }

  const { metrics, session, segments, insights, highlights, viewerTimeline, summary } = data

  return (
    <div className="mx-auto flex max-w-350 flex-col gap-6 p-8">
      <ReportHeader
        session={session}
        status={data.status}
        generatedAt={data.generatedAt}
        onBack={goBack}
        onRefresh={refetch}
        onOpenClips={goClips}
      />

      {/* KPI — 증감 비교값이 응답에 없어 delta는 표시하지 않는다 */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <MetricCard label="평균 시청자" value={formatCount(metrics.avgViewers)} icon={Users} />
        <MetricCard
          label="최고 동시 시청자"
          value={formatCount(metrics.peakViewers)}
          icon={TrendingUp}
        />
        <MetricCard
          label="총 채팅 수"
          value={formatCount(metrics.totalChat)}
          icon={MessageSquare}
        />
        <MetricCard
          label="긍정 반응"
          value={formatPercent(metrics.positiveRatio)}
          icon={Sparkles}
          caption="전체 채팅 대비"
        />
      </div>

      {/* 2열: 주 콘텐츠 + 사이드 340px (design.md §2)
          GNB 232px를 뺀 콘텐츠 폭이 뷰포트보다 작으므로, 최소 창 폭 1240px에서도
          2열이 유지되도록 브레이크포인트를 한 단계 낮춘 lg로 잡는다. */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex min-w-0 flex-col gap-6">
          <ViewerTimelineChart
            timeline={viewerTimeline}
            segments={segments}
            startedAt={session.startedAt}
          />

          {summary === null ? (
            <PartialSummaryNotice />
          ) : (
            <Card>
              <CardHeader>
                <CardTitle className="text-sm font-semibold">AI 요약</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-body leading-relaxed text-muted-foreground">{summary}</p>
              </CardContent>
            </Card>
          )}

          <InsightList insights={insights} />
        </div>

        <aside className="flex flex-col gap-6">
          <SentimentBreakdown metrics={metrics} />
          <HighlightList highlights={highlights} onPlayClip={playClip} />
        </aside>
      </div>
    </div>
  )
}
