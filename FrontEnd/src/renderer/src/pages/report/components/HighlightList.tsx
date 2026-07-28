import { useState } from 'react'
import { Film, Play } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import { formatClipLength, formatMs } from '@renderer/lib/format'
import type { ReportHighlight } from '@renderer/types/report'

/**
 * 하이라이트 후보 목록.
 *
 * 명세서 §11.1에 따라 점수·탐지 사유는 응답에 없으므로 표시하지 않는다.
 * 썸네일은 렌더러 CSP가 `default-src 'self'`라 외부 URL이 차단될 수 있어 실패 시 플레이스홀더로 대체한다.
 */

interface HighlightThumbnailProps {
  src: string
  alt: string
}

function HighlightThumbnail({ src, alt }: HighlightThumbnailProps): React.JSX.Element {
  const [failed, setFailed] = useState(false)

  if (failed) {
    return (
      <span
        className="flex aspect-video w-full items-center justify-center rounded-md bg-muted text-muted-foreground"
        aria-label={alt}
      >
        <Film className="size-[18px]" strokeWidth={1.8} aria-hidden />
      </span>
    )
  }

  return (
    <img
      src={src}
      alt={alt}
      onError={() => setFailed(true)}
      className="aspect-video w-full rounded-md bg-muted object-cover"
    />
  )
}

interface HighlightListProps {
  highlights: ReportHighlight[]
  /** 클립 재생 진입. 현재는 SC-07 클립 화면으로 이동한다. */
  onOpenClip: (highlight: ReportHighlight) => void
}

export default function HighlightList({
  highlights,
  onOpenClip
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
              return (
                <li key={highlight.highlightId} className="flex flex-col gap-2">
                  <HighlightThumbnail src={highlight.thumbnailUrl} alt={`${range} 구간 썸네일`} />
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex min-w-0 flex-col">
                      <span className="truncate font-mono text-xs font-semibold">{range}</span>
                      <span className="text-[11px] text-muted-foreground">
                        {formatClipLength(highlight.endMs - highlight.startMs)}
                      </span>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      // TODO: preload에 shell.openExternal 브리지가 생기면 clipUrl을 외부 플레이어로 연다
                      onClick={() => onOpenClip(highlight)}
                    >
                      <Play className="size-[14px]" strokeWidth={1.8} aria-hidden />
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
