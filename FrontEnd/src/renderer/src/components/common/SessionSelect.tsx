import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@renderer/components/ui/select'
import { formatDateTime } from '@renderer/lib/format'
import { cn } from '@renderer/lib/utils'
import type { EndedSessionSummary } from '@renderer/types/report'

/**
 * 종료된 방송 세션 선택기 — SC-07 클립과 SC-10 포트폴리오가 공유한다.
 *
 * 하이라이트는 세션별로만 조회할 수 있어(명세서 §11.4), 클립 후보를 고르려면
 * 두 화면 모두 먼저 세션을 골라야 한다.
 */

interface SessionSelectProps {
  sessions: EndedSessionSummary[]
  /** 목록에 없는 세션(예: URL에 잘못된 sessionId)이면 null을 넘겨 placeholder를 띄운다. */
  value: number | null
  onChange: (sessionId: number) => void
  className?: string
}

export default function SessionSelect({
  sessions,
  value,
  onChange,
  className
}: SessionSelectProps): React.JSX.Element {
  return (
    <Select
      value={value === null ? undefined : String(value)}
      onValueChange={(next) => onChange(Number(next))}
    >
      <SelectTrigger className={cn('w-80', className)} aria-label="방송 세션 선택">
        <SelectValue placeholder="방송을 선택하세요" />
      </SelectTrigger>
      <SelectContent>
        {sessions.map((session) => (
          <SelectItem key={session.sessionId} value={String(session.sessionId)}>
            <span className="flex flex-col items-start">
              <span className="text-body">{session.title}</span>
              <span className="font-mono text-caption text-muted-foreground">
                {formatDateTime(session.startedAt)}
              </span>
            </span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
