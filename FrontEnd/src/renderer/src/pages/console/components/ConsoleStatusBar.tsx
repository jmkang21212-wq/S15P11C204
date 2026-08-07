import { useEffect, useRef, useState } from 'react'
import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'
import { formatMs, formatCount } from '@renderer/lib/format'
import type { ConsoleServices, LiveSnapshotSession } from '@renderer/types/console'
import type { UseAudioStreamResult } from '@renderer/hooks/useAudioStream'
import BroadcastControl from './BroadcastControl'

interface ConsoleStatusBarProps {
  /** 진행 중 세션 ID. 아직 방송을 시작하지 않았으면 null(준비 모드)이다. */
  sessionId: number | null
  /** 스냅샷 조회 실패 시 null — 그래도 음성 제어·방송 종료는 계속 쓸 수 있어야 한다. */
  session: LiveSnapshotSession | null
  services: ConsoleServices | null
  onOpenMini: () => void
  /** 음성 스트리밍 — 마이크·STT 상태와 방송 시작/종료 연동에 함께 쓴다. */
  audio: UseAudioStreamResult
  /** 방송 시작 성공 — 라우팅과 음성 분석 시작 타이밍은 페이지가 맡는다. */
  onStarted: (sessionId: number) => void
}

type DotState = 'ok' | 'pending' | 'off'

function ServiceDot({ label, state }: { label: string; state: DotState }): React.JSX.Element {
  return (
    <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
      <span
        className={cn(
          'size-1.5 rounded-full',
          state === 'ok' ? 'bg-success' : state === 'pending' ? 'bg-warning' : 'bg-muted-foreground'
        )}
      />
      {label}
    </span>
  )
}

/**
 * 경과 시간 표시.
 *
 * `elapsedSeconds`는 3초 폴링(`useLiveSnapshot`) 응답에만 실려 오기 때문에 그대로 그리면
 * 시계가 3초씩 뛴다. 새 값이 올 때마다 (값, 도착 시각)을 기준점으로 잡고 그 사이를 로컬
 * 1초 티커로 메운다 — 표시만 매끄러워지고 폴링 주기와 서버 부하는 그대로다.
 *
 * 기준점을 매 응답마다 다시 잡는 게 핵심이다. 로컬 타이머가 밀리거나(창이 가려졌을 때의
 * 백그라운드 스로틀) PC 시계가 서버와 어긋나 있어도 다음 응답에서 곧바로 교정된다.
 *
 * LIVE일 때만 굴린다 — PREPARING은 startedAt이 없어 서버가 0을 고정으로 내려주고 종료된
 * 세션은 값이 멈추는데, 거기에 로컬 시간을 이어 붙이면 흐르지 않은 시간이 흐른다.
 *
 * 별도 컴포넌트로 뺀 건 1초마다의 리렌더를 이 span 안에 가두기 위해서다. 상태줄에는
 * OBS 상태를 구독하는 `BroadcastControl`이 같이 있어 통째로 다시 그릴 이유가 없다.
 */
function ElapsedClock({ session }: { session: LiveSnapshotSession }): React.JSX.Element {
  const live = session.status === 'LIVE'
  const [ticked, setTicked] = useState(session.elapsedSeconds)

  /**
   * 기준점은 state가 아니라 ref다. 새 응답이 올 때마다 갱신해야 하는데 effect 안에서
   * setState를 동기 호출하면 응답마다 렌더가 한 번씩 더 붙는다(react-hooks/set-state-in-effect).
   * 화면에 그리는 값은 아래 1초 티커가 따로 들고 있으므로 기준점 자체는 렌더를 유발할
   * 이유가 없다.
   */
  const anchorRef = useRef<{ seconds: number; at: number } | null>(null)

  useEffect(() => {
    anchorRef.current = live ? { seconds: session.elapsedSeconds, at: Date.now() } : null
  }, [live, session.elapsedSeconds])

  useEffect(() => {
    if (!live) return
    const id = window.setInterval(() => {
      const anchor = anchorRef.current
      if (anchor) {
        setTicked(anchor.seconds + Math.max(0, Math.floor((Date.now() - anchor.at) / 1000)))
      }
    }, 1_000)
    return () => window.clearInterval(id)
  }, [live])

  // 서버 값보다 뒤로 가지 않게 둘 중 큰 쪽을 쓴다. 티커는 최대 1초 늦게 따라오고(기준점을
  // 갈아 끼운 직후), 타이머가 밀렸다면 서버 값이 앞서므로 그때는 서버 값이 그대로 이긴다.
  const elapsed = live ? Math.max(ticked, session.elapsedSeconds) : session.elapsedSeconds

  return <span className="font-mono text-xl font-bold">{formatMs(elapsed * 1000)}</span>
}

