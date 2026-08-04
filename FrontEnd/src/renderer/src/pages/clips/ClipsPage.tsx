import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AlertTriangle, CalendarDays, Clock3, FileX, Film } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import PageHeader from '@renderer/components/common/PageHeader'
import ClipCard, { ClipCardSkeleton } from './components/ClipCard'
import SessionSelect from '@renderer/components/common/SessionSelect'
import { fetchEndedSessions } from '@renderer/api/broadcastSessions'
import { fetchSessionHighlights } from '@renderer/api/highlights'
import { ApiError } from '@renderer/api/client'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { triggerClipWorker } from '@renderer/hooks/useClipWorker'
import { downloadFile, openExternal } from '@renderer/lib/desktopBridge'
import { formatCount } from '@renderer/lib/format'
import { PATHS } from '@renderer/routes/paths'
import type { ReportHighlight } from '@renderer/types/report'

/** 클립 목록 조회 실패 시 기다리면 되는 상태인지 구분한다 (BE: ReportErrorCode / BroadcastSessionErrorCode). */
const WAITING_CODES = new Set(['REPORT_GENERATING'])
const NO_REPORT_CODES = new Set(['REPORT_NOT_FOUND', 'SESSION_NOT_FOUND'])

/** 10초마다면 생성 중인 클립을 놓치지 않으면서도 서버에 부담을 주지 않는다. */
const CLIP_POLL_MS = 10_000

/**
 * SC-07 하이라이트 / 클립.
 * 종료된 방송을 골라 자동 생성된 클립을 그리드로 본다. 재생·다운로드는 Electron 브릿지를 쓴다.
 *
 * SC-06 리포트에서 `?sessionId=`를 물고 넘어오며, 세션을 바꾸면 URL도 함께 갱신된다.
 */
