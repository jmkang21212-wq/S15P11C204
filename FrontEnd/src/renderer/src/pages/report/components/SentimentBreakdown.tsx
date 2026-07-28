import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { formatPercent } from '@renderer/lib/format'
import type { ReportMetrics } from '@renderer/types/report'

/**
 * 채팅 감성 비율 — 사이드 열에 놓이는 좁은 폭 카드라
 * 도넛 대신 단일 스택 가로 바 + 범례로 읽기 쉽게 구성한다.
 */

interface SentimentBreakdownProps {
  metrics: ReportMetrics
}

export default function SentimentBreakdown({
  metrics
}: SentimentBreakdownProps): React.JSX.Element {
  const rows = [
    { key: 'positive', label: '긍정', ratio: metrics.positiveRatio, color: 'var(--success)' },
    {
      key: 'neutral',
      label: '중립',
      ratio: metrics.neutralRatio,
      color: 'var(--muted-foreground)'
    },
    { key: 'negative', label: '부정', ratio: metrics.negativeRatio, color: 'var(--destructive)' }
  ]

  const total = rows.reduce((sum, row) => sum + row.ratio, 0)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">채팅 감성 비율</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {total <= 0 ? (
          <p className="py-2 text-xs text-muted-foreground">감성 분석 데이터가 없습니다.</p>
        ) : (
          <>
            <div
              className="flex h-2.5 w-full overflow-hidden rounded-full bg-muted"
              role="img"
              aria-label={rows.map((row) => `${row.label} ${formatPercent(row.ratio)}`).join(', ')}
            >
              {rows.map((row) => (
                <span
                  key={row.key}
                  style={{ width: `${(row.ratio / total) * 100}%`, backgroundColor: row.color }}
                />
              ))}
            </div>

            <ul className="flex flex-col gap-2">
              {rows.map((row) => (
                <li key={row.key} className="flex items-center gap-2 text-xs">
                  <span
                    className="size-2 shrink-0 rounded-full"
                    style={{ backgroundColor: row.color }}
                    aria-hidden
                  />
                  <span className="flex-1 text-muted-foreground">{row.label}</span>
                  <span className="font-mono font-semibold">{formatPercent(row.ratio)}</span>
                </li>
              ))}
            </ul>
          </>
        )}
      </CardContent>
    </Card>
  )
}
