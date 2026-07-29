import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
import { cn } from '@renderer/lib/utils'
import { formatTime } from '@renderer/lib/format'
import type { Briefing, BriefingType } from '@renderer/types/console'

const TYPE_META: Record<BriefingType, { label: string; badge: string }> = {
  RISK: { label: '위험 발언', badge: 'text-destructive bg-destructive/10' },
  CHAT_SUMMARY: { label: '채팅 요약', badge: 'text-cyan-700 bg-cyan-50' },
  FAQ: { label: '반복 질문', badge: 'text-violet-700 bg-violet-50' },
  PROMISE: { label: '약속 감지', badge: 'text-amber-700 bg-amber-50' }
}

function BriefingItem({ briefing }: { briefing: Briefing }): React.JSX.Element {
  const meta = TYPE_META[briefing.briefingType]
  const isHighRisk = briefing.briefingType === 'RISK' && briefing.level === 'HIGH'

  return (
    <li
      className={cn(
        'rounded-xl border bg-card p-4',
        isHighRisk && 'border-destructive/40 border-l-4 border-l-destructive bg-destructive/5'
      )}
    >
      <div className="mb-1.5 flex items-center gap-2">
        <span className={cn('rounded px-2 py-0.5 text-micro font-bold', meta.badge)}>
          {meta.label}
          {briefing.level ? ` · ${briefing.level}` : ''}
        </span>
        <span className="ml-auto font-mono text-micro text-muted-foreground">
          {formatTime(briefing.createdAt)}
        </span>
      </div>
      <p className="text-sm leading-relaxed">{briefing.message}</p>

      {/* PROMISE 감지 시 확정/무시 액션 */}
      {briefing.briefingType === 'PROMISE' && (
        // TODO: POST /promises/{id}/confirm · /dismiss
        <div className="mt-2.5 flex gap-2">
          <Button size="sm">약속 추가</Button>
          <Button size="sm" variant="outline">
            무시
          </Button>
        </div>
      )}
    </li>
  )
}

export default function BriefingFeed({ briefings }: { briefings: Briefing[] }): React.JSX.Element {
  return (
    <section className="flex flex-1 flex-col overflow-hidden p-5">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">브리핑 피드</span>
        <span className="text-micro text-muted-foreground">시간순 · 분당 최대 6건</span>
      </div>
      {briefings.length > 0 ? (
        <ul className="flex flex-col gap-2.5 overflow-y-auto">
          {briefings.map((briefing) => (
            <BriefingItem key={briefing.briefingId} briefing={briefing} />
          ))}
        </ul>
      ) : (
        <EmptyState title="아직 도착한 브리핑이 없어요" className="rounded-lg bg-muted/40 py-8" />
      )}
    </section>
  )
}
