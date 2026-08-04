import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2, Link2, Check } from 'lucide-react'
import { PATHS } from '@renderer/routes/paths'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { PLATFORM_BRAND_COLOR } from '@renderer/lib/brand'
import { startPlatformLink } from '@renderer/api/platformAccounts'
import { platformAccountStore, usePlatformAccounts } from '@renderer/hooks/usePlatformAccounts'
import type { PlatformProvider } from '@renderer/types/platformAccount'
import Stepper from './components/Stepper'
import PlatformCard from './components/PlatformCard'

type Status = 'idle' | 'connecting' | 'connected' | 'error'

const STEPS = ['플랫폼 선택', 'OAuth 인증', '채널 확인']

/** BE(`PlatformAccountService.requireSupported`)가 CHZZK만 허용해 SOOP은 선택할 수 없다. */
const PLATFORMS: {
  id: PlatformProvider
  name: string
  subLabel: string
  color: string
  supported: boolean
}[] = [
  {
    id: 'CHZZK',
    name: '치지직',
    subLabel: 'CHZZK',
    color: PLATFORM_BRAND_COLOR.CHZZK,
    supported: true
  },
  { id: 'SOOP', name: 'SOOP', subLabel: '숲', color: PLATFORM_BRAND_COLOR.SOOP, supported: false }
]

const ERROR_MESSAGE: Record<string, string> = {
  PLATFORM_ALREADY_LINKED: '이미 다른 계정에 연동된 채널이에요.',
  PLATFORM_CONNECTION_FAILED: '플랫폼 연동에 실패했어요. 잠시 후 다시 시도해 주세요.',
  PLATFORM_INVALID_REQUEST: '연동 요청이 만료됐어요. 다시 시도해 주세요.'
}

/**
 * SC-01b 플랫폼 연동 화면 — 온보딩 겸 재연동(REAUTH_REQUIRED) 진입점(design.md §5.1).
 * `window.api.oauth.open`으로 실제 CHZZK OAuth 인증 창을 띄운다(로그인과 같은 IPC).
 */
export default function PlatformConnectPage(): React.JSX.Element {
  const navigate = useNavigate()
  const { accounts } = usePlatformAccounts()
  const existing = accounts?.[0] ?? null
  const isReauth = existing !== null

  const [selected, setSelected] = useState<PlatformProvider | null>(
    isReauth ? existing.platform : null
  )
  const [status, setStatus] = useState<Status>('idle')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [connectedName, setConnectedName] = useState<string | null>(null)

  const currentStep = status === 'connected' ? 2 : selected ? 1 : 0
  const selectedName = PLATFORMS.find((p) => p.id === selected)?.name ?? ''

  const handleConnect = async (): Promise<void> => {
    if (!selected) return
    setStatus('connecting')
    setErrorMessage(null)

    const result = await startPlatformLink(selected)
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
    const linked = platformAccountStore.get().accounts?.find((a) => a.platform === selected)
    setConnectedName(linked?.channelName ?? selectedName)
    setStatus('connected')
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

          {status === 'connected' ? (
            /* 채널 확인 단계 */
            <div className="flex flex-col items-center gap-3 rounded-xl border bg-accent/40 p-6 text-center">
              <span className="flex size-12 items-center justify-center rounded-full bg-success text-success-foreground">
                <Check className="size-6" strokeWidth={3} aria-hidden />
              </span>
              <div>
                <p className="text-sm font-semibold">{selectedName} 채널이 연결되었어요</p>
                {connectedName && (
                  <p className="mt-1 text-xs text-muted-foreground">{connectedName}</p>
                )}
              </div>
            </div>
          ) : (
            <>
              {/* 재연동 진입 — 이미 연동된 채널이 있으면(REAUTH_REQUIRED 포함) 먼저 알려준다 */}
              {isReauth && (
                <div className="rounded-lg border border-warning/25 bg-warning/10 p-3 text-xs text-warning">
                  현재 <b>{existing.channelName}</b>
                  {existing.connectionStatus === 'REAUTH_REQUIRED'
                    ? ' 연동이 만료됐어요. 다시 연동해 주세요.'
                    : ' 채널이 연동돼 있어요. 다시 연동하면 최신 상태로 갱신됩니다.'}
                </div>
              )}

              {/* 플랫폼 선택 */}
              <div className="flex gap-3">
                {PLATFORMS.map((p) => (
                  <PlatformCard
                    key={p.id}
                    name={p.name}
                    subLabel={p.subLabel}
                    color={p.color}
                    selected={selected === p.id}
                    disabled={!p.supported}
                    onSelect={() => {
                      if (!p.supported) return
                      setSelected(p.id)
                      setStatus('idle')
                    }}
                  />
                ))}
              </div>

              {/* OAuth 안내 */}
              <div className="flex items-center gap-3 rounded-lg bg-muted p-4">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-md border bg-card">
                  <Link2 className="size-4 text-muted-foreground" aria-hidden />
                </span>
                <p className="text-xs leading-relaxed text-muted-foreground">
                  기본 브라우저에서 <b className="text-foreground">OAuth 인증 창</b>이 열립니다.
                  인증을 완료하면 자동으로 앱으로 돌아옵니다.
                </p>
              </div>

              {status === 'error' && (
                <p className="text-xs font-medium text-destructive">
                  {errorMessage ?? '연동에 실패했어요. 다시 시도해 주세요.'}
                </p>
              )}
            </>
          )}
        </CardContent>

        <CardFooter className="flex-col gap-3">
          {status === 'connected' ? (
            <Button size="lg" className="w-full" onClick={goDashboard}>
              시작하기
            </Button>
          ) : (
            <Button
              size="lg"
              className="w-full"
              disabled={!selected || status === 'connecting'}
              onClick={() => void handleConnect()}
            >
              {status === 'connecting' && <Loader2 className="animate-spin" aria-hidden />}
              {status === 'connecting'
                ? '인증 진행 중…'
                : `${selectedName || '플랫폼'} ${isReauth ? '다시 연동' : '인증 진행'}`}
            </Button>
          )}
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
