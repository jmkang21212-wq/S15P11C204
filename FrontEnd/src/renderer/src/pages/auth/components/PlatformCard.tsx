import { Check, RefreshCw } from 'lucide-react'
import { cn } from '@renderer/lib/utils'

interface PlatformCardProps {
  name: string
  subLabel: string
  /** 로고 placeholder에 쓸 브랜드 색상 (실제 로고는 후속 적용) */
  color: string
  selected: boolean
  onSelect: () => void
  /** 이미 연동된 채널명 — 있으면 카드에 상태를 함께 보여준다. */
  connectedChannelName?: string | null
  /** 연동은 돼 있으나 토큰 재인증이 필요한 상태. */
  reauthRequired?: boolean
}

/** 연동할 방송 플랫폼(치지직/유튜브) 선택 카드. */
export default function PlatformCard({
  name,
  subLabel,
  color,
  selected,
  onSelect,
  connectedChannelName,
  reauthRequired = false
}: PlatformCardProps): React.JSX.Element {
  const connected = !reauthRequired && !!connectedChannelName

  return (
    <button
      type="button"
      onClick={onSelect}
      className={cn(
        'relative flex flex-1 flex-col items-start gap-2 rounded-xl border p-4 text-left transition-colors',
        selected ? 'border-primary bg-accent' : 'border-border hover:border-primary/40'
      )}
    >
      {connected && !selected && (
        <span className="absolute top-2.5 right-2.5 flex size-4 items-center justify-center rounded-full bg-success text-success-foreground">
          <Check className="size-3" strokeWidth={3} aria-hidden />
        </span>
      )}
      {reauthRequired && (
        <span className="absolute top-2.5 right-2.5 flex size-4 items-center justify-center rounded-full bg-warning text-warning-foreground">
          <RefreshCw className="size-2.5" strokeWidth={3} aria-hidden />
        </span>
      )}
      {/* 로고 placeholder */}
      <span className="size-6 rounded-md" style={{ backgroundColor: color }} aria-hidden />
      <span className="text-sm font-bold">{name}</span>
      <span className="text-xs text-muted-foreground">
        {reauthRequired ? '재연동 필요' : connected ? connectedChannelName : subLabel}
      </span>
    </button>
  )
}
