import { Link } from 'react-router-dom'
import { Play } from 'lucide-react'
import { Card } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
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
            <div key={clip.highlightId} className="overflow-hidden rounded-lg border">
              {/* 썸네일 placeholder — 실제 이미지는 API 연동 후 clip.thumbnailUrl 사용 */}
              <div className="relative flex h-20 items-center justify-center bg-gradient-to-br from-muted to-accent">
                <span className="flex size-7 items-center justify-center rounded-full bg-black/50 text-white">
                  <Play className="size-3.5" aria-hidden />
                </span>
                <span className="absolute right-1.5 bottom-1.5 rounded bg-black/65 px-1.5 py-0.5 font-mono text-[10px] text-white">
                  {formatMs(clip.durationMs)}
                </span>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState title="아직 생성된 클립이 없어요" className="rounded-lg bg-muted/40 py-8" />
      )}
    </Card>
  )
}
