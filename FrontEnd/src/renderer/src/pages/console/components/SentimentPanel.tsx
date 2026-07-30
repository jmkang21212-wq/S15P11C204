import { formatCount, formatPercent } from '@renderer/lib/format'
import { topSentiments } from '@renderer/lib/sentiment'
import { cn } from '@renderer/lib/utils'
import type { Sentiment } from '@renderer/types/console'

/**
 * 콘솔 여론 패널 — 채팅 감정 8종 중 상위 3종을 보여준다.
 *
 * 백엔드는 8종 비율을 모두 내려주지만(명세 10.6) 좁은 패널에 8줄은 읽히지 않아
 * 상위 3종만 노출한다. 색은 긍정·중립·부정 방향으로 묶어 분위기를 한눈에 준다.
 *
 * 세션 리포트(SC-06)의 3분류 막대는 응답 구조가 달라 공용 SentimentBar를 계속 쓴다.
 */
export default function SentimentPanel({ sentiment }: { sentiment: Sentiment }): React.JSX.Element {
  const shares = topSentiments(sentiment)

  return (
    <div>
      <div className="mb-3 flex items-baseline justify-between">
        <span className="text-xs font-semibold text-muted-foreground">
          여론{' '}
          <span className="font-normal text-muted-foreground/70">
            · 최근 {sentiment.windowSec}초
          </span>
        </span>
        <span className="font-mono text-micro text-muted-foreground">
          분석 {formatCount(sentiment.analyzedChat)}건
        </span>
      </div>

      {shares.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {shares.map((share, index) => (
            <li key={share.label} className="flex items-center gap-2.5">
              <span
                className={cn(
                  'w-20 shrink-0 text-caption',
                  // 최다 감정만 강조한다 — 나머지는 참고 값이다.
                  index === 0 ? 'font-semibold text-foreground' : 'text-muted-foreground'
                )}
              >
                {share.text}
              </span>
              <span className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
                <span
                  className={cn('block h-full rounded-full', share.barClass)}
                  style={{ width: `${share.ratio * 100}%` }}
                />
              </span>
              <span className="w-9 shrink-0 text-right font-mono text-caption text-muted-foreground">
                {formatPercent(share.ratio)}
              </span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="rounded-lg bg-muted/40 px-3 py-4 text-center text-caption text-muted-foreground">
          아직 분석된 채팅이 없어요
        </p>
      )}

      {/* 호응도 = 유니크 채터 ÷ 시청자 (FR-F-05) */}
      {sentiment.engagement !== null && (
        <div className="mt-3 flex items-baseline justify-between border-t pt-3">
          <span className="text-caption text-muted-foreground">호응도</span>
          <span className="font-mono text-caption font-semibold">
            {formatPercent(sentiment.engagement)}
          </span>
        </div>
      )}
    </div>
  )
}
