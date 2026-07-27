import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PATHS } from '@renderer/routes/paths'

/**
 * SC-01 로그인 화면.
 * 백엔드 미연결 상태 — 로그인 버튼을 누르면 성공을 가정하고
 * 다음 온보딩 단계(플랫폼 연동)로 이동한다.
 */
export default function LoginPage(): React.JSX.Element {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const handleSubmit = (e: React.FormEvent): void => {
    e.preventDefault()
    // TODO: POST /auth/login 연동 (현재는 성공 가정)
    // TODO: 로그인 성공 후 GET /platform-accounts로 연동 여부 확인
    //  - 연동 있음 → 대시보드
    //  - 연동 없음 → 플랫폼 연동(온보딩, 건너뛰기 가능)
    navigate(PATHS.dashboard)
  }

  return (
    <div className="flex h-screen items-center justify-center bg-muted">
      <div className="flex w-[360px] flex-col rounded-xl border bg-background p-8">
        {/* 로고 */}
        <div className="mb-8 flex items-center gap-2 self-center">
          <span className="flex h-9 w-9 items-center justify-center rounded-md bg-primary font-bold text-primary-foreground">
            L
          </span>
          <span className="text-xl font-bold">LAIVE</span>
        </div>

        <h1 className="mb-6 text-xl font-semibold">다시 오신 걸 환영해요</h1>

        {/* 이메일/비밀번호 로그인 */}
        <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="email" className="text-sm font-medium">
              이메일
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="streamer@example.com"
              className="rounded-md border px-3 py-2 text-sm"
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="password" className="text-sm font-medium">
              비밀번호
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="rounded-md border px-3 py-2 text-sm"
            />
          </div>

          <button
            type="submit"
            className="mt-2 rounded-md bg-primary py-3 text-sm font-semibold text-primary-foreground"
          >
            로그인
          </button>
        </form>

        {/* 구분선 */}
        <div className="my-5 flex items-center gap-3 text-xs text-muted-foreground">
          <span className="h-px flex-1 bg-border" />
          또는
          <span className="h-px flex-1 bg-border" />
        </div>

        {/* 소셜 로그인 (provider: NAVER | GOOGLE) */}
        <div className="flex flex-col gap-2.5">
          <button
            type="button"
            className="rounded-md bg-[#03c75a] py-3 text-sm font-semibold text-white"
          >
            네이버로 계속하기
          </button>
          <button
            type="button"
            className="rounded-md border py-3 text-sm font-semibold"
          >
            Google로 계속하기
          </button>
        </div>
      </div>
    </div>
  )
}
