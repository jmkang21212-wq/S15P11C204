import { useState } from 'react'
import { UserRound } from 'lucide-react'
import { cn } from '@renderer/lib/utils'

/**
 * 프로필 이미지 — SC-10 포트폴리오와 SC-11 검색 카드가 공유한다.
 * CSP(`img-src 'self' data:`)로 외부 URL이 차단될 수 있어 실패 시 사람 아이콘으로 폴백한다.
 */

interface AvatarProps {
  src: string | null
  alt: string
  /** Tailwind 크기 클래스. 기본은 size-12(48px). */
  size?: string
  className?: string
}

export default function Avatar({
  src,
  alt,
  size = 'size-12',
  className
}: AvatarProps): React.JSX.Element {
  const [failed, setFailed] = useState(false)

  if (src === null || failed) {
    return (
      <span
        className={cn(
          'flex shrink-0 items-center justify-center rounded-full bg-muted text-muted-foreground',
          size,
          className
        )}
        aria-label={alt}
      >
        <UserRound className="size-[55%]" strokeWidth={1.8} aria-hidden />
      </span>
    )
  }
  return (
    <img
      src={src}
      alt={alt}
      onError={() => setFailed(true)}
      className={cn('shrink-0 rounded-full bg-muted object-cover', size, className)}
    />
  )
}
