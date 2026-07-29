import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'
import { formatMs, formatCount } from '@renderer/lib/format'
import type { ConsoleServices, LiveSnapshotSession } from '@renderer/types/console'
import ObsControl from './ObsControl'
import SessionControl from './SessionControl'

interface ConsoleStatusBarProps {
  session: LiveSnapshotSession
  services: ConsoleServices
  onOpenMini: () => void
}

function ServiceDot({ label, ok }: { label: string; ok: boolean }): React.JSX.Element {
  return (
    <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
      <span className={cn('size-1.5 rounded-full', ok ? 'bg-success' : 'bg-muted-foreground')} />
      {label}
    </span>
  )
}

export default function ConsoleStatusBar({
  session,
  services,
  onOpenMini
}: ConsoleStatusBarProps): React.JSX.Element {
  return (
    <div className="flex items-center gap-5 border-b bg-card px-6 py-3">
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
      <div className="flex flex-col border-l pl-5">
        <span className="text-micro text-muted-foreground">현재 시청자</span>
        <span className="font-mono text-lg font-bold">{formatCount(session.currentViewers)}</span>
      </div>

      {/* 서비스 상태 */}
      <div className="ml-auto flex items-center gap-3 rounded-lg bg-muted px-3 py-1.5">
        <ServiceDot label="마이크" ok={services.microphone === 'CONNECTED'} />
        <ServiceDot label="STT" ok={services.stt === 'RUNNING'} />
        <ServiceDot label="AI" ok={services.ai === 'RUNNING'} />
      </div>

      {/* 미니 모드 = 항상-위 별도 창 열기 (IPC → main) */}
      <Button variant="outline" size="sm" onClick={onOpenMini}>
        미니 모드
      </Button>

      {/* LAIVE 분석 세션 종료 */}
      <SessionControl sessionId={session.sessionId} />

      {/* OBS 연결 + 송출 제어 */}
      <ObsControl />
    </div>
  )
}
