import { useState } from 'react'
import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
import { cn } from '@renderer/lib/utils'
import { formatTime } from '@renderer/lib/format'
import { ApiError } from '@renderer/api/client'
import { confirmPromise, dismissPromise } from '@renderer/api/promise'
import type { Briefing, BriefingType } from '@renderer/types/console'

const TYPE_META: Record<BriefingType, { label: string; badge: string }> = {
  RISK: { label: '위험 발언', badge: 'text-destructive bg-destructive/10' },
  CHAT_SUMMARY: { label: '채팅 요약', badge: 'text-cyan-700 bg-cyan-50' },
  FAQ: { label: '반복 질문', badge: 'text-violet-700 bg-violet-50' },
  PROMISE: { label: '약속 감지', badge: 'text-amber-700 bg-amber-50' }
}

type Resolution = 'confirmed' | 'dismissed'

/** 약속 확정/무시 실패 시 사람이 읽을 메시지. 이미 처리된 건은 BE가 409로 구분해 알려준다. */
function promiseActionErrorMessage(e: unknown, fallback: string): string {
  if (e instanceof ApiError && e.code === 'PROMISE_NOT_DETECTED') {
    return '이미 처리된 약속이에요.'
  }
  return fallback
}

/** PROMISE 브리핑 전용 확정/무시 액션. 카드마다 독립적이라 상태를 로컬로 둔다. */
function PromiseActions({ promiseId }: { promiseId: number | undefined }): React.JSX.Element {
  const [resolution, setResolution] = useState<Resolution | null>(null)
  const [pending, setPending] = useState<'confirm' | 'dismiss' | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async (id: number): Promise<void> => {
    setPending('confirm')
    setError(null)
    try {
      await confirmPromise(id)
      setResolution('confirmed')
    } catch (e) {
      setError(promiseActionErrorMessage(e, '약속 확정에 실패했어요.'))
    } finally {
      setPending(null)
    }
  }

  const handleDismiss = async (id: number): Promise<void> => {
    setPending('dismiss')
    setError(null)
    try {
      await dismissPromise(id)
      setResolution('dismissed')
    } catch (e) {
      setError(promiseActionErrorMessage(e, '약속 무시에 실패했어요.'))
    } finally {
      setPending(null)
    }
  }

  if (resolution) {
    return (
      <p className="mt-2.5 text-xs font-semibold text-muted-foreground">
        {resolution === 'confirmed' ? '약속으로 추가했어요' : '무시했어요'}
      </p>
    )
  }

  // AI 서버가 Spring 저장에 실패했거나 저장 자체가 꺼져 있으면 promise_id가 없다
  // (AI/FRONTEND_API.md §5) — 가리킬 행이 없어 확정/무시를 붙일 수 없다.
  if (promiseId === undefined) {
    return (
      <p className="mt-2.5 text-micro text-muted-foreground">
        이 약속은 저장에 실패해 확정/무시할 수 없어요.
      </p>
    )
  }

  return (
    <div className="mt-2.5">
      <div className="flex gap-2">
        <Button size="sm" disabled={pending !== null} onClick={() => void handleConfirm(promiseId)}>
          {pending === 'confirm' ? '처리 중…' : '약속 추가'}
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={pending !== null}
          onClick={() => void handleDismiss(promiseId)}
        >
          {pending === 'dismiss' ? '처리 중…' : '무시'}
        </Button>
      </div>
      {error && <p className="mt-1.5 text-micro text-destructive">{error}</p>}
    </div>
  )
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

      {/* 근거가 된 발화 원문 */}
      {briefing.quote && (
        <p className="mt-1.5 border-l-2 pl-2.5 text-xs leading-relaxed text-muted-foreground">
          “{briefing.quote}”
        </p>
      )}

      {/* 판정 근거 · 권장 조치 */}
      {briefing.advice && <p className="mt-1.5 text-xs text-muted-foreground">{briefing.advice}</p>}

      {/* PROMISE 감지 시 확정/무시 액션 */}
      {briefing.briefingType === 'PROMISE' && <PromiseActions promiseId={briefing.promiseId} />}
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