export default function ClipsPage(): React.JSX.Element {
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const sessionsLoader = useCallback(() => fetchEndedSessions(), [])
  const {
    data: sessions,
    loading: sessionsLoading,
    error: sessionsError,
    refetch: refetchSessions
  } = useAsyncData(sessionsLoader)

  /**
   * URL의 sessionId가 우선.
   * 없으면 분석이 완료된 가장 최근 세션을 고른다. 그냥 최신 세션을 잡으면 첫 화면이
   * 빈 상태로 뜨기 쉬운데, 하이라이트는 AI가 만드는 산출물이라
   * PROCESSING(분석 중)·FAILED는 물론 PARTIAL(AI 단계 일부 실패)에도 없을 수 있기 때문이다.
   * 완료된 세션이 하나도 없으면 최신 세션으로 떨어진다.
   */
  const selectedSessionId = useMemo(() => {
    const fromUrl = Number(searchParams.get('sessionId'))
    if (Number.isFinite(fromUrl) && fromUrl > 0) return fromUrl
    const completed = sessions?.find((s) => s.reportStatus === 'COMPLETED')
    return completed?.sessionId ?? sessions?.[0]?.sessionId ?? null
  }, [searchParams, sessions])

  const highlightsLoader = useCallback(() => {
    if (selectedSessionId === null) return Promise.resolve([])
    return fetchSessionHighlights(selectedSessionId)
  }, [selectedSessionId])
  const {
    data: highlights,
    loading: highlightsLoading,
    error: highlightsError,
    refetch: refetchHighlights
  } = useAsyncData(highlightsLoader)

  /**
   * 마지막으로 성공한 응답을 붙들어 둔다 (DashboardPage.tsx와 같은 패턴).
   * 생성 중인 클립이 있으면 아래에서 10초마다 재조회하는데, 그때마다 그리드 전체를
   * 스켈레톤으로 바꾸면 이미 완료된 클립까지 깜빡인다. 세션을 바꾸면 즉시 비워서
   * 직전 세션의 클립이 잠깐이라도 남아 보이지 않게 한다.
   */
  const [lastGoodHighlights, setLastGoodHighlights] = useState<ReportHighlight[] | null>(null)
  const [lastGoodSessionId, setLastGoodSessionId] = useState<number | null>(null)
  if (selectedSessionId !== lastGoodSessionId) {
    setLastGoodHighlights(null)
    setLastGoodSessionId(selectedSessionId)
  } else if (!highlightsLoading && !highlightsError && highlights !== lastGoodHighlights) {
    setLastGoodHighlights(highlights)
  }

  // 클립 화면을 열어 둔 채로 생성이 끝나는 걸 보고 싶을 때를 위한 두 트리거.
  // useClipWorker(AppLayout의 부팅 1회)와 별개로, 이 화면에 들어올 때도 한 번 깨운다.
  useEffect(() => {
    triggerClipWorker()
  }, [])

  const hasPendingClip = (lastGoodHighlights ?? []).some((h) => h.clipStatus === 'PENDING')
  useEffect(() => {
    if (!hasPendingClip) return
    const id = setInterval(refetchHighlights, CLIP_POLL_MS)
    return () => clearInterval(id)
  }, [hasPendingClip, refetchHighlights])

  const selectSession = (sessionId: number): void => {
    setSearchParams({ sessionId: String(sessionId) })
  }

  const playClip = (clipUrl: string): void => openExternal(clipUrl)
  const downloadClip = (clipUrl: string): void => downloadFile(clipUrl)

  const doneCount = lastGoodHighlights?.filter((h) => h.clipUrl !== null).length ?? 0
  const failedCount =
    lastGoodHighlights?.filter((h) => h.clipStatus === 'FAILED' || h.clipStatus === 'SKIPPED')
      .length ?? 0
  const pendingCount = (lastGoodHighlights?.length ?? 0) - doneCount - failedCount

  return (
    <div className="mx-auto flex max-w-[1400px] flex-col gap-6 p-8">
      <PageHeader
        title="하이라이트 / 클립"
        caption="방송에서 자동으로 뽑아낸 구간이에요. 재생하거나 파일로 내려받을 수 있어요."
        actions={
          sessions &&
          sessions.length > 0 && (
            <SessionSelect
              sessions={sessions}
              // 목록에 없는 세션이면 placeholder를 띄워 사용자가 다시 고를 수 있게 한다
              value={
                sessions.some((s) => s.sessionId === selectedSessionId) ? selectedSessionId : null
              }
              onChange={selectSession}
            />
          )
        }
      />

      {/* 세션 목록 자체를 못 불러온 경우 — 아래 그리드를 그릴 근거가 없다 */}
      {sessionsError && (
        <ErrorState message="방송 목록을 불러오지 못했습니다." onRetry={refetchSessions} />
      )}

      {!sessionsError && !sessionsLoading && sessions?.length === 0 && (
        <EmptyState
          icon={CalendarDays}
          title="아직 종료된 방송이 없어요"
          description="방송을 마치면 하이라이트 클립이 여기에 쌓입니다."
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

      {/* 첫 로딩에만 스켈레톤을 쓴다 — 이미 보여줄 클립이 있으면(lastGoodHighlights)
          10초 폴링·재조회 때마다 그리드가 깜빡이지 않는다. */}
      {!sessionsError && lastGoodHighlights === null && (sessionsLoading || highlightsLoading) && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <ClipCardSkeleton key={i} />
          ))}
        </div>
      )}

      {/* 보여줄 클립이 없는 상태에서 조회가 실패했을 때만 — 코드별로 "기다리면 되는 것"과
          "잘못된 것"을 구분한다. 이미 클립을 보여주고 있으면 폴링 실패는 조용히 넘긴다. */}
      {!sessionsError &&
        lastGoodHighlights === null &&
        !highlightsLoading &&
        highlightsError &&
        (highlightsError instanceof ApiError && WAITING_CODES.has(highlightsError.code ?? '') ? (
          <EmptyState
            icon={Clock3}
            title="분석이 아직 진행 중이에요"
            description="방송 분석이 끝나면 하이라이트가 여기에 나타나요."
          />
        ) : highlightsError instanceof ApiError && highlightsError.code === 'REPORT_FAILED' ? (
          <EmptyState
            icon={AlertTriangle}
            title="이 방송은 분석에 실패했어요"
            description="분석이 실패해 하이라이트 클립을 만들 수 없어요."
          />
        ) : highlightsError instanceof ApiError &&
          NO_REPORT_CODES.has(highlightsError.code ?? '') ? (
          <EmptyState icon={FileX} title="이 방송에는 리포트가 없어요" />
        ) : (
          <ErrorState message="클립을 불러오지 못했습니다." onRetry={refetchHighlights} />
        ))}

      {!sessionsError && lastGoodHighlights !== null && (
        <>
          {lastGoodHighlights.length === 0 ? (
            <EmptyState
              icon={Film}
              title="이 방송에는 클립이 없어요"
              description="하이라이트로 뽑을 만한 구간을 찾지 못했거나 분석이 아직 끝나지 않았습니다."
            />
          ) : (
            <>
              <p className="text-xs text-muted-foreground">
                총{' '}
                <span className="font-mono font-semibold text-foreground">
                  {formatCount(lastGoodHighlights.length)}
                </span>
                개 · 완료{' '}
                <span className="font-mono font-semibold text-foreground">
                  {formatCount(doneCount)}
                </span>
                개 · 생성 중{' '}
                <span className="font-mono font-semibold text-foreground">
                  {formatCount(pendingCount)}
                </span>
                개
                {failedCount > 0 && (
                  <>
                    {' '}
                    · 실패{' '}
                    <span className="font-mono font-semibold text-destructive">
                      {formatCount(failedCount)}
                    </span>
                    개
                  </>
                )}
              </p>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                {lastGoodHighlights.map((highlight) => (
                  <ClipCard
                    key={highlight.highlightId}
                    highlight={highlight}
                    onPlay={playClip}
                    onDownload={downloadClip}
                  />
                ))}
              </div>
            </>
          )}
        </>
      )}
    </div>
  )
}
