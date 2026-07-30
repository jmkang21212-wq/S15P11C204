import { useCallback } from 'react'
import { useParams, useNavigate, useOutletContext } from 'react-router-dom'
import { MonitorPlay } from 'lucide-react'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { useAudioStream } from '@renderer/hooks/useAudioStream'
import type { CurrentSessionContext } from '@renderer/hooks/useCurrentSession'
import { getLiveSnapshot } from '@renderer/api/console'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { cn } from '@renderer/lib/utils'
import { Button } from '@renderer/components/ui/button'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import { PATHS } from '@renderer/routes/paths'
import ConsoleStatusBar from './components/ConsoleStatusBar'
import BriefingFeed from './components/BriefingFeed'
import SentimentPanel from './components/SentimentPanel'
import RepeatedQuestions from './components/RepeatedQuestions'

const AUDIO_STATUS_LABEL = {
  IDLE: '음성 분석 대기',
  CONNECTING: 'AI 서버 연결 중…',
  STREAMING: 'AI 서버 연결됨 · 음성 분석 중',
  ERROR: 'AI 서버 연결 끊김'
} as const

/**
 * SC-03 방송 콘솔.
 * 지표·여론·반복 질문은 아직 mock 스냅샷이고, 위험 발언·약속 브리핑은
 * AI 서버 음성 스트리밍(useAudioStream)에서 실시간으로 들어온다.
 * TODO: 나머지 항목도 WebSocket(`/ws/v1/broadcast-sessions/{id}`)으로 실시간 갱신.
 */
export default function BroadcastConsolePage(): React.JSX.Element {
  const navigate = useNavigate()
  const { sessionId } = useParams()
  // URL에 sessionId가 없으면(GNB LIVE 배너를 거치지 않고 /console로 바로 들어온 경우)
  // 서버가 보고하는 진행 중 세션을 대신 쓴다. 폴링은 ConsoleLayout이 한 번만 돌려 여기로 내려준다.
  const { session: currentSession, loading: currentLoading } =
    useOutletContext<CurrentSessionContext>()
  const sid = sessionId ? Number(sessionId) : (currentSession?.sessionId ?? null)

  const loader = useCallback(() => {
    if (sid === null) return Promise.resolve(null)
    return getLiveSnapshot(sid)
  }, [sid])
  const { data, loading, error, refetch } = useAsyncData(loader)
  const audio = useAudioStream(`session${sid}`)

  if (sessionId === undefined && currentLoading) {
    return (
      <div className="flex h-full flex-col gap-4 p-5">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-full w-full rounded-xl" />
      </div>
    )
  }

  if (sid === null) {
    return (
      <div className="flex h-full items-center justify-center">
        <EmptyState
          icon={MonitorPlay}
          title="진행 중인 방송이 없어요"
          description="방송을 시작하면 콘솔이 여기에 표시됩니다."
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
      </div>
    )
  }

  if (loading) {
    return (
      <div className="flex h-full flex-col gap-4 p-5">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-full w-full rounded-xl" />
      </div>
    )
  }

  if (error || !data) {
    return (
      <div className="flex h-full items-center justify-center">
        <ErrorState message="콘솔 상태를 불러오지 못했어요." onRetry={refetch} />
      </div>
    )
  }

  const streaming = audio.status === 'STREAMING'

  return (
    <div className="flex h-full flex-col">
      <ConsoleStatusBar
        session={data.session}
        onOpenMini={() => window.api.openMiniWindow()}
        audio={audio}
      />

      <div className="flex flex-1 overflow-hidden">
        {/* 좌: 브리핑 피드 — 실시간 이벤트를 스냅샷 위에 최신순으로 쌓는다 */}
        <BriefingFeed briefings={[...audio.briefings, ...data.briefings]} />

        {/* 우: 여론 · 반복 질문 · 연결 상태 */}
        <aside className="flex w-[328px] shrink-0 flex-col gap-4 border-l bg-card p-5">
          <SentimentPanel sentiment={data.sentiment} />
          <RepeatedQuestions questions={data.repeatedQuestions} />
          <div
            className={cn(
              'mt-auto flex items-center gap-2 rounded-lg px-3 py-2 text-caption',
              streaming
                ? 'bg-success/10 text-success'
                : audio.status === 'ERROR'
                  ? 'bg-destructive/10 text-destructive'
                  : 'bg-muted text-muted-foreground'
            )}
          >
            <span
              className={cn(
                'size-1.5 rounded-full',
                streaming
                  ? 'bg-success'
                  : audio.status === 'ERROR'
                    ? 'bg-destructive'
                    : 'bg-muted-foreground'
              )}
            />
            {AUDIO_STATUS_LABEL[audio.status]}
          </div>
        </aside>
      </div>
    </div>
  )
}
