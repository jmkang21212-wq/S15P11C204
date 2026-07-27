import { Check } from 'lucide-react'
import { cn } from '@renderer/lib/utils'

interface PlatformCardProps {
  name: string
  subLabel: string
  /** 로고 placeholder에 쓸 브랜드 색상 (실제 로고는 후속 적용) */
  color: string
  selected: boolean
  onSelect: () => void
}

/** 연동할 방송 플랫폼(치지직/SOOP) 선택 카드. */
export default function PlatformCard({
  name,
  subLabel,
  color,
  selected,
  onSelect,
}: PlatformCardProps): React.JSX.Element {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        'relative flex flex-1 flex-col items-start gap-2 rounded-xl border p-4 text-left transition-colors',
        selected ? 'border-primary bg-accent' : 'border-border hover:border-primary/40',
      )}
    >
      {selected && (
        <span className="absolute top-2.5 right-2.5 flex size-4 items-center justify-center rounded-full bg-primary text-primary-foreground">
          <Check className="size-3" strokeWidth={3} aria-hidden />
        </span>
      )}
      {/* 로고 placeholder */}
      <span className="size-6 rounded-md" style={{ backgroundColor: color }} aria-hidden />
      <span className="text-sm font-bold">{name}</span>
      <span className="text-xs text-muted-foreground">{subLabel}</span>
    </button>
  )
}
