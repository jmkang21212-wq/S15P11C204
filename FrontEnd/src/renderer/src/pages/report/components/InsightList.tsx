import { Lightbulb } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import type { ReportInsight } from '@renderer/types/report'

interface InsightListProps {
  insights: ReportInsight[]
}

/** AI 개선 제안 목록 (FR-T-04의 "개선 제안 3건"). */
export default function InsightList({ insights }: InsightListProps): React.JSX.Element {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">개선 제안</CardTitle>
      </CardHeader>
      <CardContent>
        {insights.length === 0 ? (
          <EmptyState
            icon={Lightbulb}
            title="개선 제안이 없어요"
            description="AI가 이번 방송에서 제안할 항목을 찾지 못했습니다."
          />
        ) : (
          <ol className="flex flex-col gap-4">
            {insights.map((insight, index) => (
              <li key={insight.insightId} className="flex gap-3">
                <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-md bg-accent font-mono text-xs font-bold text-accent-foreground">
                  {index + 1}
                </span>
                <div className="flex flex-col gap-1">
                  <p className="text-[13px] font-semibold">{insight.title}</p>
                  <p className="text-xs leading-relaxed text-muted-foreground">{insight.content}</p>
                </div>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  )
}
