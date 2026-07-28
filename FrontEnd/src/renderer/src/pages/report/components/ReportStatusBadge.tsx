import { Badge } from '@renderer/components/ui/badge'
import { cn } from '@renderer/lib/utils'
import type { ReportStatus } from '@renderer/types/report'

/**
 * 리포트 생성 상태 배지 — SC-06 목록/상세가 공유한다.
 *
 * design.md §6 상태 표현 규칙에 맞춰 완료=Success, 주의=Warning, 실패=Danger,
 * 진행 중=뉴트럴로 매핑한다. shadcn Badge에는 success/warning variant가 없어
 * 공용 파일을 수정하는 대신 토큰 클래스로 덧입힌다.
 */

const STATUS_STYLE: Record<ReportStatus, { label: string; className: string }> = {
  COMPLETED: {
    label: '분석 완료',
    className: 'border-success/25 bg-success/10 text-success'
  },
  PARTIAL: {
    label: '지표만 완료',
    className: 'border-warning/25 bg-warning/10 text-warning'
  },
  PROCESSING: {
    label: '분석 중',
    className: 'border-border bg-muted text-muted-foreground'
  },
  FAILED: {
    label: '분석 실패',
    className: 'border-destructive/25 bg-destructive/10 text-destructive'
  }
}

interface ReportStatusBadgeProps {
  status: ReportStatus
  className?: string
}

export default function ReportStatusBadge({
  status,
  className
}: ReportStatusBadgeProps): React.JSX.Element {
  const { label, className: statusClassName } = STATUS_STYLE[status]
  return (
    <Badge variant="outline" className={cn('font-semibold', statusClassName, className)}>
      {label}
    </Badge>
  )
}
