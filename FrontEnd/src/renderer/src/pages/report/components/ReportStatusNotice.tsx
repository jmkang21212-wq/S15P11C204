import { AlertTriangle, RotateCcw } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Progress } from '@renderer/components/ui/progress'
import { formatDateTime, formatDuration } from '@renderer/lib/format'
import type { ReportSession } from '@renderer/types/report'

/**
 * 리포트 생성 상태 안내 UI 두 가지.
 *
 * - `ReportProcessingCard`: PROCESSING(202)일 때 상세 화면 전체를 대체한다.
 * - `PartialSummaryNotice`: PARTIAL일 때 AI 요약 카드 자리를 대체한다.
 *
 * 자동 폴링은 하지 않는다 — 사용자가 새로고침으로 다시 조회한다.
 */

interface ReportProcessingCardProps {
  session: ReportSession
  /** 0~1. 서버가 주지 않으면 불확정 표시로 둔다. */
  progress?: number
  onRefresh: () => void
  refreshing?: boolean
}

export function ReportProcessingCard({
  session,
  progress,
  onRefresh,
  refreshing = false
}: ReportProcessingCardProps): React.JSX.Element {
  const percent = progress != null ? Math.round(progress * 100) : null

  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-5 px-6 py-12 text-center">
        <div className="flex flex-col gap-1.5">
          <p className="text-sm font-semibold">방송 분석 중이에요</p>
          <p className="text-xs text-muted-foreground">
            보통 3분 정도 걸려요. 완료되면 새로고침해서 확인하세요.
          </p>
        </div>

        <div className="flex w-full max-w-[360px] flex-col gap-2">
          {/* 진행 중(클립 생성 등)은 뉴트럴 프로그레스 바 + 캡션 — design.md §6 */}
          <Progress value={percent ?? 12} />
          <p className="text-[11px] text-muted-foreground">
            <span className="font-mono">{formatDateTime(session.startedAt)}</span>
            {' · '}
            <span className="font-mono">{formatDuration(session.startedAt, session.endedAt)}</span>
            {percent != null && (
              <>
                {' · '}
                <span className="font-mono">{percent}%</span>
              </>
            )}
          </p>
        </div>

        <Button type="button" variant="outline" size="sm" onClick={onRefresh} disabled={refreshing}>
          <RotateCcw className="size-[14px]" strokeWidth={1.8} aria-hidden />
          새로고침
        </Button>
      </CardContent>
    </Card>
  )
}

/** AI 요약 생성이 실패했지만 집계 지표는 정상인 경우의 앰버 배너. */
export function PartialSummaryNotice(): React.JSX.Element {
  return (
    <div
      className="flex items-start gap-3 rounded-lg border border-warning/25 bg-warning/10 px-4 py-3"
      role="status"
    >
      <AlertTriangle
        className="mt-0.5 size-[18px] shrink-0 text-warning"
        strokeWidth={1.8}
        aria-hidden
      />
      <div className="flex flex-col gap-0.5">
        <p className="text-[13px] font-semibold text-warning">AI 요약을 생성하지 못했어요</p>
        <p className="text-xs text-muted-foreground">
          집계 지표와 시청자 추이는 정상입니다. 요약과 개선 제안은 다음 방송부터 다시 제공됩니다.
        </p>
      </div>
    </div>
  )
}
