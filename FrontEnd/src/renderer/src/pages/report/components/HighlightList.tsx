import { useCallback, useEffect, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight, Film, Play } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import ClipThumbnail from '@renderer/components/common/ClipThumbnail'
import { formatClipLength, formatMs } from '@renderer/lib/format'
import type { ReportHighlight } from '@renderer/types/report'

/**
 * 하이라이트 후보 목록 — 가로 캐러셀.
 *
 * 사이드바(340px)에 있을 땐 세로로 쌓아야 했지만 본문 열로 내려오면서 폭이 두 배 가까이
 * 늘었다. 세로로 계속 쌓으면 카드 한 장이 화면을 다 먹으므로, 폭을 써서 옆으로 늘어놓고
 * 좌우 버튼으로 한 장씩 넘긴다.
 *
 * 휠·트랙패드로도 그대로 스크롤된다 — 버튼은 그 위에 얹은 보조 수단이고, 네이티브
 * 스크롤을 막지 않는다. 스크롤바만 숨긴다(globals.css의 .scrollbar-none).
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
  const scrollerRef = useRef<HTMLUListElement>(null)
  const [atStart, setAtStart] = useState(true)
  const [atEnd, setAtEnd] = useState(true)

  /**
   * 양 끝에 닿았는지 다시 계산해 버튼 비활성화를 정한다.
   * 스크롤 위치는 소수점으로 떨어져 끝에 닿아도 1px 가까이 남는 경우가 있어 여유를 둔다.
   */
  const updateEdges = useCallback((): void => {
    const el = scrollerRef.current
    if (!el) return
    setAtStart(el.scrollLeft <= 1)
    setAtEnd(el.scrollLeft >= el.scrollWidth - el.clientWidth - 1)
  }, [])

  // 창 폭이 바뀌면 보이는 개수가 달라져 "끝에 닿음" 여부도 같이 바뀐다.
  // 항목이 새로 들어오는 경우(클립 생성 완료 폴링)도 같은 이유로 다시 잰다.
  useEffect(() => {
    const el = scrollerRef.current
    if (!el) return
    updateEdges()
    const observer = new ResizeObserver(updateEdges)
    observer.observe(el)
    return () => observer.disconnect()
  }, [updateEdges, highlights.length])

  /**
   * 한 장씩 민다. 이동량은 클래스에 박은 값이 아니라 실제 DOM에서 잰다 —
   * 첫 항목과 둘째 항목의 offsetLeft 차이가 곧 "카드 폭 + 간격"이라, 나중에 폭이나
   * gap을 바꿔도 여기를 같이 고칠 필요가 없다.
   */
  const scrollByOne = (direction: -1 | 1): void => {
    const el = scrollerRef.current
    if (!el) return
    const [first, second] = el.children as unknown as (HTMLElement | undefined)[]
    const step =
      first && second
        ? second.offsetLeft - first.offsetLeft
        : (first?.offsetWidth ?? el.clientWidth)
    el.scrollBy({ left: step * direction, behavior: 'smooth' })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">하이라이트 후보</CardTitle>
        {highlights.length > 0 && (
          <CardAction className="flex items-center gap-1">
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              disabled={atStart}
              onClick={() => scrollByOne(-1)}
              aria-label="이전 하이라이트"
            >
              <ChevronLeft className="size-4" strokeWidth={1.8} aria-hidden />
            </Button>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              disabled={atEnd}
              onClick={() => scrollByOne(1)}
              aria-label="다음 하이라이트"
            >
              <ChevronRight className="size-4" strokeWidth={1.8} aria-hidden />
            </Button>
          </CardAction>
        )}
      </CardHeader>
      <CardContent>
        {highlights.length === 0 ? (
          <EmptyState
            icon={Film}
            title="하이라이트 후보가 없어요"
            description="이번 방송에서는 클립으로 만들 만한 구간을 찾지 못했습니다."
          />
        ) : (
          <ul
            ref={scrollerRef}
            onScroll={updateEdges}
            className="scrollbar-none flex snap-x snap-mandatory gap-4 overflow-x-auto"
          >
            {highlights.map((highlight) => {
              const range = `${formatMs(highlight.startMs)} – ${formatMs(highlight.endMs)}`
              const clipUrl = highlight.clipUrl
              return (
                <li
                  key={highlight.highlightId}
                  className="flex w-64 shrink-0 snap-start flex-col gap-2"
                >
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
