import { Radio, Users } from 'lucide-react'
import { Badge } from '@renderer/components/ui/badge'
import { Card, CardContent } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import Avatar from '@renderer/components/common/Avatar'
import { formatCount } from '@renderer/lib/format'
import { PLATFORM_LABEL } from '@renderer/types/portfolio'
import type { PortfolioSearchItem } from '@renderer/types/portfolioSearch'

const MAX_VISIBLE_TAGS = 3

interface PortfolioCardProps {
  item: PortfolioSearchItem
  onSelect: (publicSlug: string) => void
}

/** 검색 결과 카드. 전체가 클릭 영역이며 SC-10 공개 뷰로 이동한다. */
export default function PortfolioCard({ item, onSelect }: PortfolioCardProps): React.JSX.Element {
  const visibleTags = item.categoryTags.slice(0, MAX_VISIBLE_TAGS)
  const hiddenTagCount = item.categoryTags.length - visibleTags.length

  return (
    <Card
      role="button"
      tabIndex={0}
      onClick={() => onSelect(item.publicSlug)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onSelect(item.publicSlug)
        }
      }}
      className="cursor-pointer gap-0 py-5 transition-colors hover:border-primary/40 focus-visible:ring-[3px] focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      <CardContent className="flex flex-col gap-4 px-5">
        <div className="flex items-center gap-3">
          <Avatar src={item.profileImageUrl} alt={`${item.nickname} 프로필 이미지`} />
          <div className="flex min-w-0 flex-col gap-1">
            <span className="truncate text-subtitle font-bold tracking-[-0.01em]">
              {item.nickname}
            </span>
            <div className="flex flex-wrap gap-1">
              {item.platformAccounts.map((account) => (
                <Badge key={account.platformAccountId} variant="outline" className="font-medium">
                  {PLATFORM_LABEL[account.platform]}
                </Badge>
              ))}
            </div>
          </div>
        </div>

        {visibleTags.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {visibleTags.map((tag) => (
              <Badge
                key={tag}
                variant="outline"
                className="border-accent bg-accent text-accent-foreground"
              >
                {tag}
              </Badge>
            ))}
            {hiddenTagCount > 0 && (
              <Badge variant="outline" className="text-muted-foreground">
                +{hiddenTagCount}
              </Badge>
            )}
          </div>
        )}

        <div className="flex items-center gap-5 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <Users className="size-[14px]" strokeWidth={1.8} aria-hidden />
            평균{' '}
            <span className="font-mono font-semibold text-foreground">
              {formatCount(item.avgViewers)}
            </span>
          </span>
          <span className="flex items-center gap-1.5">
            <Radio className="size-[14px]" strokeWidth={1.8} aria-hidden />주{' '}
            <span className="font-mono font-semibold text-foreground">
              {formatCount(item.broadcastsPerWeek)}
            </span>
            회
          </span>
        </div>
      </CardContent>
    </Card>
  )
}

/** 로딩 중 그리드 자리를 지키는 스켈레톤. */
export function PortfolioCardSkeleton(): React.JSX.Element {
  return (
    <Card className="gap-0 py-5">
      <CardContent className="flex flex-col gap-4 px-5">
        <div className="flex items-center gap-3">
          <Skeleton className="size-12 shrink-0 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-5 w-16 rounded-full" />
          </div>
        </div>
        <Skeleton className="h-5 w-32" />
        <Skeleton className="h-4 w-40" />
      </CardContent>
    </Card>
  )
}
