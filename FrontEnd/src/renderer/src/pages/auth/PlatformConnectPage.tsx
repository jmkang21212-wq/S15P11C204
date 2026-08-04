import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2, Link2 } from 'lucide-react'
import { PATHS } from '@renderer/routes/paths'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { ApiError } from '@renderer/api/client'
import { PLATFORM_BRAND_COLOR } from '@renderer/lib/brand'
import { startPlatformLink } from '@renderer/api/platformAccounts'
import { platformAccountStore, usePlatformAccounts } from '@renderer/hooks/usePlatformAccounts'
import type { PlatformProvider } from '@renderer/types/platformAccount'
import Stepper from './components/Stepper'
import PlatformCard from './components/PlatformCard'

type Status = 'idle' | 'connecting' | 'error'

const STEPS = ['플랫폼 선택', 'OAuth 인증', '채널 확인']

/** 연동 가능한 플랫폼 — BE `PlatformAdapterRegistry`가 enabled인 어댑터만 실제로 통과시킨다. */
const PLATFORMS: { id: PlatformProvider; name: string; subLabel: string; color: string }[] = [
  { id: 'CHZZK', name: '치지직', subLabel: '연동하지 않음', color: PLATFORM_BRAND_COLOR.CHZZK },
  { id: 'YOUTUBE', name: '유튜브', subLabel: '연동하지 않음', color: PLATFORM_BRAND_COLOR.YOUTUBE }
]

const ERROR_MESSAGE: Record<string, string> = {
  PLATFORM_ALREADY_LINKED: '이미 다른 계정에 연동된 채널이에요.',
  PLATFORM_CONNECTION_FAILED: '플랫폼 연동에 실패했어요. 잠시 후 다시 시도해 주세요.',
  PLATFORM_INVALID_REQUEST: '연동 요청이 만료됐어요. 다시 시도해 주세요.'
}

/**
 * SC-01b 플랫폼 연동 화면 — 온보딩 겸 재연동(REAUTH_REQUIRED) 진입점(design.md §5.1).
 * `window.api.oauth.open`으로 실제 OAuth 인증 창을 띄운다(로그인과 같은 IPC).
 *
 * 여러 플랫폼을 동시에 연동할 수 있어 완료 시 화면을 통째로 갈아치우지 않고,
 * 카드별 연동 상태만 갱신하며 계속 다른 플랫폼을 이어서 연동할 수 있게 한다.
 */
export default function PlatformConnectPage(): React.JSX.Element {
  const navigate = useNavigate()
  const { accounts } = usePlatformAccounts()

  const [selected, setSelected] = useState<PlatformProvider | null>(null)
  const [status, setStatus] = useState<Status>('idle')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const hasConnected = accounts?.some((a) => a.connectionStatus === 'CONNECTED') ?? false
  const selectedName = PLATFORMS.find((p) => p.id === selected)?.name ?? ''
  const currentStep = status === 'connecting' ? 1 : hasConnected ? 2 : 0

  const handleConnect = async (platform: PlatformProvider): Promise<void> => {
    setSelected(platform)
    setStatus('connecting')
    setErrorMessage(null)

    try {
      const result = await startPlatformLink(platform)
      if (!result.ok) {
        if (result.reason === 'CANCELLED') {
          setStatus('idle')
          return
        }
        setErrorMessage(ERROR_MESSAGE[result.reason] ?? result.reason)
        setStatus('error')
        return
      }
      await platformAccountStore.refresh()
      setStatus('idle')
    } catch (e) {
      setErrorMessage(e instanceof ApiError ? e.message : '연동에 실패했어요. 다시 시도해 주세요.')
      setStatus('error')
    }
  }

  const goDashboard = (): void => {
    navigate(PATHS.dashboard)
  }

  return (
    <div className="flex h-screen items-center justify-center bg-background">
      <Card className="w-[440px]">
        <CardHeader className="gap-1">
          <span className="text-xs font-bold tracking-wide text-primary">STEP 2 · 플랫폼 연동</span>
          <CardTitle className="text-xl">방송 플랫폼을 연결하세요</CardTitle>
        </CardHeader>

        <CardContent className="flex flex-col gap-6">
          <Stepper steps={STEPS} current={currentStep} />

          {/* 플랫폼 선택 — 여러 개를 순서대로 연동할 수 있다 */}
          <div className="flex gap-3">
            {PLATFORMS.map((p) => {
              const account = accounts?.find((a) => a.platform === p.id) ?? null
              return (
                <PlatformCard
                  key={p.id}
                  name={p.name}
                  subLabel={p.subLabel}
                  color={p.color}
                  selected={selected === p.id && status === 'connecting'}
                  connectedChannelName={account?.channelName}
                  reauthRequired={account?.connectionStatus === 'REAUTH_REQUIRED'}
                  onSelect={() => void handleConnect(p.id)}
                />
              )
            })}
          </div>

          {/* OAuth 안내 */}
          <div className="flex items-center gap-3 rounded-lg bg-muted p-4">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-md border bg-card">
              <Link2 className="size-4 text-muted-foreground" aria-hidden />
            </span>
            <p className="text-xs leading-relaxed text-muted-foreground">
              기본 브라우저에서 <b className="text-foreground">OAuth 인증 창</b>이 열립니다. 인증을
              완료하면 자동으로 앱으로 돌아옵니다.
            </p>
          </div>

          {status === 'connecting' && (
            <p className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
              <Loader2 className="size-3.5 animate-spin" aria-hidden />
              {selectedName} 인증 진행 중…
            </p>
          )}

          {status === 'error' && (
            <p className="text-xs font-medium text-destructive">
              {errorMessage ?? '연동에 실패했어요. 다시 시도해 주세요.'}
            </p>
          )}
        </CardContent>

        <CardFooter className="flex-col gap-3">
          <Button size="lg" className="w-full" disabled={!hasConnected} onClick={goDashboard}>
            시작하기
          </Button>
          <button
            type="button"
            onClick={goDashboard}
            className="text-xs text-muted-foreground hover:text-foreground"
          >
            나중에 하기 (연동 없이 둘러보기)
          </button>
        </CardFooter>
      </Card>
    </div>
  )
}
