import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { PATHS } from '@renderer/routes/paths'
import { audioStreamStore } from '@renderer/hooks/useAudioStream'
import { useAuth } from './authStore'

/**
 * 인증 가드. Access Token이 없으면 로그인 화면으로 보낸다.
 * authStore를 구독하므로 사용 중 세션이 만료되어 스토어가 비워져도
 * (401 → refresh 실패 → authStore.clear) 즉시 로그인 화면으로 이동한다.
 *
 * 인증이 끊기면 음성 스트림도 함께 정리한다. 오디오 스트림은 화면 수명과 분리되어
 * (`useAudioStream`) 페이지 이동만으로는 멈추지 않으므로, 여기서 놓아주지 않으면 로그아웃한
 * 뒤에도 마이크가 켜져 있고 다음 사용자에게 이전 방송의 브리핑이 남는다.
 *
 * 로그아웃과 세션 만료가 모두 `authStore.clear()`를 지나므로 이 한 곳이면 두 경로를 다 덮는다.
 */
export default function RequireAuth(): React.JSX.Element {
  const { accessToken } = useAuth()
  const signedOut = accessToken === null

  useEffect(() => {
    if (signedOut) audioStreamStore.reset()
  }, [signedOut])

  if (signedOut) return <Navigate to={PATHS.login} replace />
  return <Outlet />
}
