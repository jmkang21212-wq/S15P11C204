import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PATHS } from '@renderer/routes/paths'
import { Button } from '@renderer/components/ui/button'
import { Input } from '@renderer/components/ui/input'
import { Label } from '@renderer/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'

/**
 * SC-01a 로그인 화면.
 * 백엔드 미연결 상태 — 로그인 버튼을 누르면 성공을 가정하고
 * 디자인 가이드(SC-01a → SC-01b)에 따라 플랫폼 연동 화면으로 이동한다.
 */
export default function LoginPage(): React.JSX.Element {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    // TODO: POST /auth/login 연동 (현재는 성공 가정)
    navigate(PATHS.platformConnect)
  }

  return (
    <div className="flex h-screen items-center justify-center bg-background">
      <Card className="w-[360px]">
        <CardHeader className="flex flex-col items-center gap-3 text-center">
          <div className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-md bg-primary font-bold text-primary-foreground">
              L
            </span>
            <span className="text-xl font-bold">LAIVE</span>
          </div>
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
              className="w-full bg-[#03c75a] text-white hover:bg-[#03c75a]/90"
            >
              네이버로 계속하기
            </Button>
            <Button type="button" variant="outline" size="lg" className="w-full">
              Google로 계속하기
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
