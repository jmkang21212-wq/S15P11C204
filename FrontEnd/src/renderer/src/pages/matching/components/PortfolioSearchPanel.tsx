import { useCallback, useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, Search, Users, X } from 'lucide-react'
import { Input } from '@renderer/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@renderer/components/ui/select'
import { Button } from '@renderer/components/ui/button'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import PortfolioCard, { PortfolioCardSkeleton } from './PortfolioCard'
import { searchPortfolios } from '@renderer/api/portfolioSearch'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import type { Platform } from '@renderer/types/portfolio'
import type { PortfolioSearchQuery, PortfolioSort } from '@renderer/types/portfolioSearch'

/**
 * 포트폴리오 검색 — SC-11의 핵심 UI. `/matching`(GNB)과 `/discover`(공개)가 그대로 공유한다.
 *
 * 명세서 §12.3 쿼리 파라미터(keyword·platform·tag·minAvgViewers·maxAvgViewers·sort·page·size)에
 * 1:1로 대응하는 필터를 둔다. 매칭 요청·추천 점수는 §1.2에서 이번 개정 제외로 명시돼
 * 여기 없다.
 */

const PAGE_SIZE = 20

const SORT_LABEL: Record<PortfolioSort, string> = {
  RECENT: '최신순',
  AVG_VIEWERS_DESC: '평균 시청자 많은순',
  BROADCASTS_DESC: '방송 많은순'
}

/** 필터 원본 상태. minAvgViewers 등은 입력 중 빈 문자열일 수 있어 문자열로 둔다. */
interface FilterState {
  keyword: string
  platform: Platform | 'ALL'
  tag: string
  minAvgViewers: string
  maxAvgViewers: string
  sort: PortfolioSort
}

const INITIAL_FILTERS: FilterState = {
  keyword: '',
  platform: 'ALL',
  tag: '',
  minAvgViewers: '',
  maxAvgViewers: '',
  sort: 'RECENT'
}

function toQuery(filters: FilterState, page: number): PortfolioSearchQuery {
  return {
    keyword: filters.keyword.trim() || undefined,
    platform: filters.platform === 'ALL' ? undefined : filters.platform,
    tag: filters.tag.trim() || undefined,
    minAvgViewers: filters.minAvgViewers === '' ? undefined : Number(filters.minAvgViewers),
    maxAvgViewers: filters.maxAvgViewers === '' ? undefined : Number(filters.maxAvgViewers),
    sort: filters.sort,
    page,
    size: PAGE_SIZE
  }
}

function isFilterActive(filters: FilterState): boolean {
  return (
    filters.keyword !== '' ||
    filters.platform !== 'ALL' ||
    filters.tag !== '' ||
    filters.minAvgViewers !== '' ||
    filters.maxAvgViewers !== '' ||
    filters.sort !== 'RECENT'
  )
}

interface PortfolioSearchPanelProps {
  onSelectPortfolio: (publicSlug: string) => void
}

