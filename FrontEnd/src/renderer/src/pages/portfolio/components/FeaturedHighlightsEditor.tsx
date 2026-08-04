import { useCallback, useState } from 'react'
import { ArrowDown, ArrowUp, Check, Film, Plus, X } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Separator } from '@renderer/components/ui/separator'
import { Skeleton } from '@renderer/components/ui/skeleton'
import ClipThumbnail from '@renderer/components/common/ClipThumbnail'
import SessionSelect from '@renderer/components/common/SessionSelect'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import { fetchEndedSessions } from '@renderer/api/broadcastSessions'
import { fetchSessionHighlights } from '@renderer/api/highlights'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { formatMs } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import type { FeaturedHighlight } from '@renderer/types/portfolio'

/**
 * 대표 하이라이트 편집.
 *
 * 명세서에 "내 하이라이트 전체" 조회 API가 없어(세션별 조회뿐) 후보를 한 번에 가져올 수 없다.
 * 그래서 세션을 고르면 그 세션의 하이라이트가 후보로 나오고, 거기서 담는 방식으로 만들었다.
 * `displayOrder`는 저장 시 배열 순서로 다시 매기므로 여기서는 순서만 관리한다.
 */

interface FeaturedHighlightsEditorProps {
  selected: FeaturedHighlight[]
  onChange: (next: FeaturedHighlight[]) => void
}

