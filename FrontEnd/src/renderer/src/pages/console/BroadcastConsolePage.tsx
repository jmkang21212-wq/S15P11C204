import { useParams } from 'react-router-dom'
import { useLiveSnapshot } from '@renderer/hooks/useLiveSnapshot'
import { useAudioStream } from '@renderer/hooks/useAudioStream'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { cn } from '@renderer/lib/utils'
import ConsoleStatusBar from './components/ConsoleStatusBar'
import BriefingFeed from './components/BriefingFeed'
import SentimentPanel from './components/SentimentPanel'
import RepeatedQuestions from './components/RepeatedQuestions'

/**
 * 기본 세션 ID — /console(파라미터 없음) 진입 시 사용한다.
 * TODO: POST /broadcast-sessions 연동 후 실제 세션 ID로 교체 (플랫폼 연동 필요).
 */
const DEFAULT_SESSION_ID = 9002

const AUDIO_STATUS_LABEL = {
  IDLE: '음성 분석 대기',
  CONNECTING: 'AI 서버 연결 중…',
  STREAMING: 'AI 서버 연결됨 · 음성 분석 중',
  ERROR: 'AI 서버 연결 끊김'
} as const

/**
 * SC-03 방송 콘솔.
 *
 * 두 갈래에서 데이터가 온다:
 *  - 백엔드 스냅샷 폴링(2.5초) — 시청자·채팅 여론·수집기 상태
 *  - AI 음성 서버 스트리밍 — 위험 발언·약속 브리핑, 마이크·STT 상태
 *
 * 스냅샷 조회가 실패해도 화면을 비우지 않는다. 음성 분석은 백엔드와 무관하게
 * 동작하므로, 스냅샷이 없다고 콘솔 전체를 막으면 쓸 수 있는 기능까지 잃는다.
 */
export default function BroadcastConsolePage(): React.JSX.Element {
  const { sessionId } = useParams()
  const sid = sessionId ? Number(sessionId) : DEFAULT_SESSION_ID
  const { data, loading, error } = useLiveSnapshot(sid)
  const audio = useAudioStream(`session${sid}`)

  if (loading) {
    return (
      <div className="flex h-full flex-col gap-4 p-5">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-full w-full rounded-xl" />
      </div>
    )
  }

  const streaming = audio.status === 'STREAMING'

  return (
    <div className="flex h-full flex-col">
      <ConsoleStatusBar
        session={data?.session ?? null}
        services={data?.services ?? null}
        onOpenMini={() => window.api.openMiniWindow()}
        audio={audio}
      />

      <div className="flex flex-1 overflow-hidden">
        {/* 좌: 브리핑 피드 — 음성 서버 실시간 이벤트 + 백엔드 브리핑(현재 빈 배열) */}
        <BriefingFeed briefings={[...audio.briefings, ...(data?.briefings ?? [])]} />

        {/* 우: 여론 · 반복 질문 · 연결 상태 */}
        <aside className="flex w-[328px] shrink-0 flex-col gap-4 border-l bg-card p-5">
          {data ? (
            <>
              <SentimentPanel sentiment={data.sentiment} />
              <RepeatedQuestions questions={data.repeatedQuestions} />
              {/* 폴링이 일시적으로 실패하면 직전 값을 유지하고 갱신 실패만 알린다 */}
              {error && (
                <p className="text-caption text-warning">최신 정보로 갱신하지 못했어요</p>
              )}
            </>
          ) : (
            <p className="rounded-lg bg-muted/40 px-3 py-6 text-center text-caption text-muted-foreground">
              시청자·여론 정보를 불러올 수 없어요.
              <br />
              음성 분석은 그대로 사용할 수 있어요.
            </p>
          )}

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
