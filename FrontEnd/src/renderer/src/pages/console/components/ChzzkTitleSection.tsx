import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { Pencil } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Input } from '@renderer/components/ui/input'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ApiError } from '@renderer/api/client'
import { fetchLiveSetting, isRelinkRequired, updateLiveSetting } from '@renderer/api/liveSetting'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import { usePlatformAccounts } from '@renderer/hooks/usePlatformAccounts'
import { PATHS } from '@renderer/routes/paths'
import type { BroadcastPlatformSession } from '@renderer/types/broadcastSession'

/** 치지직 스튜디오 UI 관측치 — 백엔드 `PlatformLiveSettingService.TITLE_MAX_LENGTH`와 맞춘다. */
const TITLE_MAX = 100

/**
 * 방송 콘솔의 치지직 방송 제목 섹션.
 *
 * 캘린더 일정 제목과 별개인 "치지직에 실제로 표시되는 제목"을 보여주고 즉석에서 수정한다.
 * 구 scope 토큰(방송 설정 권한 없음)이나 만료 토큰은 오류 코드로 구분해 재연동을 유도한다.
 *
 * 대시보드가 아니라 콘솔에 둔다. 치지직에 실시간 반영되는 값이라 방송 중에 만지는 것이
 * 정상이고, 방송을 조작하는 화면이 콘솔이라 여기가 제 자리다.
 *
 * <b>치지직으로 방송 중일 때만 보여준다.</b> 유튜브는 진행 중인 방송의 제목을 바꾸는
 * 경로가 없어 이 기능을 제공하지 않기로 했는데, 연동 여부만 보고 렌더하면 유튜브로
 * 방송하는 중에도 입력칸이 떠서 고칠 수 있을 것처럼 보인다. 그래서 "치지직이 연동돼
 * 있는가"가 아니라 "지금 방송이 치지직을 쓰는가"로 판단한다.
 */
export default function ChzzkTitleSection({
  platformSessions
}: {
  /** 현재 세션의 플랫폼별 자식 세션. 방송 전이면 빈 배열이다. */
  platformSessions: BroadcastPlatformSession[]
}): React.JSX.Element | null {
  const { accounts } = usePlatformAccounts()
  // 대표 계정 훅은 "CONNECTED 첫 번째"라 CHZZK 보장이 없어 직접 고른다.
  const chzzk = accounts?.find((a) => a.platform === 'CHZZK') ?? null

  const broadcastingOnChzzk = platformSessions.some(
    (session) => session.platformCode === 'CHZZK' && session.status !== 'FAILED'
  )

  if (!broadcastingOnChzzk) return null
  if (chzzk === null) return null
  if (chzzk.connectionStatus === 'REAUTH_REQUIRED' || chzzk.connectionStatus === 'DISCONNECTED') {
    return (
      <SectionFrame>
        <RelinkNotice message="치지직 재연동이 필요해요." />
      </SectionFrame>
    )
  }
  return <ConnectedSection platformAccountId={chzzk.platformAccountId} />
}

function ConnectedSection({ platformAccountId }: { platformAccountId: number }): React.JSX.Element {
  const loader = useCallback(() => fetchLiveSetting(platformAccountId), [platformAccountId])
  const { data, loading, error, refetch } = useAsyncData(loader)

  // 저장 성공 직후에는 PATCH 응답이 돌려준 최신 제목을 쓴다 — 재조회가 필요 없다.
  const [savedTitle, setSavedTitle] = useState<string | null>(null)
  const title = savedTitle ?? data?.defaultLiveTitle ?? null

  if (loading) {
    return (
      <SectionFrame>
        <Skeleton className="h-4 w-40" />
      </SectionFrame>
    )
  }

  if (error) {
    if (error instanceof ApiError && isRelinkRequired(error.code)) {
      return (
        <SectionFrame>
          <RelinkNotice
            message={
              error.code === 'PLATFORM_SCOPE_INSUFFICIENT'
                ? '방송 설정 권한이 없어요. 재연동해 새 권한을 승인해 주세요.'
                : '치지직 재연동이 필요해요.'
            }
          />
        </SectionFrame>
      )
    }
    return (
      <SectionFrame>
        <p className="text-xs text-destructive">
          치지직 방송 제목을 불러오지 못했어요.{' '}
          <button
            type="button"
            className="font-medium underline underline-offset-2"
            onClick={refetch}
          >
            다시 시도
          </button>
        </p>
      </SectionFrame>
    )
  }

  return (
    <SectionFrame>
      <TitleRow platformAccountId={platformAccountId} title={title ?? ''} onSaved={setSavedTitle} />
    </SectionFrame>
  )
}

function TitleRow({
  platformAccountId,
  title,
  onSaved
}: {
  platformAccountId: number
  title: string
  onSaved: (title: string) => void
}): React.JSX.Element {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(title)
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const openEditor = (): void => {
    setDraft(title)
    setFormError(null)
    setEditing(true)
  }

  const handleSave = async (): Promise<void> => {
    const trimmed = draft.trim()
    if (!trimmed) {
      setFormError('제목을 입력해 주세요.')
      return
    }
    setBusy(true)
    setFormError(null)
    try {
      const updated = await updateLiveSetting(platformAccountId, trimmed)
      onSaved(updated.defaultLiveTitle)
      setEditing(false)
    } catch (e) {
      setFormError(e instanceof ApiError ? e.message : '제목을 변경하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="relative">
      <div className="flex items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs font-semibold text-muted-foreground">치지직 방송 제목</p>
          <p className="truncate text-sm" title={title}>
            {title}
          </p>
        </div>
        <Button
          size="icon"
          variant="ghost"
          className="shrink-0"
          aria-label="치지직 방송 제목 수정"
          onClick={openEditor}
        >
          <Pencil aria-hidden />
        </Button>
      </div>

      {editing && (
        <div className="absolute top-full right-0 z-50 mt-2 flex w-72 flex-col gap-2 rounded-lg border bg-card p-3 shadow-md">
          <label className="text-xs font-medium text-muted-foreground">치지직 방송 제목</label>
          <Input
            value={draft}
            maxLength={TITLE_MAX}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="오늘 방송 제목"
          />
          {formError && <p className="text-xs text-destructive">{formError}</p>}
          <div className="flex gap-2">
            <Button size="sm" className="flex-1" onClick={() => void handleSave()} disabled={busy}>
              {busy ? '저장하는 중…' : '저장'}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(false)} disabled={busy}>
              취소
            </Button>
          </div>
          <p className="text-micro leading-relaxed text-muted-foreground">
            치지직 채널에 바로 반영돼요. 일정 제목과는 별개예요.
          </p>
        </div>
      )}
    </div>
  )
}

/**
 * 콘솔 우측 패널의 첫 블록이라 위쪽 구분선을 두지 않는다 — 대시보드 카드 안에 있을 때는
 * 앞 내용과 나누는 `border-t`가 필요했지만, 지금은 위에 아무것도 없어 선만 떠 보인다.
 * 아래 블록과의 간격은 패널의 `gap-4`가 맡는다.
 */
function SectionFrame({ children }: { children: React.ReactNode }): React.JSX.Element {
  return <div className="border-b pb-4">{children}</div>
}

function RelinkNotice({ message }: { message: string }): React.JSX.Element {
  return (
    <p className="text-xs text-muted-foreground">
      {message}{' '}
      <Link to={PATHS.platformConnect} className="font-medium text-primary">
        연동 관리 →
      </Link>
    </p>
  )
}