export default function FeaturedHighlightsEditor({
  selected,
  onChange
}: FeaturedHighlightsEditorProps): React.JSX.Element {
  const [sessionId, setSessionId] = useState<number | null>(null)

  const sessionsLoader = useCallback(() => fetchEndedSessions(), [])
  const { data: sessions, loading: sessionsLoading } = useAsyncData(sessionsLoader)

  const candidatesLoader = useCallback(() => {
    if (sessionId === null) return Promise.resolve([])
    return fetchSessionHighlights(sessionId)
  }, [sessionId])
  const {
    data: candidates,
    loading: candidatesLoading,
    error: candidatesError,
    refetch: refetchCandidates
  } = useAsyncData(candidatesLoader)

  const isPicked = (highlightId: number): boolean =>
    selected.some((h) => h.highlightId === highlightId)

  const add = (highlightId: number, clipUrl: string | null, thumbnailUrl: string | null): void => {
    if (isPicked(highlightId)) return
    onChange([
      ...selected,
      { highlightId, displayOrder: selected.length + 1, clipUrl, thumbnailUrl }
    ])
  }

  const remove = (highlightId: number): void => {
    onChange(selected.filter((h) => h.highlightId !== highlightId))
  }

  const move = (index: number, direction: -1 | 1): void => {
    const target = index + direction
    if (target < 0 || target >= selected.length) return
    const next = [...selected]
    ;[next[index], next[target]] = [next[target], next[index]]
    onChange(next)
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">대표 하이라이트</CardTitle>
        <p className="text-caption text-muted-foreground">
          공개 포트폴리오 상단에 이 순서대로 보여집니다.
        </p>
      </CardHeader>

      <CardContent className="flex flex-col gap-6">
        {/* 담긴 목록 */}
        <div className="flex flex-col gap-3">
          <p className="text-xs font-medium">
            담은 클립 <span className="font-mono text-muted-foreground">{selected.length}</span>
          </p>

          {selected.length === 0 ? (
            <EmptyState
              icon={Film}
              title="아직 대표 클립이 없어요"
              description="아래에서 방송을 고르고 보여주고 싶은 클립을 담아 보세요."
              className="rounded-lg bg-muted/40 py-8"
            />
          ) : (
            <ul className="flex flex-col gap-2">
              {selected.map((highlight, index) => (
                <li
                  key={highlight.highlightId}
                  className="flex items-center gap-3 rounded-lg border p-2"
                >
                  <span className="w-5 shrink-0 text-center font-mono text-xs font-bold text-muted-foreground">
                    {index + 1}
                  </span>
                  <ClipThumbnail
                    src={highlight.thumbnailUrl}
                    alt={`대표 클립 ${index + 1} 썸네일`}
                    className="w-[96px] shrink-0"
                  />
                  <span className="flex-1 font-mono text-xs">#{highlight.highlightId}</span>
                  <div className="flex shrink-0 items-center gap-1">
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label="위로 이동"
                      disabled={index === 0}
                      onClick={() => move(index, -1)}
                    >
                      <ArrowUp className="size-[14px]" strokeWidth={1.8} aria-hidden />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label="아래로 이동"
                      disabled={index === selected.length - 1}
                      onClick={() => move(index, 1)}
                    >
                      <ArrowDown className="size-[14px]" strokeWidth={1.8} aria-hidden />
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      aria-label="제거"
                      onClick={() => remove(highlight.highlightId)}
                    >
                      <X className="size-[14px]" strokeWidth={1.8} aria-hidden />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>

        <Separator />

        {/* 후보 고르기 */}
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs font-medium">방송에서 클립 고르기</p>
            {sessions && sessions.length > 0 && (
              <SessionSelect
                sessions={sessions}
                value={sessionId}
                onChange={setSessionId}
                className="w-[280px]"
              />
            )}
          </div>

          {sessionsLoading && <Skeleton className="h-9 w-full" />}

          {!sessionsLoading && sessions?.length === 0 && (
            <p className="text-xs text-muted-foreground">아직 종료된 방송이 없어요.</p>
          )}

          {sessionId === null && !sessionsLoading && sessions && sessions.length > 0 && (
            <p className="text-xs text-muted-foreground">방송을 고르면 클립 후보가 나옵니다.</p>
          )}

          {sessionId !== null && candidatesLoading && (
            <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
              {Array.from({ length: 3 }, (_, i) => (
                <Skeleton key={i} className="aspect-video w-full rounded-md" />
              ))}
            </div>
          )}

          {sessionId !== null && !candidatesLoading && candidatesError && (
            <ErrorState message="클립을 불러오지 못했습니다." onRetry={refetchCandidates} />
          )}

          {sessionId !== null && !candidatesLoading && !candidatesError && candidates && (
            <>
              {candidates.length === 0 ? (
                <p className="text-xs text-muted-foreground">이 방송에는 클립이 없어요.</p>
              ) : (
                <ul className="grid grid-cols-2 gap-3 md:grid-cols-3">
                  {candidates.map((candidate) => {
                    const picked = isPicked(candidate.highlightId)
                    // 클립이 아직 만들어지지 않았으면 대표로 걸어도 재생할 수 없다.
                    const pending = candidate.clipUrl === null

                    return (
                      <li key={candidate.highlightId} className="flex flex-col gap-1.5">
                        <ClipThumbnail
                          src={candidate.thumbnailUrl}
                          alt={`${formatMs(candidate.startMs)} 구간 썸네일`}
                        />
                        <span className="font-mono text-caption">
                          {formatMs(candidate.startMs)} – {formatMs(candidate.endMs)}
                        </span>
                        <Button
                          type="button"
                          variant={picked ? 'secondary' : 'outline'}
                          size="sm"
                          disabled={picked || pending}
                          onClick={() =>
                            add(candidate.highlightId, candidate.clipUrl, candidate.thumbnailUrl)
                          }
                          className={cn('w-full', pending && 'text-muted-foreground')}
                        >
                          {picked ? (
                            <>
                              <Check className="size-[14px]" strokeWidth={1.8} aria-hidden />
                              담김
                            </>
                          ) : pending ? (
                            '생성 중'
                          ) : (
                            <>
                              <Plus className="size-[14px]" strokeWidth={1.8} aria-hidden />
                              담기
                            </>
                          )}
                        </Button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </>
          )}
        </div>
      </CardContent>
    </Card>
  )
}
