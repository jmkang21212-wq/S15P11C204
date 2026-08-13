import { memo, useState } from 'react'
import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
import { cn } from '@renderer/lib/utils'
import { formatTime } from '@renderer/lib/format'
import { ApiError } from '@renderer/api/client'
import { confirmPromise, dismissPromise } from '@renderer/api/promise'
import type { Briefing, BriefingType } from '@renderer/types/console'
import type { PromiseStatus } from '@renderer/types/promise'

const TYPE_META: Record<BriefingType, { label: string; badge: string }> = {
  RISK: { label: '위험 발언', badge: 'text-destructive bg-destructive/10' },
  CHAT_SUMMARY: { label: '채팅 요약', badge: 'text-cyan-700 bg-cyan-50' },
  FAQ: { label: '반복 질문', badge: 'text-violet-700 bg-violet-50' },
  PROMISE: { label: '약속 감지', badge: 'text-amber-700 bg-amber-50' }
}

/**
 * 이미 처리된 약속에 붙는 문구. 여기 없는 상태(`DETECTED`)만 확정/무시 버튼을 보여준다.
 *
 * 상태별로 문구를 나눈 이유는 "처리됨" 한 마디보다 지금 어디까지 갔는지가 유용해서다 —
 * 캘린더에서 일정으로 확정했는지, 완료 처리했는지가 콘솔에서도 그대로 보인다.
 */
const RESOLVED_LABEL: Partial<Record<PromiseStatus, string>> = {
  PENDING: '약속으로 추가했어요',
  ON_HOLD: '보류했어요',
  SCHEDULED: '일정으로 확정했어요',
  COMPLETED: '완료했어요',
  DISMISSED: '무시했어요'
}

/** 약속 확정/무시 실패 시 사람이 읽을 메시지. 이미 처리된 건은 BE가 409로 구분해 알려준다. */
function promiseActionErrorMessage(e: unknown, fallback: string): string {
  if (e instanceof ApiError && e.code === 'PROMISE_NOT_DETECTED') {
    return '이미 처리된 약속이에요.'
  }
  return fallback
}

interface PromiseActionsProps {
  promiseId: number | undefined
  /**
   * 서버가 알려준 현재 약속 상태. 없으면(조회 범위를 벗어난 오래된 약속) 버튼을 보여주고,
   * 이미 처리된 건이면 BE가 409로 막아 준다.
   */
  status: PromiseStatus | undefined
  /** 확정·무시 성공 후 상태를 다시 읽게 한다. 화면을 떠났다 돌아와도 표시가 남는 근거. */
  onResolved: () => void
}

/**
 * PROMISE 브리핑 전용 확정/무시 액션.
 *
 * 처리 여부를 로컬 state만으로 기억하면 다른 화면에 갔다 오는 순간 콘솔이 언마운트되어
 * 버튼이 되살아난다. 그래서 판단 근거는 **서버 상태**로 두고, 로컬 state는 요청 직후의
 * 즉각 반응만 담당한다(낙관적 표시).
 */
function PromiseActions({ promiseId, status, onResolved }: PromiseActionsProps): React.JSX.Element {
  const [optimisticStatus, setOptimisticStatus] = useState<PromiseStatus | null>(null)
  const [pending, setPending] = useState<'confirm' | 'dismiss' | null>(null)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async (id: number): Promise<void> => {
    setPending('confirm')
    setError(null)
    try {
      await confirmPromise(id)
      setOptimisticStatus('PENDING')
      onResolved()
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
      setOptimisticStatus('DISMISSED')
      onResolved()
    } catch (e) {
      setError(promiseActionErrorMessage(e, '약속 무시에 실패했어요.'))
    } finally {
      setPending(null)
    }
  }

  const effectiveStatus = optimisticStatus ?? status
  const resolvedLabel = effectiveStatus ? RESOLVED_LABEL[effectiveStatus] : undefined

  if (resolvedLabel) {
    return <p className="mt-2.5 text-xs font-semibold text-muted-foreground">{resolvedLabel}</p>
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

interface BriefingItemProps {
  briefing: Briefing
  promiseStatuses: Map<number, PromiseStatus>
  onPromiseResolved: () => void
}

function BriefingItem({
  briefing,
  promiseStatuses,
  onPromiseResolved
}: BriefingItemProps): React.JSX.Element {
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
      {briefing.briefingType === 'PROMISE' && (
        <PromiseActions
          promiseId={briefing.promiseId}
          status={
            briefing.promiseId === undefined ? undefined : promiseStatuses.get(briefing.promiseId)
          }
          onResolved={onPromiseResolved}
        />
      )}
    </li>
  )
}

interface BriefingFeedProps {
  briefings: Briefing[]
  /** promiseId → 현재 상태. 처리 여부 표시가 화면 이동 후에도 남는 근거다. */
  promiseStatuses: Map<number, PromiseStatus>
  onPromiseResolved: () => void
}

function BriefingFeed({
  briefings,
  promiseStatuses,
  onPromiseResolved
}: BriefingFeedProps): React.JSX.Element {
  return (
    <section className="flex flex-1 flex-col overflow-hidden p-5">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">브리핑 피드</span>
        <span className="text-micro text-muted-foreground">시간순 · 분당 최대 6건</span>
      </div>
      {briefings.length > 0 ? (
        <ul className="flex flex-col gap-2.5 overflow-y-auto">
          {briefings.map((briefing) => (
            <BriefingItem
              key={briefing.briefingId}
              briefing={briefing}
              promiseStatuses={promiseStatuses}
              onPromiseResolved={onPromiseResolved}
            />
          ))}
        </ul>
      ) : (
        <EmptyState title="아직 도착한 브리핑이 없어요" className="rounded-lg bg-muted/40 py-8" />
      )}
    </section>
  )
}

/*
 * 3초 스냅샷 폴링이 도는 화면이라 memo로 감싼다. 폴링 응답은 매번 새로 파싱된 객체라
 * 값이 그대로여도 참조가 달라지는데, useLiveSnapshot이 바뀌지 않은 조각의 참조를
 * 유지해 주므로(hooks/useLiveSnapshot.ts) 여기서 memo가 실제로 걸린다.
 * 측정: perf/console-render.bench.tsx
 */
export default memo(BriefingFeed)
