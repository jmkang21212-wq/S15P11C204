import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useLiveSnapshot } from '@renderer/hooks/useLiveSnapshot'
import { useLiveBriefings } from '@renderer/hooks/useLiveBriefings'
import { useCurrentSession } from '@renderer/hooks/useCurrentSession'
import { mergeBriefings } from '@renderer/lib/briefings'
import MiniWidget from './components/MiniWidget'

/** HIGH 위험이 도착하면 이 시간 동안만 창을 강조 상태로 둔다 (목업 SC-03 부속). */
const HIGH_EMPHASIS_MS = 5000

/**
 * SC-03 부속: 미니 모드 창의 내용.
 * Electron 메인 프로세스가 항상-위 별도 창(340×220)으로 이 라우트를 로드한다.
 *
 * 데이터가 두 갈래로 온다:
 *  - 브리핑(RISK·PROMISE): 콘솔 창이 AI 서버에서 받아 main을 통해 중계한 것을 구독한다
 *    (미니 창이 직접 오디오를 보내면 마이크가 이중으로 열리고 세션이 충돌한다).
 *    스냅샷의 RISK·PROMISE는 받지 않는다(cutoff=null) — 릴레이와 이중으로 뜨고,
 *    main의 최근 버퍼가 창을 늦게 열었을 때의 히스토리를 겸한다. CHAT_SUMMARY처럼
 *    WS로 오지 않는 유형만 스냅샷에서 받는다(lib/briefings.ts).
 *  - 세션·시청자·여론: 이 창은 별도 BrowserWindow라 인증 토큰이 없다(authStore는
 *    렌더러 메모리 전용) — API를 직접 부르지 않고 main의 IPC 릴레이
 *    (session:state·snapshot:state)로 콘솔 창(owner)의 폴링 결과를 구독만 한다(follower 모드).
 *
 * 미리보기: `#/console/mini` (기본) / `#/console/mini?state=high` (HIGH 강조)
 */
export default function MiniModePage(): React.JSX.Element {
  const [params] = useSearchParams()
  const { session: currentSession } = useCurrentSession('follower')
  const sessionId = currentSession?.sessionId ?? null
  const { snapshot: data } = useLiveSnapshot(sessionId, 'follower')
  const liveBriefings = useLiveBriefings()
  /** 강조 시간이 지난 HIGH 브리핑 id — 강조를 끄는 쪽만 상태로 둔다. */
  const [expiredHighId, setExpiredHighId] = useState<number | null>(null)
  /** 답변 완료를 누른 질문 — 다음 스냅샷이 반영될 때까지 낙관적으로 숨긴다. */
  const [dismissed, setDismissed] = useState<number[]>([])

  const newestHighId = liveBriefings.find(
    (briefing) => briefing.briefingType === 'RISK' && briefing.level === 'HIGH'
  )?.briefingId

  // 새 HIGH가 오면 타이머를 다시 걸고, 시간이 지나면 그 id를 만료 처리해 강조를 끈다.
  useEffect(() => {
    if (newestHighId === undefined) return
    const timer = window.setTimeout(() => setExpiredHighId(newestHighId), HIGH_EMPHASIS_MS)
    return () => window.clearTimeout(timer)
  }, [newestHighId])

  const isHigh =
    params.get('state') === 'high' || (newestHighId !== undefined && newestHighId !== expiredHighId)

  // 콘솔 패널과 같은 규칙 — CONTEXT는 화면 맥락이 필요한 질문이라 제외한다.
  // 답변 완료를 누른 질문은 다음 스냅샷이 올 때까지 여기서 먼저 숨긴다.
  const questions = (data?.repeatedQuestions ?? []).filter(
    (question) => question.type !== 'CONTEXT' && !dismissed.includes(question.questionId)
  )

  const handleDismissQuestion = (questionId: number): void => {
    if (sessionId === null) return
    setDismissed((prev) => [...prev, questionId])
    // 이 창은 토큰이 없다 — 인증을 가진 콘솔 창이 대신 API를 호출한다.
    window.api.question.requestDismiss(sessionId, questionId)
  }

  return (
    <div className="h-screen w-screen">
      <MiniWidget
        data={data}
        briefings={mergeBriefings(liveBriefings, data?.briefings ?? [], null)}
        questions={questions}
        onDismissQuestion={handleDismissQuestion}
        isHigh={isHigh}
        onGoConsole={() => window.api.focusMainWindow()}
        onClose={() => window.api.closeMiniWindow()}
      />
    </div>
  )
}
