import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useLiveSnapshot } from '@renderer/hooks/useLiveSnapshot'
import ConsoleStatusBar from './components/ConsoleStatusBar'
import BriefingFeed from './components/BriefingFeed'
import SentimentPanel from './components/SentimentPanel'
import RepeatedQuestions from './components/RepeatedQuestions'
import MiniWidget from './components/MiniWidget'

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
  const { data, isLoading, error } = useLiveSnapshot(sid)
  const [miniOpen, setMiniOpen] = useState(false)

  if (isLoading) {
    return <div className="flex h-full items-center justify-center text-sm text-muted-foreground">콘솔 불러오는 중…</div>
  }

  if (error || !data) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-destructive">
        콘솔 상태를 불러오지 못했어요.
      </div>
    )
  }

  return (
    <div className="relative flex h-full flex-col">
      <ConsoleStatusBar
        session={data.session}
        services={data.services}
        miniOpen={miniOpen}
        onToggleMini={() => setMiniOpen((v) => !v)}
      />

      {/* 인앱 미니 위젯 (데모용 토글) — 추후 별도 항상-위 창으로 대체 */}
      {miniOpen && (
        <div className="absolute right-6 bottom-6 z-50">
          <button
            type="button"
            onClick={() => setMiniOpen(false)}
            className="absolute -top-2 -right-2 z-10 flex size-6 items-center justify-center rounded-full bg-neutral-700 text-xs text-white"
            aria-label="미니 모드 닫기"
          >
            ✕
          </button>
          <MiniWidget data={data} isHigh={false} />
        </div>
      )}

      <div className="flex flex-1 overflow-hidden">
        {/* 좌: 브리핑 피드 */}
        <BriefingFeed briefings={data.briefings} />

        {/* 우: 여론 · 반복 질문 · 연결 상태 */}
        <aside className="flex w-[328px] shrink-0 flex-col gap-4 border-l bg-card p-5">
          <SentimentPanel sentiment={data.sentiment} />
          <RepeatedQuestions questions={data.repeatedQuestions} />
          <div className="mt-auto flex items-center gap-2 rounded-lg bg-success/10 px-3 py-2 text-[11px] text-success">
            <span className="size-1.5 rounded-full bg-success" />
            WebSocket 연결됨
          </div>
        </aside>
      </div>
    </div>
  )
}
