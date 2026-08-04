import { useState } from 'react'
import { Plus } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Label } from '@renderer/components/ui/label'
import { Switch } from '@renderer/components/ui/switch'
import { ApiError } from '@renderer/api/client'
import { createMyPortfolio } from '@renderer/api/portfolio'
import BasicInfoForm, { type BasicInfoValues } from './BasicInfoForm'
import FeaturedHighlightsEditor from './FeaturedHighlightsEditor'
import TagPicker from './TagPicker'
import type { FeaturedHighlight } from '@renderer/types/portfolio'

/**
 * 포트폴리오 생성 폼.
 *
 * 백엔드가 자동 구성을 하지 않으므로(`PortfolioCommandService.create`) 이 화면이 없으면
 * 포트폴리오를 만들 방법이 아예 없다. 그래서 편집 화면과 같은 조각(기본 정보·하이라이트)을
 * 재사용하고, 생성에만 필요한 태그 선택과 공개 여부를 더했다.
 *
 * 편집 화면과 달리 태그를 여기서 고른다 — `tagIds`가 생성 필수값이고, 만든 뒤로는
 * 프로필 화면이 태그를 관리한다(저장 원천이 `streamer_tags` 한 곳이라 갈라지지 않는다).
 */

const INITIAL_INFO: BasicInfoValues = {
  title: '',
  introduction: '',
  contactEmail: '',
  contactUrl: ''
}

/** 서버 검증에 걸리기 전에 화면에서 먼저 막는 두 조건 — 필수값이 비어 있으면 알려준다. */
function validate(info: BasicInfoValues, tagIds: number[]): string | null {
  if (info.title.trim() === '') return '제목을 입력해 주세요.'
  if (tagIds.length === 0) return '태그를 1개 이상 골라 주세요.'
  return null
}

/** 서버가 코드로 구분해 주는 실패만 문구를 따로 준다. 나머지는 서버 메시지를 그대로 쓴다. */
function messageOf(error: unknown): string {
  if (!(error instanceof ApiError)) return '포트폴리오를 만들지 못했어요.'
  if (error.code === 'PORTFOLIO_NOT_ELIGIBLE') {
    return '종료된 방송이 1개 이상 있어야 만들 수 있어요.'
  }
  if (error.code === 'PORTFOLIO_ALREADY_EXISTS') {
    return '이미 포트폴리오가 있어요. 새로고침하면 편집 화면으로 넘어가요.'
  }
  if (error.code === 'PORTFOLIO_HIGHLIGHT_INVALID') {
    return '고른 클립 중 사용할 수 없는 것이 있어요. 다시 골라 주세요.'
  }
  return error.message
}

interface PortfolioCreateFormProps {
  /** 생성 성공 — 페이지가 내 포트폴리오를 다시 조회해 편집 화면으로 넘어간다. */
  onCreated: () => void
}

export default function PortfolioCreateForm({
  onCreated
}: PortfolioCreateFormProps): React.JSX.Element {
  const [info, setInfo] = useState<BasicInfoValues>(INITIAL_INFO)
  const [tagIds, setTagIds] = useState<number[]>([])
  const [highlights, setHighlights] = useState<FeaturedHighlight[]>([])
  const [isPublic, setIsPublic] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const patchInfo = (patch: Partial<BasicInfoValues>): void => {
    setInfo((prev) => ({ ...prev, ...patch }))
    setError(null)
  }

  const handleCreate = async (): Promise<void> => {
    const invalid = validate(info, tagIds)
    if (invalid !== null) {
      setError(invalid)
      return
    }

    setBusy(true)
    setError(null)
    try {
      await createMyPortfolio({
        title: info.title.trim(),
        // 소개는 비워 둘 수 있지만 null로 보내지 않는다 — 조회 시 non-null 문자열로 다룬다.
        introduction: info.introduction.trim(),
        // 연락처는 비우면 공개 뷰에 표시하지 않도록 null을 보낸다.
        contactEmail: info.contactEmail.trim() === '' ? null : info.contactEmail.trim(),
        contactUrl: info.contactUrl.trim() === '' ? null : info.contactUrl.trim(),
        isPublic,
        tagIds,
        // displayOrder는 화면에 담은 순서대로 매긴다.
        featuredHighlights: highlights.map((highlight, index) => ({
          highlightId: highlight.highlightId,
          displayOrder: index + 1
        }))
      })
      onCreated()
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="rounded-xl bg-muted/40 p-5">
        <h2 className="text-sm font-semibold">포트폴리오 만들기</h2>
        <p className="mt-1 text-caption text-muted-foreground">
          방송 실적은 종료된 방송에서 자동으로 집계돼요. 제목과 태그만 정하면 시작할 수 있어요.
        </p>
      </div>

      {/* 편집 화면과 같은 2열 구성(design.md §2)이라 만든 뒤에도 위치 감각이 유지된다. */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex min-w-0 flex-col gap-6">
          <BasicInfoForm values={info} onChange={patchInfo} />
          <FeaturedHighlightsEditor
            selected={highlights}
            onChange={(next) => {
              setHighlights(next)
              setError(null)
            }}
          />
        </div>

        <aside className="flex flex-col gap-6">
          <TagPicker
            selected={tagIds}
            onChange={(next) => {
              setTagIds(next)
              setError(null)
            }}
          />

          <Card>
            <CardHeader>
              <CardTitle className="text-sm font-semibold">공개 설정</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <div className="flex items-center justify-between gap-3">
                <Label
                  htmlFor="portfolio-create-public"
                  className="flex flex-col items-start gap-0.5"
                >
                  <span className="text-body font-medium">바로 공개하기</span>
                  <span className="text-caption font-normal text-muted-foreground">
                    끄면 나만 보이고, 나중에 켤 수 있어요
                  </span>
                </Label>
                <Switch
                  id="portfolio-create-public"
                  checked={isPublic}
                  onCheckedChange={(next) => {
                    setIsPublic(next)
                    setError(null)
                  }}
                />
              </div>

              {error !== null && <p className="text-xs text-destructive">{error}</p>}

              <Button
                type="button"
                size="lg"
                className="w-full"
                onClick={() => void handleCreate()}
                disabled={busy}
              >
                <Plus aria-hidden />
                {busy ? '만드는 중…' : '포트폴리오 만들기'}
              </Button>

              <p className="text-caption text-muted-foreground">
                공개 주소는 만들 때 자동으로 정해지고 바꿀 수 없어요.
              </p>
            </CardContent>
          </Card>
        </aside>
      </div>
    </div>
  )
}
