import { Skeleton } from '@renderer/components/ui/skeleton'
import ReportStatusBadge from './ReportStatusBadge'
import { formatCount, formatDateTime, formatDuration } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import type { BroadcastSessionListItem } from '@renderer/types/broadcastSession'

interface SessionRowProps {
  session: BroadcastSessionListItem
  onClick: (sessionId: number) => void
}

/**
 * SC-06 목록의 종료 세션 행. 클릭하면 해당 세션의 리포트 상세로 이동한다.
 *
 * 카드 그리드가 아니라 리스트 행인 이유: 세션은 `thumbnail_url`을 채우는 경로가 BE에
 * 아예 없어(항상 null) 카드마다 정보량 0인 16:9 회색 상자가 깔렸었다. 이 화면의 일은
 * "어느 방송을 복기할지 고르기"라 판단 근거는 제목·날짜·길이·분석 상태고, 그림이 아니다.
 */
export default function SessionRow({ session, onClick }: SessionRowProps): React.JSX.Element {
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => onClick(session.sessionId)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onClick(session.sessionId)
        }
      }}
      className={cn(
        'flex cursor-pointer items-center gap-4 px-5 py-4 transition-colors hover:bg-muted/40',
        'focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50'
      )}
    >
      <div className="min-w-0 flex-1">
        <h2 className="truncate text-subtitle font-semibold tracking-[-0.01em]">{session.title}</h2>
        <p className="mt-1 text-caption text-muted-foreground">
          {session.category !== null && <span>{session.category}</span>}
          {session.category !== null && ' · '}
          <span className="font-mono">{formatDateTime(session.startedAt)}</span>
          {' · '}
          <span className="font-mono">{formatDuration(session.startedAt, session.endedAt)}</span>
        </p>
      </div>

      <div className="hidden shrink-0 items-baseline gap-3 text-caption text-muted-foreground lg:flex">
        {session.avgViewers !== null && (
          <>
            평균{' '}
            <span className="font-mono text-foreground">{formatCount(session.avgViewers)}</span>
          </>
        )}
        {session.peakViewers !== null && (
          <>
            · 최고{' '}
            <span className="font-mono text-foreground">{formatCount(session.peakViewers)}</span>
          </>
        )}
      </div>

      <ReportStatusBadge status={session.reportStatus} className="shrink-0" />
    </div>
  )
}

/** 목록 로딩 중 자리를 지키는 스켈레톤 행. */
export function SessionRowSkeleton(): React.JSX.Element {
  return (
    <div className="flex items-center gap-4 px-5 py-4">
      <div className="min-w-0 flex-1 space-y-2">
        <Skeleton className="h-5 w-48" />
        <Skeleton className="h-3 w-64" />
      </div>
      <Skeleton className="hidden h-4 w-28 lg:block" />
      <Skeleton className="h-5 w-16 shrink-0 rounded-full" />
    </div>
  )
}
