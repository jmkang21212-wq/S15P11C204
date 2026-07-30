import { useParams, useNavigate, useOutletContext } from 'react-router-dom'
import { MonitorPlay } from 'lucide-react'
import { useLiveSnapshot } from '@renderer/hooks/useLiveSnapshot'
import { useAudioStream } from '@renderer/hooks/useAudioStream'
import type { CurrentSessionContext } from '@renderer/hooks/useCurrentSession'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { cn } from '@renderer/lib/utils'
import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
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
 *
 * 두 갈래에서 데이터가 온다:
 *  - 백엔드 스냅샷 폴링(useLiveSnapshot, 3초 · owner 모드) — 시청자·채팅 여론·수집기 상태.
 *    이 창(콘솔)이 owner라 조회 결과를 main을 거쳐 미니·오버레이 창에도 릴레이한다.
 *  - AI 음성 서버 스트리밍(useAudioStream) — 위험 발언·약속 브리핑, 마이크·STT 상태
 *
 * 스냅샷 조회가 실패해도 화면을 비우지 않는다. 음성 분석은 백엔드와 무관하게
 * 동작하므로, 스냅샷이 없다고 콘솔 전체를 막으면 쓸 수 있는 기능까지 잃는다.
 */
export default function BroadcastConsolePage(): React.JSX.Element {
  const navigate = useNavigate()
  const { sessionId } = useParams()
  // URL에 sessionId가 없으면(GNB LIVE 배너를 거치지 않고 /console로 바로 들어온 경우)
  // 서버가 보고하는 진행 중 세션을 대신 쓴다. 폴링은 ConsoleLayout이 한 번만 돌려 여기로 내려준다.
  const { session: currentSession, loading: currentLoading } =
    useOutletContext<CurrentSessionContext>()
  const sid = sessionId ? Number(sessionId) : (currentSession?.sessionId ?? null)

  const { snapshot: data, loading, error } = useLiveSnapshot(sid, 'owner')
  const audio = useAudioStream(`session${sid}`, sid)

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

  const streaming = audio.status === 'STREAMING'

  return (
    <div className="flex h-full flex-col">
      <ConsoleStatusBar
        sessionId={sid}
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
              <RepeatedQuestions sessionId={sid} questions={data.repeatedQuestions} />
              {/* 폴링이 일시적으로 실패하면 직전 값을 유지하고 갱신 실패만 알린다 */}
              {error && <p className="text-caption text-warning">최신 정보로 갱신하지 못했어요</p>}
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
