import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'
import { formatMs, formatCount } from '@renderer/lib/format'
import type { LiveSnapshotSession } from '@renderer/types/console'
import type { UseAudioStreamResult } from '@renderer/hooks/useAudioStream'
import ObsControl from './ObsControl'
import SessionControl from './SessionControl'

interface ConsoleStatusBarProps {
  session: LiveSnapshotSession
  onOpenMini: () => void
  /** 음성 스트리밍 — 마이크·STT·AI 상태와 방송 시작/종료 연동에 함께 쓴다. */
  audio: UseAudioStreamResult
}

function ServiceDot({
  label,
  ok,
  pending
}: {
  label: string
  ok: boolean
  pending: boolean
}): React.JSX.Element {
  return (
    <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
      <span
        className={cn(
          'size-1.5 rounded-full',
          ok ? 'bg-success' : pending ? 'bg-warning' : 'bg-muted-foreground'
        )}
      />
      {label}
    </span>
  )
}

export default function ConsoleStatusBar({
  session,
  onOpenMini,
  audio
}: ConsoleStatusBarProps): React.JSX.Element {
  // 마이크·STT·AI는 모두 음성 스트리밍 파이프라인이 살아있을 때만 동작한다.
  const streaming = audio.status === 'STREAMING'
  const pending = audio.status === 'CONNECTING'

  return (
    <div className="flex items-center gap-4 border-b bg-card px-6 py-3">
      {/* 상태 + 경과 시간 */}
      <div className="flex items-center gap-2.5">
        <span
          className={cn(
            'rounded px-2 py-0.5 text-micro font-bold tracking-wide text-white',
            session.status === 'PREPARING' ? 'bg-warning' : 'bg-destructive'
          )}
        >
          {session.status === 'PREPARING' ? '● 준비 중' : '● LIVE'}
        </span>
        <span className="font-mono text-xl font-bold">
          {formatMs(session.elapsedSeconds * 1000)}
        </span>
      </div>

      {/* 현재 시청자 */}
      <div className="flex flex-col border-l pl-4">
        <span className="text-micro text-muted-foreground">현재 시청자</span>
        <span className="font-mono text-lg font-bold">{formatCount(session.currentViewers)}</span>
      </div>

      {/* 서비스 상태 — 실제 음성 스트리밍 상태를 반영한다 */}
      <div className="ml-auto flex items-center gap-3 rounded-lg bg-muted px-3 py-1.5">
        <ServiceDot label="마이크" ok={streaming} pending={pending} />
        <ServiceDot label="STT" ok={streaming} pending={pending} />
        <ServiceDot label="AI" ok={streaming} pending={pending} />
      </div>

      {/* 음성 분석 단독 제어 — OBS 없이도 켜고 끌 수 있게 둔다 */}
      <Button
        size="sm"
        variant={streaming ? 'secondary' : 'outline'}
        disabled={pending}
        onClick={streaming ? audio.stop : audio.start}
      >
        {pending ? '연결 중…' : streaming ? '음성 분석 중지' : '음성 분석 시작'}
      </Button>

      {/* 미니 모드 = 항상-위 별도 창 열기 (IPC → main) */}
      <Button variant="outline" size="sm" onClick={onOpenMini}>
        미니 모드
      </Button>

      {/* LAIVE 분석 세션 종료 */}
      <SessionControl sessionId={session.sessionId} />

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
