import { Navigate, Outlet } from 'react-router-dom'
import { PATHS } from '@renderer/routes/paths'
import { useAuth } from './authStore'

/**
 * 인증 가드. Access Token이 없으면 로그인 화면으로 보낸다.
 * authStore를 구독하므로 사용 중 세션이 만료되어 스토어가 비워져도
 * (401 → refresh 실패 → authStore.clear) 즉시 로그인 화면으로 이동한다.
 */
export default function RequireAuth(): React.JSX.Element {
  const { accessToken } = useAuth()
  if (!accessToken) return <Navigate to={PATHS.login} replace />
  return <Outlet />
}
