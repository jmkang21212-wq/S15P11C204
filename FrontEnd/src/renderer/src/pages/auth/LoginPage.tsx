import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PATHS } from '@renderer/routes/paths'
import { Button } from '@renderer/components/ui/button'
import { Input } from '@renderer/components/ui/input'
import { Label } from '@renderer/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import logo from '@renderer/assets/logo.png'
import { NAVER_GREEN } from '@renderer/lib/brand'
import { loginWithDevAccount, loginWithNaver } from '@renderer/api/auth'
import { authStore } from '@renderer/auth/authStore'

/**
 * SC-01a 로그인 화면.
 * 백엔드 미연결 상태 — 로그인 버튼을 누르면 성공을 가정하고
 * 디자인 가이드(SC-01a → SC-01b)에 따라 플랫폼 연동 화면으로 이동한다.
 */
export default function LoginPage(): React.JSX.Element {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [naverLoading, setNaverLoading] = useState(false)
  const [devLoading, setDevLoading] = useState(false)

  // 부팅 시 App이 Refresh 쿠키로 세션을 복구해 두었으면 로그인 화면을 건너뛴다.
  useEffect(() => {
    if (authStore.getToken()) navigate(PATHS.dashboard, { replace: true })
  }, [navigate])

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    // 이메일 로그인은 백엔드 미구현 — 현재는 동작하지 않음 (네이버 OAuth만 연동)
  }

  const handleNaverLogin = async (): Promise<void> => {
    setNaverLoading(true)
    try {
      const ok = await loginWithNaver()
      // 로그인 성공 → 온보딩(플랫폼 연동)으로 (design.md SC-01a → SC-01b)
      if (ok) navigate(PATHS.platformConnect)
    } finally {
      setNaverLoading(false)
    }
  }

  // 개발 편의용 — 온보딩을 건너뛰고 바로 대시보드로 간다.
  const handleDevLogin = async (): Promise<void> => {
    setDevLoading(true)
    try {
      const ok = await loginWithDevAccount()
      if (ok) navigate(PATHS.dashboard)
    } finally {
      setDevLoading(false)
    }
  }

  return (
    <div className="flex h-screen items-center justify-center bg-background">
      <Card className="w-90">
        <CardHeader className="flex flex-col items-center gap-3 text-center">
          <img src={logo} alt="LAIVE" className="h-9 w-auto" />
          <CardTitle className="text-xl">다시 오신 걸 환영해요</CardTitle>
        </CardHeader>

        <CardContent className="flex flex-col gap-5">
          {/* 이메일/비밀번호 로그인 */}
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <div className="flex flex-col gap-2">
              <Label htmlFor="email">이메일</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="streamer@example.com"
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label htmlFor="password">비밀번호</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
            </div>
            <Button type="submit" size="lg" className="mt-1 w-full">
              로그인
            </Button>
          </form>

          {/* 구분선 */}
          <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <span className="h-px flex-1 bg-border" />
            또는
            <span className="h-px flex-1 bg-border" />
          </div>

          {/* 소셜 로그인 (provider: NAVER | GOOGLE) */}
          <div className="flex flex-col gap-2.5">
            <Button
              type="button"
              size="lg"
              className="w-full text-white transition-opacity hover:opacity-90"
              style={{ backgroundColor: NAVER_GREEN }}
              disabled={naverLoading}
              onClick={() => void handleNaverLogin()}
            >
              {naverLoading ? '네이버 인증 중…' : '네이버로 계속하기'}
            </Button>
            <Button type="button" variant="outline" size="lg" className="w-full">
              Google로 계속하기
            </Button>

            {/* 개발 빌드 전용 — 백엔드 DEV_LOGIN_ENABLED=true 필요 (프로덕션 번들에서 제거됨) */}
            {import.meta.env.DEV && (
              <Button
                type="button"
                variant="outline"
                size="lg"
                className="w-full border-dashed text-muted-foreground"
                disabled={devLoading}
                onClick={() => void handleDevLogin()}
              >
                {devLoading ? '로그인 중…' : '개발용 계정으로 로그인'}
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
