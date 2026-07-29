import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Check, Copy, ExternalLink, Save } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Label } from '@renderer/components/ui/label'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { Switch } from '@renderer/components/ui/switch'
import { ErrorState } from '@renderer/components/common/StateView'
import PageHeader from '@renderer/components/common/PageHeader'
import { MetricCardSkeleton } from '@renderer/components/common/MetricCard'
import BasicInfoForm, { type BasicInfoValues } from './components/BasicInfoForm'
import FeaturedHighlightsEditor from './components/FeaturedHighlightsEditor'
import PerformanceSummary from './components/PerformanceSummary'
import ProfileSummary from './components/ProfileSummary'
import { fetchMyPortfolio, updateMyPortfolio } from '@renderer/api/portfolio'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { formatDateTime } from '@renderer/lib/format'
import { PATHS } from '@renderer/routes/paths'
import type { FeaturedHighlight, Portfolio } from '@renderer/types/portfolio'

/**
 * SC-10 포트폴리오 — 편집 화면.
 *
 * 공개 뷰는 `/p/:publicSlug`로 분리되어 있다(design.md §5.2 "브라우저 크롬을 씌운 비인증 뷰").
 * 태그와 publicSlug는 `PATCH /portfolio/me` 본문에 없어 읽기 전용으로만 보여준다.
 */

/** 공개 주소는 편집할 수 없고 안내·복사 용도로만 쓴다. */
function publicUrlOf(publicSlug: string): string {
  return `streampilot.tv/p/${publicSlug}`
}

type SaveState = 'idle' | 'saving' | 'saved' | 'error'

