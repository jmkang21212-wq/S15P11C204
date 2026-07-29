import { Check } from 'lucide-react'
import { cn } from '@renderer/lib/utils'

interface StepperProps {
  steps: string[]
  /** 0-based 현재 단계 인덱스 */
  current: number
}

/** 온보딩 진행 단계 표시기. current 이전은 완료(✓), current는 활성, 이후는 대기. */
export default function Stepper({ steps, current }: StepperProps): React.JSX.Element {
  return (
    <div className="flex items-center">
      {steps.map((label, i) => {
        const completed = i < current
        const active = i === current
        return (
          <div key={label} className="flex flex-1 items-center last:flex-none">
            <div className="flex flex-col items-center gap-1.5">
              <span
                className={cn(
                  'flex size-7 items-center justify-center rounded-full text-xs font-bold',
                  completed && 'bg-success text-success-foreground',
                  active && 'bg-primary text-primary-foreground',
                  !completed && !active && 'border border-border bg-muted text-muted-foreground',
                )}
              >
                {completed ? <Check className="size-3.5" strokeWidth={3} aria-hidden /> : i + 1}
              </span>
              <span
                className={cn(
                  'text-caption',
                  active ? 'font-semibold text-primary' : 'text-muted-foreground',
                )}
              >
                {label}
              </span>
            </div>
            {i < steps.length - 1 && (
              <span className={cn('mx-1.5 mb-5 h-0.5 flex-1', completed ? 'bg-success' : 'bg-border')} />
            )}
          </div>
        )
      })}
    </div>
  )
}
