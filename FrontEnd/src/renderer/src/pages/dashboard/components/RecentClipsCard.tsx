import { Link } from 'react-router-dom'
import { Play } from 'lucide-react'
import { Card } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import ClipThumbnail from '@renderer/components/common/ClipThumbnail'
import { PATHS } from '@renderer/routes/paths'
import { formatMs } from '@renderer/lib/format'
import type { RecentHighlight } from '@renderer/types/dashboard'

interface RecentClipsCardProps {
  highlights: RecentHighlight[]
}

export default function RecentClipsCard({ highlights }: RecentClipsCardProps): React.JSX.Element {
  return (
    <Card className="gap-3 p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">최근 클립</span>
        <Link
          to={PATHS.clips}
          className="text-xs font-medium text-muted-foreground hover:text-foreground"
        >
          전체 보기 →
        </Link>
      </div>

      {highlights.length > 0 ? (
        <div className="grid grid-cols-5 gap-3">
          {highlights.map((clip) => (
            <div key={clip.highlightId} className="relative overflow-hidden rounded-lg border">
              <ClipThumbnail
                src={clip.thumbnailUrl}
                alt="클립 썸네일"
                className="h-20 w-full rounded-none"
              />
              <span className="absolute left-1/2 top-1/2 flex size-7 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-black/50 text-white">
                <Play className="size-3.5" aria-hidden />
              </span>
              <span className="absolute right-1.5 bottom-1.5 rounded bg-black/65 px-1.5 py-0.5 font-mono text-micro text-white">
                {formatMs(clip.durationMs)}
              </span>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState title="아직 생성된 클립이 없어요" className="rounded-lg bg-muted/40 py-8" />
      )}
    </Card>
  )
}
