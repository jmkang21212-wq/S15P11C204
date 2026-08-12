import { useEffect } from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { PATHS } from '@renderer/routes/paths'
import { audioStreamStore } from '@renderer/hooks/useAudioStream'
import { dismissRepeatedQuestion } from '@renderer/api/repeatedQuestions'
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

  /**
   * 미니 창의 "답변 완료"를 대신 처리한다.
   *
   * 미니 창은 별도 BrowserWindow라 토큰이 없어(authStore는 렌더러 메모리 전용) API를
   * 직접 부르지 못하고, main을 거쳐 이 창에 요청만 보낸다(main/question.ts).
   *
   * <b>화면이 아니라 여기가 받는 이유</b> — 원래는 콘솔의 반복 질문 패널이 받았는데,
   * 그 컴포넌트는 방송 콘솔 화면에서만 산다. 사용자가 메인 창을 대시보드 같은 다른
   * 화면에 두면 요청을 받을 사람이 없어져, 미니에서 눌러도 서버에 반영되지 않고 다음
   * 폴링에서 질문이 되살아났다. 로그인 상태면 어떤 화면이든 마운트돼 있는 이 가드가
   * 받으면 화면 위치와 무관해진다.
   *
   * 미니·오버레이 창은 RequireAuth 바깥 라우트라(routes/router.tsx) 여기에 걸리지
   * 않는다 — 토큰 없는 창이 401을 내거나 중복 호출이 생기지 않는다.
   *
   * sessionId는 payload로 온 값을 그대로 쓴다. 보낸 쪽이 지금 보고 있는 세션이라,
   * 이 창이 어느 화면에 있든 정확하다.
   */
  useEffect(() => {
    const bridge = window.api?.question
    if (bridge === undefined || signedOut) return
    return bridge.onDismissRequest((payload) => {
      // 실패해도 조용히 둔다 — 요청을 보낸 미니 창은 이미 목록에서 숨겼고, 다음 폴링에
      // 질문이 그대로 돌아오는 것이 곧 실패 표시다. 이 창에는 띄울 자리가 없다.
      void dismissRepeatedQuestion(payload.sessionId, payload.questionId).catch(() => undefined)
    })
  }, [signedOut])

  if (signedOut) return <Navigate to={PATHS.login} replace />
  return <Outlet />
}