export default function ConsoleStatusBar({
  sessionId,
  session,
  services,
  onOpenMini,
  audio,
  onStarted
}: ConsoleStatusBarProps): React.JSX.Element {
  // 마이크·STT는 백엔드가 항상 미연결로 내려준다(음성 파이프라인 미구현).
  // 실제 상태는 프론트 음성 스트림만 알고 있으므로 그 값을 쓴다.
  const audioState: DotState =
    audio.status === 'STREAMING' ? 'ok' : audio.status === 'CONNECTING' ? 'pending' : 'off'

  const collectorState: DotState = !services
    ? 'off'
    : services.collector === 'RUNNING'
      ? 'ok'
      : services.collector === 'RECONNECTING'
        ? 'pending'
        : 'off'

  return (
    <div className="flex items-center gap-4 border-b bg-card px-6 py-3">
      {/* 상태 + 경과 시간 */}
      <div className="flex items-center gap-2.5">
        <span
          className={cn(
            'rounded px-2 py-0.5 text-micro font-bold tracking-wide text-white',
            !session
              ? 'bg-muted-foreground'
              : session.status === 'PREPARING'
                ? 'bg-warning'
                : 'bg-destructive'
          )}
        >
          {session ? (session.status === 'PREPARING' ? '● 준비 중' : '● LIVE') : '●'}
        </span>
        {session ? (
          <ElapsedClock session={session} />
        ) : (
          <span className="font-mono text-xl font-bold">--:--</span>
        )}
      </div>

      {/* 현재 시청자 */}
      <div className="flex flex-col border-l pl-4">
        <span className="text-micro text-muted-foreground">현재 시청자</span>
        <span className="font-mono text-lg font-bold">
          {session ? formatCount(session.currentViewers) : '-'}
        </span>
      </div>

      {/* 서비스 상태 — 마이크·STT는 프론트 음성 스트림, AI·수집기는 백엔드 스냅샷 */}
      <div className="ml-auto flex items-center gap-3 rounded-lg bg-muted px-3 py-1.5">
        <ServiceDot label="마이크" state={audioState} />
        <ServiceDot label="STT" state={audioState} />
        <ServiceDot label="AI" state={services?.ai === 'RUNNING' ? 'ok' : 'off'} />
        {/* RECONNECTING 구간의 채팅은 유실되므로 반드시 보여준다 */}
        <ServiceDot label="수집기" state={collectorState} />
      </div>

      {/* 음성 분석 단독 제어 — 방송 중 마이크 문제로 다시 켜야 할 때 쓴다.
          세션 전에는 막는다: 전사·경고를 저장할 곳이 없어 브리핑이 유실된다. */}
      <Button
        size="sm"
        variant={audio.status === 'STREAMING' ? 'secondary' : 'outline'}
        disabled={audio.status === 'CONNECTING' || sessionId === null}
        title={sessionId === null ? '방송을 시작하면 사용할 수 있어요' : undefined}
        onClick={audio.status === 'STREAMING' ? audio.stop : audio.start}
      >
        {audio.status === 'CONNECTING'
          ? '연결 중…'
          : audio.status === 'STREAMING'
            ? '음성 분석 중지'
            : '음성 분석 시작'}
      </Button>

      {/* 미니 모드 = 항상-위 별도 창 열기 (IPC → main) */}
      <Button variant="outline" size="sm" onClick={onOpenMini}>
        미니 모드
      </Button>

      {/* OBS 연결 + 방송 시작/종료 — 세션과 송출을 한 버튼이 함께 제어한다 */}
      {/* 위 토글은 마이크만 끄지만(audio.stop), 방송 종료는 브리핑까지 정리한다(audio.end) */}
      <BroadcastControl sessionId={sessionId} onStarted={onStarted} onEnd={audio.end} />

      {audio.error && (
        <span className="max-w-48 truncate text-xs text-destructive" title={audio.error}>
          {audio.error}
        </span>
      )}
    </div>
  )
}
