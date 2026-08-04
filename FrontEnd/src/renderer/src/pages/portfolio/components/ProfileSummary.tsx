import { Badge } from '@renderer/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import Avatar from '@renderer/components/common/Avatar'
import { cn } from '@renderer/lib/utils'
import { PLATFORM_LABEL, type PortfolioProfile, type PortfolioTag } from '@renderer/types/portfolio'

/**
 * 프로필·플랫폼·태그 — 전부 읽기 전용이다.
 *
 * 프로필과 플랫폼 계정은 각각 `/streamers/me`, 플랫폼 연동(SC-01b)에서 관리하고,
 * 태그는 `PATCH /portfolio/me` 본문에 없어 이 화면에서 바꿀 수 없다.
 */

interface ProfileSummaryProps {
  profile: PortfolioProfile
  tags: PortfolioTag[]
  /** 공개 뷰에서는 카드 없이 헤더 형태로 쓴다. */
  variant?: 'card' | 'plain'
  className?: string
}

export default function ProfileSummary({
  profile,
  tags,
  variant = 'card',
  className
}: ProfileSummaryProps): React.JSX.Element {
  const body = (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3">
        <Avatar src={profile.profileImageUrl} alt={`${profile.nickname} 프로필 이미지`} />
        <div className="flex min-w-0 flex-col gap-1">
          <span className="truncate text-subtitle font-bold tracking-[-0.01em]">
            {profile.nickname}
          </span>
          <div className="flex flex-wrap gap-1.5">
            {profile.platformAccounts.map((account) => (
              <Badge key={account.platformAccountId} variant="outline" className="font-medium">
                {PLATFORM_LABEL[account.platform] ?? account.platform} · {account.channelName}
              </Badge>
            ))}
          </div>
        </div>
      </div>

      {tags.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {tags.map((tag) => (
            <Badge
              key={tag.tagId}
              variant="outline"
              className="border-accent bg-accent text-accent-foreground"
            >
              {tag.name}
            </Badge>
          ))}
        </div>
      )}
    </div>
  )

  if (variant === 'plain') {
    return <div className={className}>{body}</div>
  }

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">프로필</CardTitle>
        <p className="text-caption text-muted-foreground">
          프로필과 플랫폼 계정은 연동 화면에서, 태그는 별도 경로로 관리됩니다.
        </p>
      </CardHeader>
      <CardContent className={cn('flex flex-col gap-4')}>{body}</CardContent>
    </Card>
  )
}
