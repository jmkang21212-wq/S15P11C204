import { useEffect, useState } from 'react'
import { RouterProvider } from 'react-router-dom'
import { router } from '@renderer/routes/router'
import { refreshSession } from '@renderer/api/auth'

// StrictMode의 effect 이중 실행에도 refresh(토큰 회전)가 한 번만 나가도록 모듈 레벨에서 합친다.
let bootRefresh: Promise<boolean> | null = null
function ensureBootRefresh(): Promise<boolean> {
  bootRefresh ??= refreshSession()
  return bootRefresh
}

/**
 * 부팅 시 Refresh Token 쿠키로 세션 복구를 먼저 시도한 뒤 라우터를 렌더한다.
 * 로그인 화면이 첫 렌더에서 세션 유무를 판단할 수 있어야 하기 때문이다.
 */
function App(): React.JSX.Element {
  const [booting, setBooting] = useState(true)

  useEffect(() => {
    void ensureBootRefresh().finally(() => setBooting(false))
  }, [])

  if (booting) return <div className="h-screen bg-background" />
  return <RouterProvider router={router} />
}

export default App
