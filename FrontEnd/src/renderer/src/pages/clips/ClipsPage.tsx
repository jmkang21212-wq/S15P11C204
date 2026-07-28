import { useCallback, useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { CalendarDays, Film } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import ClipCard, { ClipCardSkeleton } from './components/ClipCard'
import SessionSelect from './components/SessionSelect'
import { fetchEndedSessions } from '@renderer/api/broadcastSessions'
import { fetchSessionHighlights } from '@renderer/api/highlights'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { downloadFile, openExternal } from '@renderer/lib/desktopBridge'
import { formatCount } from '@renderer/lib/format'
import { PATHS } from '@renderer/routes/paths'

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

  const selectSession = (sessionId: number): void => {
    setSearchParams({ sessionId: String(sessionId) })
  }

  const playClip = (clipUrl: string): void => openExternal(clipUrl)
  const downloadClip = (clipUrl: string): void => downloadFile(clipUrl)

  const doneCount = highlights?.filter((h) => h.clipUrl !== null).length ?? 0
  const pendingCount = (highlights?.length ?? 0) - doneCount

  return (
    <div className="mx-auto flex max-w-[1400px] flex-col gap-6 p-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-[18px] font-bold tracking-[-0.02em]">하이라이트 / 클립</h1>
          <p className="text-xs text-muted-foreground">
            방송에서 자동으로 뽑아낸 구간이에요. 재생하거나 파일로 내려받을 수 있어요.
          </p>
        </div>
        {sessions && sessions.length > 0 && (
          <SessionSelect
            sessions={sessions}
            // 목록에 없는 세션이면 placeholder를 띄워 사용자가 다시 고를 수 있게 한다
            value={
              sessions.some((s) => s.sessionId === selectedSessionId) ? selectedSessionId : null
            }
            onChange={selectSession}
          />
        )}
      </header>

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

      {!sessionsError && (sessionsLoading || highlightsLoading) && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <ClipCardSkeleton key={i} />
          ))}
        </div>
      )}

      {!sessionsError && !highlightsLoading && highlightsError && (
        <ErrorState message="클립을 불러오지 못했습니다." onRetry={refetchHighlights} />
      )}

      {!sessionsError && !highlightsLoading && !highlightsError && highlights && (
        <>
          {highlights.length === 0 ? (
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
                  {formatCount(highlights.length)}
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
              </p>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                {highlights.map((highlight) => (
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
