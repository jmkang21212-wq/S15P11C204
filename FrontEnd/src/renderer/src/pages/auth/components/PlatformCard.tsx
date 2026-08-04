import { Check } from 'lucide-react'
import { cn } from '@renderer/lib/utils'

interface PlatformCardProps {
  name: string
  subLabel: string
  /** 로고 placeholder에 쓸 브랜드 색상 (실제 로고는 후속 적용) */
  color: string
  selected: boolean
  onSelect: () => void
  /** 백엔드가 아직 지원하지 않는 플랫폼 — 선택할 수 없고 "준비 중" 배지를 보여준다. */
  disabled?: boolean
}

/** 연동할 방송 플랫폼(치지직/SOOP) 선택 카드. */
export default function PlatformCard({
  name,
  subLabel,
  color,
  selected,
  onSelect,
  disabled = false
}: PlatformCardProps): React.JSX.Element {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onSelect}
      className={cn(
        'relative flex flex-1 flex-col items-start gap-2 rounded-xl border p-4 text-left transition-colors',
        disabled
          ? 'cursor-not-allowed border-border opacity-50'
          : selected
            ? 'border-primary bg-accent'
            : 'border-border hover:border-primary/40'
      )}
    >
      {selected && !disabled && (
        <span className="absolute top-2.5 right-2.5 flex size-4 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <Check className="size-3" strokeWidth={3} aria-hidden />
        </span>
      )}
      {disabled && (
        <span className="absolute top-2.5 right-2.5 rounded-full bg-muted px-1.5 py-0.5 text-micro font-semibold text-muted-foreground">
          준비 중
        </span>
      )}
      {/* 로고 placeholder */}
      <span className="size-6 rounded-md" style={{ backgroundColor: color }} aria-hidden />
      <span className="text-sm font-bold">{name}</span>
      <span className="text-xs text-muted-foreground">{subLabel}</span>
    </button>
  )
}
