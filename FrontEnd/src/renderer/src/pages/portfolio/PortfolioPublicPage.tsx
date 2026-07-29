import { useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { EyeOff, Film, Mail, Link2, Play } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Skeleton } from '@renderer/components/ui/skeleton'
import ClipThumbnail from '@renderer/components/common/ClipThumbnail'
import { EmptyState, ErrorState } from '@renderer/components/common/StateView'
import BrowserChrome from '@renderer/components/common/BrowserChrome'
import PerformanceSummary from './components/PerformanceSummary'
import ProfileSummary from './components/ProfileSummary'
import { fetchPublicPortfolio, PORTFOLIO_NOT_PUBLIC } from '@renderer/api/portfolio'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { openExternal } from '@renderer/lib/desktopBridge'
import { PATHS } from '@renderer/routes/paths'

/**
 * SC-10 포트폴리오 — 공개 뷰.
 *
 * GNB 없는 최상위 라우트이며, 브라우저 크롬을 씌워 "링크를 받은 사람에게 이렇게 보인다"를
 * 앱 안에서 확인하게 한다(design.md §5.2).
 *
 * 명세서 §12.4대로 편집용 상태(isPublic·publishedAt)는 응답에도 화면에도 없다.
 * SC-11 비즈니스 매칭에서 이 경로로 넘어오게 된다.
 */
export default function PortfolioPublicPage(): React.JSX.Element {
  const navigate = useNavigate()
  const { publicSlug = '' } = useParams<{ publicSlug: string }>()

  const loader = useCallback(() => fetchPublicPortfolio(publicSlug), [publicSlug])
  const { data, loading, error } = useAsyncData(loader)

  /**
   * SC-11 도입 전에는 "내 포트폴리오 편집 → 공개 뷰 보기"만 존재해 `/portfolio`로
   * 고정해도 맞았다. 이제 `/matching`·`/discover`에서 다른 스트리머를 보러 와도
   * 같은 화면을 거치므로(design.md §5.2), 고정 경로는 그 경우 자기 편집 화면으로
   * 잘못 튕긴다. 브라우저 히스토리로 돌아가고, 직접 링크로 들어와 히스토리가 없으면
   * "탐색"의 자연스러운 시작점인 매칭 화면으로 보낸다.
   */
  const goBackToApp = (): void => {
    if (window.history.length > 1) {
      navigate(-1)
    } else {
      navigate(PATHS.matching)
    }
  }

  return (
    <BrowserChrome url={`streampilot.tv/p/${publicSlug}`} onBack={goBackToApp}>
      {loading && (
        <div className="flex flex-col gap-6 p-8">
          <Skeleton className="h-16 w-64" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-[200px] w-full rounded-xl" />
        </div>
      )}

      {!loading && error && (
        <div className="flex h-full items-center justify-center p-8">
          {error.message === PORTFOLIO_NOT_PUBLIC ? (
            <EmptyState
              icon={EyeOff}
              title="비공개 포트폴리오예요"
              description="공개되지 않았거나 주소가 잘못되었습니다."
            />
          ) : (
            <ErrorState message="포트폴리오를 불러오지 못했습니다." />
          )}
        </div>
      )}

      {!loading && !error && data && (
        <article className="mx-auto flex max-w-[840px] flex-col gap-8 p-8">
          <header className="flex flex-col gap-4">
            <ProfileSummary profile={data.profile} tags={data.tags} variant="plain" />
            <h1 className="text-display font-extrabold tracking-[-0.03em]">{data.title}</h1>
            <p className="text-subtitle leading-relaxed text-muted-foreground">
              {data.introduction}
            </p>
          </header>

          <section className="flex flex-col gap-3">
            <h2 className="text-sm font-semibold">방송 실적</h2>
            <PerformanceSummary performance={data.performance} />
          </section>

          <section className="flex flex-col gap-3">
            <h2 className="text-sm font-semibold">대표 하이라이트</h2>
            {data.featuredHighlights.length === 0 ? (
              <EmptyState
                icon={Film}
                title="아직 등록된 클립이 없어요"
                className="rounded-lg bg-muted/40 py-8"
              />
            ) : (
              <ul className="grid grid-cols-1 gap-4 md:grid-cols-3">
                {data.featuredHighlights.map((highlight, index) => (
                  <li key={highlight.highlightId} className="flex flex-col gap-2">
                    <ClipThumbnail
                      src={highlight.thumbnailUrl}
                      alt={`대표 클립 ${index + 1} 썸네일`}
                    />
                    {highlight.clipUrl !== null && (
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => openExternal(highlight.clipUrl as string)}
                      >
                        <Play className="size-[14px]" strokeWidth={1.8} aria-hidden />
                        재생
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </section>

          {(data.contactEmail !== null || data.contactUrl !== null) && (
            <section className="flex flex-col gap-3 rounded-xl border bg-accent/40 p-6">
              <h2 className="text-sm font-semibold">비즈니스 문의</h2>
              <div className="flex flex-wrap items-center gap-2">
                {data.contactEmail !== null && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => openExternal(`mailto:${data.contactEmail}`)}
                  >
                    <Mail className="size-[14px]" strokeWidth={1.8} aria-hidden />
                    {data.contactEmail}
                  </Button>
                )}
                {data.contactUrl !== null && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => openExternal(data.contactUrl as string)}
                  >
                    <Link2 className="size-[14px]" strokeWidth={1.8} aria-hidden />
                    문의 링크
                  </Button>
                )}
              </div>
            </section>
          )}
        </article>
      )}
    </BrowserChrome>
  )
}
