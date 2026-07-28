import { Users, TrendingUp } from 'lucide-react'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import ReportStatusBadge from './ReportStatusBadge'
import { formatCount, formatDateTime, formatDuration } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import type { EndedSessionSummary } from '@renderer/types/report'

interface SessionCardProps {
  session: EndedSessionSummary
  onClick: (sessionId: number) => void
}

/** SC-06 목록의 종료 세션 카드. 클릭하면 해당 세션의 리포트 상세로 이동한다. */
export default function SessionCard({ session, onClick }: SessionCardProps): React.JSX.Element {
  const pending = session.reportStatus === 'PROCESSING'
  const failed = session.reportStatus === 'FAILED'

  return (
    <Card
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
        'cursor-pointer gap-0 py-5 transition-colors hover:border-primary/40',
        'focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50'
      )}
    >
      <CardContent className="flex flex-col gap-3 px-5">
        <div className="flex items-start justify-between gap-2">
          <h2 className="line-clamp-2 text-[14px] font-semibold tracking-[-0.01em]">
            {session.title}
          </h2>
          <ReportStatusBadge status={session.reportStatus} className="shrink-0" />
        </div>

        <p className="text-[11px] text-muted-foreground">
          <span className="font-mono">{formatDateTime(session.startedAt)}</span>
          {' · '}
          <span className="font-mono">{formatDuration(session.startedAt, session.endedAt)}</span>
        </p>

        {pending || failed ? (
          <p className="text-xs text-muted-foreground">
            {pending ? '분석이 끝나면 지표가 표시됩니다.' : '분석에 실패해 지표가 없습니다.'}
          </p>
        ) : (
          <div className="flex items-center gap-5">
            <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <Users className="size-[14px]" strokeWidth={1.8} aria-hidden />
              평균{' '}
              <span className="font-mono font-semibold text-foreground">
                {formatCount(session.avgViewers)}
              </span>
            </span>
            <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <TrendingUp className="size-[14px]" strokeWidth={1.8} aria-hidden />
              최고{' '}
              <span className="font-mono font-semibold text-foreground">
                {formatCount(session.peakViewers)}
              </span>
            </span>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** 목록 로딩 중 자리를 지키는 스켈레톤 카드. */
export function SessionCardSkeleton(): React.JSX.Element {
  return (
    <Card className="gap-0 py-5">
      <CardContent className="flex flex-col gap-3 px-5">
        <div className="flex items-start justify-between gap-2">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-5 w-16 rounded-full" />
        </div>
        <Skeleton className="h-3 w-52" />
        <Skeleton className="h-4 w-32" />
      </CardContent>
    </Card>
  )
}
