import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'
import { formatMs, formatCount } from '@renderer/lib/format'
import type { ConsoleServices, LiveSnapshotSession } from '@renderer/types/console'
import type { UseAudioStreamResult } from '@renderer/hooks/useAudioStream'
import ObsControl from './ObsControl'
import SessionControl from './SessionControl'

interface ConsoleStatusBarProps {
  /** "분석 종료"가 걸 세션 ID. 호출부가 라우트/현재 세션에서 이미 확정한 값이라
   * 스냅샷 조회 성패와 무관하게 항상 있다. */
  sessionId: number
  /** 스냅샷 조회 실패 시 null — 그래도 음성 제어·분석 종료는 계속 쓸 수 있어야 한다. */
  session: LiveSnapshotSession | null
  services: ConsoleServices | null
  onOpenMini: () => void
  /** 음성 스트리밍 — 마이크·STT 상태와 방송 시작/종료 연동에 함께 쓴다. */
  audio: UseAudioStreamResult
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

export default function ConsoleStatusBar({
  sessionId,
  session,
  services,
  onOpenMini,
  audio
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
        <span className="font-mono text-xl font-bold">
          {session ? formatMs(session.elapsedSeconds * 1000) : '--:--'}
        </span>
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

      {/* 음성 분석 단독 제어 — OBS 없이도 켜고 끌 수 있게 둔다 */}
      <Button
        size="sm"
        variant={audio.status === 'STREAMING' ? 'secondary' : 'outline'}
        disabled={audio.status === 'CONNECTING'}
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

      {/* LAIVE 분석 세션 종료 */}
      <SessionControl sessionId={sessionId} />

      {/* OBS 연결 + 방송 송출(연동 시 음성 분석도 함께 시작/정지) */}
      <ObsControl onBroadcastStart={audio.start} onBroadcastStop={audio.stop} />

      {audio.error && (
        <span className="max-w-48 truncate text-xs text-destructive" title={audio.error}>
          {audio.error}
        </span>
      )}
    </div>
  )
}
