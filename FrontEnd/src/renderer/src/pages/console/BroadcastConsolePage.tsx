import { useEffect, useState } from 'react'
import { useParams, useNavigate, useOutletContext } from 'react-router-dom'
import { useLiveSnapshot } from '@renderer/hooks/useLiveSnapshot'
import { useAudioStream } from '@renderer/hooks/useAudioStream'
import { mergeBriefings } from '@renderer/lib/briefings'
import type { CurrentSessionContext } from '@renderer/hooks/useCurrentSession'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { cn } from '@renderer/lib/utils'
import { PATHS } from '@renderer/routes/paths'
import ConsoleStatusBar from './components/ConsoleStatusBar'
import BriefingFeed from './components/BriefingFeed'
import SentimentPanel from './components/SentimentPanel'
import ChatSummaryButton from './components/ChatSummaryButton'
import RepeatedQuestions from './components/RepeatedQuestions'

/**
 * 방송 시작 직후 음성 분석을 켜기 위한 예약 플래그.
 *
 * `useAudioStream`의 start()는 호출 시점의 sessionId를 캡처하므로(hooks/useAudioStream.ts),
 * 세션이 확정되기 전에 부르면 전사·경고가 DB에 저장되지 않는다. 그래서 시작 직후 바로
 * 켜지 않고, 라우팅으로 sid가 반영된 뒤 effect에서 켠다.
 *
 * 컴포넌트 상태가 아니라 모듈 레벨에 두는 이유는 `/console` → `/console/:sessionId`
 * 라우팅 때문이다. 이 둘은 서로 다른 라우트 엔트리라 페이지가 다시 마운트될 수 있는데,
 * 그러면 useRef 값이 사라져 음성 분석이 조용히 안 켜진다. App.tsx의 bootRefresh와 같은 이유.
 */
let pendingAudioStart = false

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
 *  - 백엔드 스냅샷 폴링(useLiveSnapshot, 3초 · owner 모드) — 시청자·채팅 여론·수집기 상태,
 *    브리핑 히스토리(진입 전 이력)와 CHAT_SUMMARY.
 *    이 창(콘솔)이 owner라 조회 결과를 main을 거쳐 미니·오버레이 창에도 릴레이한다.
 *  - AI 음성 서버 스트리밍(useAudioStream) — 진입 후 위험 발언·약속 브리핑, 마이크·STT 상태
 *
 * 위험 발언·약속은 두 경로로 겹쳐 오므로 진입 시각을 컷오버로 병합한다(lib/briefings.ts).
 *
 * 스냅샷 조회가 실패해도 화면을 비우지 않는다. 음성 분석은 백엔드와 무관하게
 * 동작하므로, 스냅샷이 없다고 콘솔 전체를 막으면 쓸 수 있는 기능까지 잃는다.
 *
 * 진행 중인 방송이 없어도(sid === null) 같은 화면을 그대로 보여준다 — 방송 시작이
 * 이 화면에 있기 때문이다. 각 패널은 각자의 빈 상태로 떨어지고, 상태줄 버튼만
 * "방송 시작"으로 바뀐다.
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
  // 브리핑 병합 컷오버(마운트 시각 고정) — 이 시각 이후의 RISK·PROMISE는
  // WS(useAudioStream)가 전담하고, 스냅샷에서는 이전 것만 히스토리로 받는다.
  const [briefingCutoff] = useState(() => new Date().toISOString())

  const startAudio = audio.start

  const handleStarted = (newSessionId: number): void => {
    pendingAudioStart = true
    navigate(PATHS.consoleSession.replace(':sessionId', String(newSessionId)), { replace: true })
  }

  useEffect(() => {
    if (sid === null || !pendingAudioStart) return
    pendingAudioStart = false
    startAudio()
  }, [sid, startAudio])

  if (sessionId === undefined && currentLoading) {
    return (
      <div className="flex h-full flex-col gap-4 p-5">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-full w-full rounded-xl" />
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
        onStarted={handleStarted}
      />

      <div className="flex flex-1 overflow-hidden">
        {/* 좌: 브리핑 피드 — 진입 후는 음성 서버 실시간 이벤트, 진입 전 이력과 채팅 요약은 스냅샷 */}
        <BriefingFeed
          briefings={mergeBriefings(audio.briefings, data?.briefings ?? [], briefingCutoff)}
        />

        {/* 우: 여론 · 반복 질문 · 연결 상태 */}
        <aside className="flex w-[328px] shrink-0 flex-col gap-4 border-l bg-card p-5">
          {data && sid !== null ? (
            <>
              <SentimentPanel sentiment={data.sentiment} />
              {/* 요약은 3분 주기로도 생성되지만, 지금 분위기를 바로 보고 싶을 때 쓴다 */}
              <ChatSummaryButton sessionId={sid} />
              <RepeatedQuestions sessionId={sid} questions={data.repeatedQuestions} />
              {/* 폴링이 일시적으로 실패하면 직전 값을 유지하고 갱신 실패만 알린다 */}
              {error && <p className="text-caption text-warning">최신 정보로 갱신하지 못했어요</p>}
            </>
          ) : (
            <p className="rounded-lg bg-muted/40 px-3 py-6 text-center text-caption text-muted-foreground">
              {sid === null ? (
                <>
                  방송을 시작하면 시청자·여론이 여기에 표시돼요.
                  <br />
                  먼저 OBS를 연결해 주세요.
                </>
              ) : (
                <>
                  시청자·여론 정보를 불러올 수 없어요.
                  <br />
                  음성 분석은 그대로 사용할 수 있어요.
                </>
              )}
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
