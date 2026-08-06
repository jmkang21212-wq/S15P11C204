import { useCallback } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ErrorState } from '@renderer/components/common/StateView'
import { fetchTagCatalog } from '@renderer/api/tags'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { cn } from '@renderer/lib/utils'
import type { PortfolioTag } from '@renderer/types/portfolio'

/**
 * 태그 다중 선택.
 *
 * 카탈로그가 30개를 넘어 한 줄로 늘어놓으면 고르기 어렵다. `tagType`으로 묶어
 * 보여주는데, 이 값은 DB CHECK가 4종으로 못박아 둔 것이라 라벨을 상수로 둘 수 있다.
 *
 * 선택 순서를 유지한다 — 서버가 `tagIds` 순서를 `streamer_tags.display_order`로 저장하고,
 * 그 순서가 검색 카드의 태그 노출 순서가 된다(`ORDER BY st.display_order`).
 */

const TYPE_LABEL: Record<string, string> = {
  CONTENT: '콘텐츠',
  GAME: '게임',
  GENRE: '장르',
  STYLE: '스타일'
}

/** 카탈로그에 새 tagType이 생겨도 화면에서 사라지지 않게, 알려진 순서 뒤에 붙인다. */
const TYPE_ORDER = ['CONTENT', 'GAME', 'GENRE', 'STYLE']

function groupByType(tags: PortfolioTag[]): [string, PortfolioTag[]][] {
  const groups = new Map<string, PortfolioTag[]>()
  for (const tag of tags) {
    const group = groups.get(tag.tagType)
    if (group) group.push(tag)
    else groups.set(tag.tagType, [tag])
  }
  return [...groups.entries()].sort(
    ([a], [b]) =>
      (TYPE_ORDER.indexOf(a) + 1 || TYPE_ORDER.length + 1) -
      (TYPE_ORDER.indexOf(b) + 1 || TYPE_ORDER.length + 1)
  )
}

interface TagPickerProps {
  /** 선택된 tagId (고른 순서) */
  selected: number[]
  onChange: (next: number[]) => void
}

export default function TagPicker({ selected, onChange }: TagPickerProps): React.JSX.Element {
  const loader = useCallback(() => fetchTagCatalog(), [])
  const { data: tags, loading, error, refetch } = useAsyncData(loader)

  const toggle = (tagId: number): void => {
    onChange(
      selected.includes(tagId) ? selected.filter((id) => id !== tagId) : [...selected, tagId]
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-semibold">
          태그 <span className="font-normal text-muted-foreground">1개 이상</span>
        </CardTitle>
        <p className="text-caption text-muted-foreground">
          광고주가 태그로 스트리머를 찾습니다. 고른 순서대로 카드에 표시돼요.
        </p>
      </CardHeader>

      <CardContent className="flex flex-col gap-5">
        {loading && (
          <div className="flex flex-col gap-3">
            {Array.from({ length: 3 }, (_, i) => (
              <div key={i} className="flex flex-col gap-2">
                <Skeleton className="h-3 w-16" />
                <Skeleton className="h-8 w-full" />
              </div>
            ))}
          </div>
        )}

        {!loading && error && (
          <ErrorState message="태그 목록을 불러오지 못했습니다." onRetry={refetch} />
        )}

        {!loading && !error && tags && (
          <>
            {groupByType(tags).map(([type, group]) => (
              <div key={type} className="flex flex-col gap-2">
                <span className="text-caption font-medium text-muted-foreground">
                  {TYPE_LABEL[type] ?? type}
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {group.map((tag) => {
                    const picked = selected.includes(tag.tagId)
                    return (
                      <button
                        key={tag.tagId}
                        type="button"
                        aria-pressed={picked}
                        onClick={() => toggle(tag.tagId)}
                        className={cn(
                          'rounded-full border px-3 py-1 text-caption transition-colors',
                          picked
                            ? 'border-primary bg-primary/10 font-medium text-primary'
                            : 'text-muted-foreground hover:bg-muted'
                        )}
                      >
                        {tag.name}
                      </button>
                    )
                  })}
                </div>
              </div>
            ))}

            <p className="text-caption text-muted-foreground">
              선택 <span className="font-mono">{selected.length}</span>개
            </p>
          </>
        )}
      </CardContent>
    </Card>
  )
}
