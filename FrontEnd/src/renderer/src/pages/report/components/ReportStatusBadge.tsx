import StatusBadge, { type StatusTone } from '@renderer/components/common/StatusBadge'
import type { ReportStatus } from '@renderer/types/report'

/**
 * 리포트 생성 상태 배지 — SC-06 목록/상세가 공유한다.
 *
 * 색 규칙 자체는 components/common/StatusBadge가 소유한다. 여기는 ReportStatus를
 * tone·label로 옮기는 얇은 어댑터일 뿐이다 — SessionCard.tsx와 ReportHeader.tsx
 * 두 곳이 이 매핑을 필요로 해서 각자 복붙하지 않도록 한 곳에 둔다.
 */

const REPORT_STATUS: Record<ReportStatus, { label: string; tone: StatusTone }> = {
  COMPLETED: { label: '분석 완료', tone: 'success' },
  PARTIAL: { label: '지표만 완료', tone: 'warning' },
  PROCESSING: { label: '분석 중', tone: 'neutral' },
  FAILED: { label: '분석 실패', tone: 'danger' }
}

interface ReportStatusBadgeProps {
  /** 목록 응답은 reports를 LEFT JOIN해서 만들어져 리포트가 아직 없으면 null이다. */
  status: ReportStatus | null
  className?: string
}

export default function ReportStatusBadge({
  status,
  className
}: ReportStatusBadgeProps): React.JSX.Element {
  const { label, tone } =
    status === null ? { label: '대기 중', tone: 'neutral' as StatusTone } : REPORT_STATUS[status]
  return <StatusBadge tone={tone} label={label} className={className} />
}
