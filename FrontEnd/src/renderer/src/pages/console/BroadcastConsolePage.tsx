import { useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { getLiveSnapshot } from '@renderer/api/console'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ErrorState } from '@renderer/components/common/StateView'
import ConsoleStatusBar from './components/ConsoleStatusBar'
import BriefingFeed from './components/BriefingFeed'
import SentimentPanel from './components/SentimentPanel'
import RepeatedQuestions from './components/RepeatedQuestions'

/** 기본 세션 ID — /console(파라미터 없음) 진입 시 현재 세션 대체용. */
const DEFAULT_SESSION_ID = 9002

/**
 * SC-03 방송 콘솔 (정적 껍데기).
 * 현재는 mock 스냅샷으로 렌더한다.
 * TODO: 실제 연동 시 WebSocket(`/ws/v1/broadcast-sessions/{id}`)으로
 *  briefings·sentiment·repeatedQuestions·viewers를 실시간 갱신.
 */
export default function BroadcastConsolePage(): React.JSX.Element {
  const { sessionId } = useParams()
  const sid = sessionId ? Number(sessionId) : DEFAULT_SESSION_ID
  const loader = useCallback(() => getLiveSnapshot(sid), [sid])
  const { data, loading, error, refetch } = useAsyncData(loader)

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

  return (
    <div className="flex h-full flex-col">
      <ConsoleStatusBar
        session={data.session}
        services={data.services}
        onOpenMini={() => window.api.openMiniWindow()}
      />

      <div className="flex flex-1 overflow-hidden">
        {/* 좌: 브리핑 피드 */}
        <BriefingFeed briefings={data.briefings} />

        {/* 우: 여론 · 반복 질문 · 연결 상태 */}
        <aside className="flex w-[328px] shrink-0 flex-col gap-4 border-l bg-card p-5">
          <SentimentPanel sentiment={data.sentiment} />
          <RepeatedQuestions questions={data.repeatedQuestions} />
          <div className="mt-auto flex items-center gap-2 rounded-lg bg-success/10 px-3 py-2 text-caption text-success">
            <span className="size-1.5 rounded-full bg-success" />
            WebSocket 연결됨
          </div>
        </aside>
      </div>
    </div>
  )
}
