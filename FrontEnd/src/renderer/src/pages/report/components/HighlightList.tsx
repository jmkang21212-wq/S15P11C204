import { Film, Play } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import ClipThumbnail from '@renderer/components/common/ClipThumbnail'
import { formatClipLength, formatMs } from '@renderer/lib/format'
import type { ReportHighlight } from '@renderer/types/report'

/**
 * 하이라이트 후보 목록.
 *
 * 명세서 §11.1에 따라 점수·탐지 사유는 응답에 없으므로 표시하지 않는다.
 * `clipUrl`이 null이면 아직 클립이 만들어지지 않은 상태라 재생할 수 없다.
 */

interface HighlightListProps {
  highlights: ReportHighlight[]
  /** 클립 재생. 기본 브라우저로 clipUrl을 연다. */
  onPlayClip: (clipUrl: string) => void
}

export default function HighlightList({
  highlights,
  onPlayClip
}: HighlightListProps): React.JSX.Element {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">하이라이트 후보</CardTitle>
      </CardHeader>
      <CardContent>
        {highlights.length === 0 ? (
          <EmptyState
            icon={Film}
            title="하이라이트 후보가 없어요"
            description="이번 방송에서는 클립으로 만들 만한 구간을 찾지 못했습니다."
          />
        ) : (
          <ul className="flex flex-col gap-4">
            {highlights.map((highlight) => {
              const range = `${formatMs(highlight.startMs)} – ${formatMs(highlight.endMs)}`
              const clipUrl = highlight.clipUrl
              return (
                <li key={highlight.highlightId} className="flex flex-col gap-2">
                  <ClipThumbnail src={highlight.thumbnailUrl} alt={`${range} 구간 썸네일`} />
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex min-w-0 flex-col">
                      <span className="truncate font-mono text-xs font-semibold">{range}</span>
                      <span className="text-caption text-muted-foreground">
                        {clipUrl === null
                          ? '클립 생성 중'
                          : formatClipLength(highlight.endMs - highlight.startMs)}
                      </span>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      disabled={clipUrl === null}
                      onClick={() => clipUrl !== null && onPlayClip(clipUrl)}
                    >
                      <Play className="size-3.5" strokeWidth={1.8} aria-hidden />
                      클립 열기
                    </Button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