export default function PortfolioEditPage(): React.JSX.Element {
  const navigate = useNavigate()

  const loader = useCallback(() => fetchMyPortfolio(), [])
  const { data, loading, error, refetch } = useAsyncData(loader)

  const [form, setForm] = useState<BasicInfoValues | null>(null)
  const [isPublic, setIsPublic] = useState(false)
  const [highlights, setHighlights] = useState<FeaturedHighlight[]>([])
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [copied, setCopied] = useState(false)
  const [syncedFrom, setSyncedFrom] = useState<Portfolio | null>(null)

  /**
   * 조회가 끝나면 편집 상태를 서버 값으로 한 번 초기화한다.
   * effect가 아니라 렌더 중에 맞추는 편이 낫다 — effect에서 setState를 동기 호출하면
   * 불필요한 연쇄 렌더가 생기고(react-hooks/set-state-in-effect), React도 이 경우
   * "렌더 중 상태 조정"을 권한다. 같은 응답으로는 다시 덮어쓰지 않아 편집 중 값이 날아가지 않는다.
   */
  if (data !== null && data !== syncedFrom) {
    setSyncedFrom(data)
    setForm({
      title: data.title,
      introduction: data.introduction,
      contactEmail: data.contactEmail ?? '',
      contactUrl: data.contactUrl ?? ''
    })
    setIsPublic(data.isPublic)
    setHighlights(data.featuredHighlights)
  }

  const patchForm = (patch: Partial<BasicInfoValues>): void => {
    setForm((prev) => (prev === null ? prev : { ...prev, ...patch }))
    setSaveState('idle')
  }

  const handleSave = async (): Promise<void> => {
    if (form === null) return
    setSaveState('saving')
    try {
      await updateMyPortfolio({
        title: form.title,
        introduction: form.introduction,
        // 비워 두면 공개 뷰에 연락처를 표시하지 않도록 null로 보낸다.
        contactEmail: form.contactEmail.trim() === '' ? null : form.contactEmail.trim(),
        contactUrl: form.contactUrl.trim() === '' ? null : form.contactUrl.trim(),
        isPublic,
        // displayOrder는 화면 순서대로 다시 매긴다.
        featuredHighlights: highlights.map((h, i) => ({
          highlightId: h.highlightId,
          displayOrder: i + 1
        }))
      })
      setSaveState('saved')
    } catch {
      setSaveState('error')
    }
  }

  const copyUrl = (portfolio: Portfolio): void => {
    void navigator.clipboard.writeText(`https://${publicUrlOf(portfolio.publicSlug)}`)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1500)
  }

  if (loading) {
    return (
      <div className="mx-auto flex max-w-[1400px] flex-col gap-6 p-8">
        <Skeleton className="h-8 w-48" />
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <MetricCardSkeleton key={i} />
          ))}
        </div>
        <Skeleton className="h-[320px] w-full rounded-xl" />
      </div>
    )
  }

  if (error || !data || form === null) {
    return (
      <div className="mx-auto max-w-[1400px] p-8">
        <ErrorState message="포트폴리오를 불러오지 못했습니다." onRetry={refetch} />
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-[1400px] flex-col gap-6 p-8">
      <PageHeader
        title="포트폴리오"
        caption="광고주와 다른 스트리머에게 보여줄 공개 프로필을 관리하세요."
        actions={
          <div className="flex shrink-0 items-center gap-2">
            {saveState === 'saved' && (
              <span className="flex items-center gap-1 text-xs font-medium text-success">
                <Check className="size-[14px]" strokeWidth={2} aria-hidden />
                저장됨
              </span>
            )}
            {saveState === 'error' && (
              <span className="text-xs font-medium text-destructive">저장하지 못했어요</span>
            )}
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() =>
                navigate(PATHS.publicPortfolio.replace(':publicSlug', data.publicSlug))
              }
            >
              <ExternalLink className="size-[14px]" strokeWidth={1.8} aria-hidden />
              공개 뷰 보기
            </Button>
            <Button type="button" size="sm" onClick={handleSave} disabled={saveState === 'saving'}>
              <Save className="size-[14px]" strokeWidth={1.8} aria-hidden />
              {saveState === 'saving' ? '저장 중…' : '저장'}
            </Button>
          </div>
        }
      />

      {/* 2열: 편집 폼 + 사이드 340px (design.md §2) */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex min-w-0 flex-col gap-6">
          <section className="flex flex-col gap-3">
            <div className="flex items-baseline gap-2">
              <h2 className="text-sm font-semibold">방송 실적</h2>
              <span className="text-[11px] text-muted-foreground">
                자동 집계되며 직접 수정할 수 없어요
              </span>
            </div>
            <PerformanceSummary performance={data.performance} />
          </section>

          <BasicInfoForm values={form} onChange={patchForm} />

          <FeaturedHighlightsEditor
            selected={highlights}
            onChange={(next) => {
              setHighlights(next)
              setSaveState('idle')
            }}
          />
        </div>

        <aside className="flex flex-col gap-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-sm font-semibold">공개 설정</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <div className="flex items-center justify-between gap-3">
                <Label htmlFor="portfolio-public" className="flex flex-col items-start gap-0.5">
                  <span className="text-[13px] font-medium">포트폴리오 공개</span>
                  <span className="text-[11px] font-normal text-muted-foreground">
                    끄면 링크로도 볼 수 없어요
                  </span>
                </Label>
                <Switch
                  id="portfolio-public"
                  checked={isPublic}
                  onCheckedChange={(next) => {
                    setIsPublic(next)
                    setSaveState('idle')
                  }}
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <span className="text-[11px] font-medium text-muted-foreground">공개 주소</span>
                <div className="flex items-center gap-2">
                  <span className="min-w-0 flex-1 truncate rounded-md bg-muted px-2.5 py-1.5 font-mono text-[11px]">
                    {publicUrlOf(data.publicSlug)}
                  </span>
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    aria-label="공개 주소 복사"
                    onClick={() => copyUrl(data)}
                  >
                    {copied ? (
                      <Check className="size-[14px] text-success" strokeWidth={2} aria-hidden />
                    ) : (
                      <Copy className="size-[14px]" strokeWidth={1.8} aria-hidden />
                    )}
                  </Button>
                </div>
                <span className="text-[11px] text-muted-foreground">주소는 변경할 수 없어요.</span>
              </div>

              {data.publishedAt !== null && (
                <p className="text-[11px] text-muted-foreground">
                  공개 시작 <span className="font-mono">{formatDateTime(data.publishedAt)}</span>
                </p>
              )}
            </CardContent>
          </Card>

          <ProfileSummary profile={data.profile} tags={data.tags} />
        </aside>
      </div>
    </div>
  )
}
