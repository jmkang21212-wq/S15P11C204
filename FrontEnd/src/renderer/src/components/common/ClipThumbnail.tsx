import { useState } from 'react'
import { Film } from 'lucide-react'
import { cn } from '@renderer/lib/utils'

/**
 * 클립·하이라이트 썸네일 — SC-06 리포트와 SC-07 클립이 공유한다.
 *
 * 두 가지 이유로 이미지가 없을 수 있어 항상 폴백을 준비한다.
 *  1) 클립 생성 전·실패면 `thumbnailUrl`이 null이다 (명세서 §1.5)
 *  2) 렌더러 CSP가 `img-src 'self' data:`라 외부 CDN 이미지는 차단된다
 */

interface ClipThumbnailProps {
  src: string | null
  alt: string
  className?: string
}

export default function ClipThumbnail({
  src,
  alt,
  className
}: ClipThumbnailProps): React.JSX.Element {
  const [failed, setFailed] = useState(false)

  if (src === null || failed) {
    return (
      <span
        className={cn(
          'flex aspect-video w-full items-center justify-center rounded-md bg-muted text-muted-foreground',
          className
        )}
        aria-label={alt}
      >
        <Film className="size-4.5" strokeWidth={1.8} aria-hidden />
      </span>
    )
  }

  return (
    <img
      src={src}
      alt={alt}
      onError={() => setFailed(true)}
      className={cn('aspect-video w-full rounded-md bg-muted object-cover', className)}
    />
  )
}
