import { ArrowLeft, Film, RotateCcw } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import ReportStatusBadge from './ReportStatusBadge'
import { formatDateTime, formatDuration } from '@renderer/lib/format'
import type { ReportSession, ReportStatus } from '@renderer/types/report'

interface ReportHeaderProps {
  session: ReportSession
  status: ReportStatus
  /** 리포트 생성 완료 시각. 생성 전이면 null */
  generatedAt: string | null
  onBack: () => void
  onRefresh: () => void
  onOpenClips: () => void
  /** 재조회 중이면 새로고침 버튼을 잠근다 */
  refreshing?: boolean
}

/**
 * SC-06 상세 상단 헤더 — 세션 식별 정보와 화면 단위 액션.
 *
 * 자동 폴링은 하지 않기로 했으므로 진행 중 리포트는 여기 새로고침 버튼으로 다시 조회한다.
 */
export default function ReportHeader({
  session,
  status,
  generatedAt,
  onBack,
  onRefresh,
  onOpenClips,
  refreshing = false
}: ReportHeaderProps): React.JSX.Element {
  return (
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div className="flex min-w-0 items-start gap-3">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={onBack}
          aria-label="세션 리포트 목록으로"
          className="mt-0.5 shrink-0"
        >
          <ArrowLeft className="size-4.5" strokeWidth={1.8} aria-hidden />
        </Button>

        <div className="flex min-w-0 flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-title font-bold tracking-[-0.02em]">{session.title}</h1>
            <ReportStatusBadge status={status} />
          </div>
          <p className="text-xs text-muted-foreground">
            <span className="font-mono">{formatDateTime(session.startedAt)}</span>
            {' · '}
            <span className="font-mono">{formatDuration(session.startedAt, session.endedAt)}</span>
            {generatedAt && (
              <>
                {' · 리포트 생성 '}
                <span className="font-mono">{formatDateTime(generatedAt)}</span>
              </>
            )}
          </p>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onRefresh}
          disabled={refreshing}
          aria-label="리포트 새로고침"
        >
          <RotateCcw className="size-3.5" strokeWidth={1.8} aria-hidden />
          새로고침
        </Button>
        <Button type="button" size="sm" onClick={onOpenClips}>
          <Film className="size-3.5" strokeWidth={1.8} aria-hidden />
          클립 보러가기
        </Button>
      </div>
    </header>
  )
}
