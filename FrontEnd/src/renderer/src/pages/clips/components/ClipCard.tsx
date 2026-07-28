import { Download, Play } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Progress } from '@renderer/components/ui/progress'
import { Skeleton } from '@renderer/components/ui/skeleton'
import ClipThumbnail from '@renderer/components/common/ClipThumbnail'
import { formatClipLength, formatMs } from '@renderer/lib/format'
import type { ReportHighlight } from '@renderer/types/report'

/**
 * 클립 카드 — 완료 / 생성 중 두 상태를 가진다.
 *
 * `clipUrl`이 null이면 아직 클립 파일이 없다. 응답에 "생성 중"과 "생성 실패"를
 * 구분할 필드가 없어(명세서 §1.5) 둘 다 생성 중으로 표시한다.
 * 진행 중 표현은 design.md §6에 따라 뉴트럴 프로그레스 바 + 캡션이다.
 */

interface ClipCardProps {
  highlight: ReportHighlight
  onPlay: (clipUrl: string) => void
  onDownload: (clipUrl: string) => void
}

export default function ClipCard({
  highlight,
  onPlay,
  onDownload
}: ClipCardProps): React.JSX.Element {
  const range = `${formatMs(highlight.startMs)} – ${formatMs(highlight.endMs)}`
  const clipUrl = highlight.clipUrl
  const pending = clipUrl === null

  return (
    <Card className="gap-0 py-4">
      <CardContent className="flex flex-col gap-3 px-4">
        <ClipThumbnail src={highlight.thumbnailUrl} alt={`${range} 구간 썸네일`} />

        <div className="flex flex-col gap-0.5">
          <span className="font-mono text-[13px] font-semibold">{range}</span>
          <span className="text-[11px] text-muted-foreground">
            {formatClipLength(highlight.endMs - highlight.startMs)}
          </span>
        </div>

        {pending ? (
          <div className="flex flex-col gap-1.5">
            {/*
              design.md §6 "진행 중은 뉴트럴 프로그레스 바 + 캡션".
              shadcn Progress는 기본이 Primary 색이고 value가 없으면 빈 막대로 보이므로,
              벤더 파일을 고치는 대신 여기서 뉴트럴 색 + 진행감(pulse)만 입힌다.
              진행률을 모르는 상태라 수치는 표시하지 않는다.
            */}
            <Progress
              value={100}
              aria-label="클립 생성 중"
              className="animate-pulse bg-muted [&>[data-slot=progress-indicator]]:bg-muted-foreground/40"
            />
            <span className="text-[11px] text-muted-foreground">클립을 만드는 중이에요</span>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="flex-1"
              onClick={() => onPlay(clipUrl)}
            >
              <Play className="size-[14px]" strokeWidth={1.8} aria-hidden />
              재생
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="flex-1"
              onClick={() => onDownload(clipUrl)}
            >
              <Download className="size-[14px]" strokeWidth={1.8} aria-hidden />
              다운로드
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** 로딩 중 그리드 자리를 지키는 스켈레톤. */
export function ClipCardSkeleton(): React.JSX.Element {
  return (
    <Card className="gap-0 py-4">
      <CardContent className="flex flex-col gap-3 px-4">
        <Skeleton className="aspect-video w-full rounded-md" />
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-3 w-16" />
        </div>
        <Skeleton className="h-8 w-full" />
      </CardContent>
    </Card>
  )
}
