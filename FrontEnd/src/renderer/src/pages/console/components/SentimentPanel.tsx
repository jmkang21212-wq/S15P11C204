import SentimentBar from '@renderer/components/common/SentimentBar'
import { formatPercent } from '@renderer/lib/format'
import type { Sentiment } from '@renderer/types/console'

export default function SentimentPanel({ sentiment }: { sentiment: Sentiment }): React.JSX.Element {
  const { positiveRatio, neutralRatio, negativeRatio } = sentiment
  return (
    <div>
      <div className="mb-3 text-xs font-semibold text-muted-foreground">
        여론 <span className="font-normal text-muted-foreground/70">· 실시간</span>
      </div>
      <SentimentBar
        positiveRatio={positiveRatio}
        neutralRatio={neutralRatio}
        negativeRatio={negativeRatio}
        className="mb-2.5 h-3"
      />
      <div className="flex justify-between text-caption">
        <span className="font-semibold text-success">긍정 {formatPercent(positiveRatio)}</span>
        <span className="text-muted-foreground">중립 {formatPercent(neutralRatio)}</span>
        <span className="font-semibold text-destructive">부정 {formatPercent(negativeRatio)}</span>
      </div>
    </div>
  )
}
