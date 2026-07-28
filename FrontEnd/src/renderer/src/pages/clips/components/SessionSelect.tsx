import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@renderer/components/ui/select'
import { formatDateTime } from '@renderer/lib/format'
import type { EndedSessionSummary } from '@renderer/types/report'

interface SessionSelectProps {
  sessions: EndedSessionSummary[]
  /** 목록에 없는 세션(예: URL에 잘못된 sessionId)이면 null을 넘겨 placeholder를 띄운다. */
  value: number | null
  onChange: (sessionId: number) => void
}

/** 클립을 볼 방송 세션을 고른다. 종료된 세션만 들어온다. */
export default function SessionSelect({
  sessions,
  value,
  onChange
}: SessionSelectProps): React.JSX.Element {
  return (
    <Select
      value={value === null ? undefined : String(value)}
      onValueChange={(next) => onChange(Number(next))}
    >
      <SelectTrigger className="w-[320px]" aria-label="방송 세션 선택">
        <SelectValue placeholder="방송을 선택하세요" />
      </SelectTrigger>
      <SelectContent>
        {sessions.map((session) => (
          <SelectItem key={session.sessionId} value={String(session.sessionId)}>
            <span className="flex flex-col items-start">
              <span className="text-[13px]">{session.title}</span>
              <span className="font-mono text-[11px] text-muted-foreground">
                {formatDateTime(session.startedAt)}
              </span>
            </span>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
