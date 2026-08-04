import { AlertCircle, RotateCcw, type LucideIcon } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { cn } from '@renderer/lib/utils'

/**
 * 공용 빈 상태 / 에러 상태 뷰.
 *
 * design.md §0 "로딩/빈 상태/에러 상태를 모두 구현한다"를 화면마다 새로 만들지 않도록 공용화했다.
 * 로딩은 화면마다 골격이 달라 각 컴포넌트에서 shadcn `Skeleton`을 직접 조합한다.
 */

interface EmptyStateProps {
  /** lucide 아이콘. 생략하면 아이콘 없이 텍스트만 표시한다. */
  icon?: LucideIcon
  title: string
  description?: string
  /** 다음 행동으로 유도하는 버튼 등 */
  action?: React.ReactNode
  className?: string
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className
}: EmptyStateProps): React.JSX.Element {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-3 px-6 py-12 text-center',
        className
      )}
    >
      {Icon && (
        <span className="flex size-11 items-center justify-center rounded-full bg-muted text-muted-foreground">
          <Icon className="size-[22px]" strokeWidth={1.8} aria-hidden />
        </span>
      )}
      <div className="flex flex-col gap-1">
        <p className="text-sm font-semibold">{title}</p>
        {description && <p className="text-xs text-muted-foreground">{description}</p>}
      </div>
      {action}
    </div>
  )
}

interface ErrorStateProps {
  title?: string
  message?: string
  onRetry?: () => void
  className?: string
}

export function ErrorState({
  title = '불러오지 못했어요',
  message = '잠시 후 다시 시도해 주세요.',
  onRetry,
  className
}: ErrorStateProps): React.JSX.Element {
  return (
    <div
      className={cn(
        'flex flex-col items-center justify-center gap-3 px-6 py-12 text-center',
        className
      )}
      role="alert"
    >
      <span className="flex size-11 items-center justify-center rounded-full bg-destructive/10 text-destructive">
        <AlertCircle className="size-[22px]" strokeWidth={1.8} aria-hidden />
      </span>
      <div className="flex flex-col gap-1">
        <p className="text-sm font-semibold">{title}</p>
        <p className="text-xs text-muted-foreground">{message}</p>
      </div>
      {onRetry && (
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          <RotateCcw className="size-[14px]" strokeWidth={1.8} aria-hidden />
          다시 시도
        </Button>
      )}
    </div>
  )
}