export default function PortfolioSearchPanel({
  onSelectPortfolio
}: PortfolioSearchPanelProps): React.JSX.Element {
  const [keywordDraft, setKeywordDraft] = useState('')
  const [filters, setFilters] = useState<FilterState>(INITIAL_FILTERS)
  const [page, setPage] = useState(0)

  // 검색어는 타이핑마다 재조회하면 매 글자마다 로딩이 깜빡여 350ms 디바운스로 커밋한다.
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setFilters((prev) =>
        prev.keyword === keywordDraft ? prev : { ...prev, keyword: keywordDraft }
      )
    }, 350)
    return () => window.clearTimeout(timer)
  }, [keywordDraft])

  const patchFilter = (patch: Partial<FilterState>): void => {
    setFilters((prev) => ({ ...prev, ...patch }))
    setPage(0)
  }

  const resetFilters = (): void => {
    setKeywordDraft('')
    setFilters(INITIAL_FILTERS)
    setPage(0)
  }

  const loader = useCallback(() => searchPortfolios(toQuery(filters, page)), [filters, page])
  const { data, loading, error, refetch } = useAsyncData(loader)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end gap-3">
        <div className="relative min-w-55 flex-1">
          <Search
            className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
            strokeWidth={1.8}
            aria-hidden
          />
          <Input
            value={keywordDraft}
            onChange={(e) => setKeywordDraft(e.target.value)}
            placeholder="채널명 또는 태그로 검색"
            className="pl-9"
            aria-label="채널명 또는 태그 검색"
          />
        </div>

        <Select
          value={filters.platform}
          onValueChange={(v) => patchFilter({ platform: v as Platform | 'ALL' })}
        >
          <SelectTrigger className="w-30" aria-label="플랫폼 필터">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">전체 플랫폼</SelectItem>
            <SelectItem value="CHZZK">치지직</SelectItem>
            <SelectItem value="YOUTUBE">유튜브</SelectItem>
          </SelectContent>
        </Select>

        <Input
          value={filters.tag}
          onChange={(e) => patchFilter({ tag: e.target.value })}
          placeholder="태그"
          className="w-30"
          aria-label="태그 필터"
        />

        <div className="flex items-center gap-1.5">
          <Users className="size-4 text-muted-foreground" strokeWidth={1.8} aria-hidden />
          <Input
            type="number"
            min={0}
            value={filters.minAvgViewers}
            onChange={(e) => patchFilter({ minAvgViewers: e.target.value })}
            placeholder="최소"
            className="w-22.5 font-mono"
            aria-label="최소 평균 시청자"
          />
          <span className="text-muted-foreground">~</span>
          <Input
            type="number"
            min={0}
            value={filters.maxAvgViewers}
            onChange={(e) => patchFilter({ maxAvgViewers: e.target.value })}
            placeholder="최대"
            className="w-22.5 font-mono"
            aria-label="최대 평균 시청자"
          />
        </div>

        <Select
          value={filters.sort}
          onValueChange={(v) => patchFilter({ sort: v as PortfolioSort })}
        >
          <SelectTrigger className="w-42" aria-label="정렬 기준">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {(Object.keys(SORT_LABEL) as PortfolioSort[]).map((sort) => (
              <SelectItem key={sort} value={sort}>
                {SORT_LABEL[sort]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {isFilterActive(filters) && (
          <Button type="button" variant="ghost" size="sm" onClick={resetFilters}>
            <X className="size-3.5" strokeWidth={1.8} aria-hidden />
            필터 초기화
          </Button>
        )}
      </div>

      {loading && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 9 }, (_, i) => (
            <PortfolioCardSkeleton key={i} />
          ))}
        </div>
      )}

      {!loading && error && (
        <ErrorState message="스트리머 목록을 불러오지 못했습니다." onRetry={refetch} />
      )}

      {!loading && !error && data && (
        <>
          {data.items.length === 0 ? (
            <EmptyState
              icon={Search}
              title="조건에 맞는 스트리머가 없어요"
              description="검색어나 필터를 조정해 보세요."
            />
          ) : (
            <>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
                {data.items.map((item) => (
                  <PortfolioCard key={item.portfolioId} item={item} onSelect={onSelectPortfolio} />
                ))}
              </div>

              {data.totalPages > 1 && (
                <div className="flex items-center justify-center gap-3">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={page === 0}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    <ChevronLeft className="size-3.5" strokeWidth={1.8} aria-hidden />
                    이전
                  </Button>
                  <span className="font-mono text-xs text-muted-foreground">
                    페이지 {page + 1} / {data.totalPages}
                  </span>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={page + 1 >= data.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    다음
                    <ChevronRight className="size-3.5" strokeWidth={1.8} aria-hidden />
                  </Button>
                </div>
              )}
            </>
          )}
        </>
      )}
    </div>
  )
}
