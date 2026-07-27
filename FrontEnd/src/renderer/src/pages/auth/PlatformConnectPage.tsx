import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2, Link2, Check } from 'lucide-react'
import { PATHS } from '@renderer/routes/paths'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@renderer/components/ui/card'
import Stepper from './components/Stepper'
import PlatformCard from './components/PlatformCard'

type Platform = 'CHZZK' | 'SOOP'
type Status = 'idle' | 'connecting' | 'connected' | 'error'

const STEPS = ['플랫폼 선택', 'OAuth 인증', '채널 확인']

const PLATFORMS: { id: Platform; name: string; subLabel: string; color: string }[] = [
  { id: 'CHZZK', name: '치지직', subLabel: 'CHZZK', color: '#00ffa3' },
  { id: 'SOOP', name: 'SOOP', subLabel: '숲', color: '#00c73c' },
]

/**
 * SC-01b 플랫폼 연동 화면 (온보딩).
 * 백엔드 미연결 상태 — 실제 OAuth 대신 성공을 가정한 UI 흐름만 구현한다.
 */
export default function PlatformConnectPage(): React.JSX.Element {
  const navigate = useNavigate()
  const [selected, setSelected] = useState<Platform | null>(null)
  const [status, setStatus] = useState<Status>('idle')

  const currentStep = status === 'connected' ? 2 : selected ? 1 : 0
  const selectedName = PLATFORMS.find((p) => p.id === selected)?.name ?? ''

  const handleConnect = (): void => {
    if (!selected) return
    // TODO: GET /platform-accounts/{platform}/authorize
    //  → 기본 브라우저에서 OAuth 진행 후 딥링크로 앱 복귀 (Electron 통신 단계에서 구현)
    setStatus('connecting')
    // 백엔드 미연결 — 연동 성공을 가정하고 잠시 후 완료 처리 (UI 시뮬레이션)
    window.setTimeout(() => setStatus('connected'), 900)
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
                <p className="mt-1 text-xs text-muted-foreground">한별TV · 구독자 12,480</p>
              </div>
            </div>
          ) : (
            <>
              {/* 플랫폼 선택 */}
              <div className="flex gap-3">
                {PLATFORMS.map((p) => (
                  <PlatformCard
                    key={p.id}
                    name={p.name}
                    subLabel={p.subLabel}
                    color={p.color}
                    selected={selected === p.id}
                    onSelect={() => {
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
                  기본 브라우저에서 <b className="text-foreground">OAuth 인증 창</b>이 열립니다. 인증을
                  완료하면 자동으로 앱으로 돌아옵니다.
                </p>
              </div>

              {/* 에러 상태 — 실제 연동 실패(PLATFORM_CONNECTION_FAILED) 시 노출, 현재는 미발생 */}
              {status === 'error' && (
                <p className="text-xs font-medium text-destructive">
                  연동에 실패했어요. 다시 시도해 주세요.
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
              onClick={handleConnect}
            >
              {status === 'connecting' && <Loader2 className="animate-spin" aria-hidden />}
              {status === 'connecting' ? '인증 진행 중…' : `${selectedName || '플랫폼'} 인증 진행`}
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
