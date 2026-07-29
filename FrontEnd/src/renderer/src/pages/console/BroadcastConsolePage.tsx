import { useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { useAudioStream } from '@renderer/hooks/useAudioStream'
import { getLiveSnapshot } from '@renderer/api/console'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ErrorState } from '@renderer/components/common/StateView'
import { cn } from '@renderer/lib/utils'
import ConsoleStatusBar from './components/ConsoleStatusBar'
import BriefingFeed from './components/BriefingFeed'
import SentimentPanel from './components/SentimentPanel'
import RepeatedQuestions from './components/RepeatedQuestions'

/** 기본 세션 ID — /console(파라미터 없음) 진입 시 현재 세션 대체용. */
const DEFAULT_SESSION_ID = 9002

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
  const { sessionId } = useParams()
  const sid = sessionId ? Number(sessionId) : DEFAULT_SESSION_ID
  const loader = useCallback(() => getLiveSnapshot(sid), [sid])
  const { data, loading, error, refetch } = useAsyncData(loader)
  const audio = useAudioStream(`session${sid}`)

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
