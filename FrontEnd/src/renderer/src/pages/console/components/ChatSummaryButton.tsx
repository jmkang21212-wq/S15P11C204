import { memo, useEffect, useState } from 'react'
import { Sparkles } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { createChatSummary } from '@renderer/api/chatSummary'
import { formatCount } from '@renderer/lib/format'

/**
 * 수동 여론 요약 버튼.
 *
 * 요약은 3분 주기로 자동 생성되지만, 스트리머가 "지금 분위기"를 바로 알고 싶은
 * 순간이 있어 즉시 생성 요청을 제공한다(계약 §2 MANUAL).
 *
 * 생성 결과를 여기서 직접 보여주지 않는다 — 요약은 CHAT_SUMMARY 브리핑으로 저장되어
 * 다음 스냅샷 폴링에 브리핑 피드로 나타난다. 두 곳에 같은 내용을 띄우면 중복이다.
 */
function ChatSummaryButton({ sessionId }: { sessionId: number }): React.JSX.Element {
  const [requesting, setRequesting] = useState(false)
  /** 남은 쿨다운(초). 0보다 크면 버튼을 잠근다. */
  const [cooldownSec, setCooldownSec] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)

  // 1초마다 줄인다. setTimeout 콜백에서만 setState하므로 effect 본문에서 동기 갱신하지 않는다.
  useEffect(() => {
    if (cooldownSec <= 0) return
    const timer = window.setTimeout(() => setCooldownSec((prev) => Math.max(0, prev - 1)), 1000)
    return () => window.clearTimeout(timer)
  }, [cooldownSec])

  const handleClick = async (): Promise<void> => {
    setRequesting(true)
    setNotice(null)
    try {
      const result = await createChatSummary(sessionId)
      switch (result.status) {
        case 'CREATED':
          // 요약문은 브리핑 피드에서 보여준다. 여기서는 다음 요청까지 잠그기만 한다.
          setNotice('요약을 만들었어요 · 브리핑에서 확인하세요')
          setCooldownSec(secondsUntil(result.nextAvailableAt) ?? 0)
          break
        case 'COOLDOWN':
          setCooldownSec(result.retryAfterSec ?? secondsUntil(result.nextAvailableAt) ?? 60)
          break
        case 'IN_FLIGHT':
          setNotice('요약을 만들고 있어요')
          setCooldownSec(result.retryAfterSec ?? 2)
          break
        case 'NOT_ENOUGH_CHATS':
          setNotice(
            `채팅 ${formatCount(result.chatCount ?? 0)}/${formatCount(result.required ?? 0)}건 — 조금 더 모이면 요약할 수 있어요`
          )
          break
      }
    } catch {
      setNotice('요약 생성에 실패했어요')
    } finally {
      setRequesting(false)
    }
  }

  const locked = requesting || cooldownSec > 0

  return (
    <div className="flex flex-col gap-1.5 border-t pt-4">
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={locked}
        onClick={() => void handleClick()}
      >
        <Sparkles className="size-4" strokeWidth={1.8} aria-hidden />
        {requesting ? '요청 중…' : cooldownSec > 0 ? `${cooldownSec}초 후 가능` : '지금 여론 요약'}
      </Button>
      {notice && <p className="text-micro text-muted-foreground">{notice}</p>}
    </div>
  )
}

/** ISO 시각까지 남은 초. 값이 없거나 이미 지났으면 null. */
function secondsUntil(iso: string | null): number | null {
  if (!iso) return null
  const remaining = Math.ceil((new Date(iso).getTime() - Date.now()) / 1000)
  return remaining > 0 ? remaining : null
}

/*
 * 3초 스냅샷 폴링이 도는 화면이라 memo로 감싼다. 폴링 응답은 매번 새로 파싱된 객체라
 * 값이 그대로여도 참조가 달라지는데, useLiveSnapshot이 바뀌지 않은 조각의 참조를
 * 유지해 주므로(hooks/useLiveSnapshot.ts) 여기서 memo가 실제로 걸린다.
 * 측정: perf/console-render.bench.tsx
 */
export default memo(ChatSummaryButton)
