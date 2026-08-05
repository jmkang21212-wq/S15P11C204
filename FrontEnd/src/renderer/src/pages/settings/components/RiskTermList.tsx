import { X } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import StatusBadge, { type StatusTone } from '@renderer/components/common/StatusBadge'
import { RISK_SEVERITY_LABEL, type RiskTermSeverity } from '@renderer/types/riskTerm'

/**
 * 개인 사전 한 목록의 항목들. 금칙어·허용어가 등급 표시 유무만 다르므로 함께 쓴다.
 *
 * 화면에 보이는 값은 항상 `display`(사용자 입력 원문)다. 서버가 매칭에 쓰는 정규화된
 * 표기는 응답에 없다 — 사람이 읽기 나빠서 자기가 뭘 등록했는지 알아보지 못한다.
 */

/** 등급→tone 매핑은 화면이 갖는다. StatusBadge는 도메인 타입을 알지 않는다. */
const SEVERITY_TONE: Record<RiskTermSeverity, StatusTone> = {
  HIGH: 'danger',
  MEDIUM: 'warning',
  LOW: 'neutral'
}

export interface RiskTermListItem {
  id: number
  display: string
  /** 금칙어만 등급을 갖는다. 허용어는 생략한다. */
  severity?: RiskTermSeverity
}

interface RiskTermListProps {
  items: RiskTermListItem[]
  /** 삭제 중인 항목 id — 그 행만 비활성화해 다른 항목은 계속 지울 수 있다. */
  pendingId: number | null
  onRemove: (id: number) => void
}

export default function RiskTermList({
  items,
  pendingId,
  onRemove
}: RiskTermListProps): React.JSX.Element {
  return (
    <ul className="flex flex-col gap-1.5">
      {items.map((item) => (
        <li key={item.id} className="flex items-center gap-2 rounded-lg border px-3 py-2">
          <span className="min-w-0 flex-1 truncate text-body" title={item.display}>
            {item.display}
          </span>
          {item.severity && (
            <StatusBadge
              tone={SEVERITY_TONE[item.severity]}
              label={RISK_SEVERITY_LABEL[item.severity]}
            />
          )}
          <Button
            type="button"
            variant="ghost"
            size="icon"
            aria-label={`${item.display} 삭제`}
            disabled={pendingId === item.id}
            onClick={() => onRemove(item.id)}
          >
            <X className="size-[14px]" strokeWidth={1.8} aria-hidden />
          </Button>
        </li>
      ))}
    </ul>
  )
}
