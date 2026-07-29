import { Badge } from '@renderer/components/ui/badge'
import { cn } from '@renderer/lib/utils'

/**
 * 상태 배지 — 완료/진행 중/주의/실패 등 도메인 상태를 표시하는 모든 화면이 공유한다.
 *
 * shadcn Badge에는 success/warning variant가 없어(components/ui/badge.tsx는 손대지 않는다 —
 * `shadcn add badge`를 다시 돌리면 사라지므로) tone→클래스 맵을 여기서 소유한다.
 * "배지를 틴트가 아니라 솔리드로" 같은 시스템 변경은 이 파일 하나만 고치면 된다.
 *
 * 도메인 상태(ReportStatus·GoalStatus 등)는 직접 받지 않는다 — 그러면 이 컴포넌트가
 * 특정 화면의 타입을 알게 되어 서로 결합된다. tone·label만 받고, 도메인→tone 매핑은
 * 각 화면(또는 그 화면의 얇은 어댑터)이 갖는다.
 */

export type StatusTone = 'success' | 'warning' | 'danger' | 'neutral'

const TONE_CLASS: Record<StatusTone, string> = {
  success: 'border-success/25 bg-success/10 text-success',
  warning: 'border-warning/25 bg-warning/10 text-warning',
  danger: 'border-destructive/25 bg-destructive/10 text-destructive',
  neutral: 'border-border bg-muted text-muted-foreground'
}

interface StatusBadgeProps {
  tone: StatusTone
  label: string
  className?: string
}

export default function StatusBadge({ tone, label, className }: StatusBadgeProps): React.JSX.Element {
  return (
    <Badge variant="outline" className={cn('font-semibold', TONE_CLASS[tone], className)}>
      {label}
    </Badge>
  )
}
