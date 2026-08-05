import { useState } from 'react'
import { Plus, ShieldAlert, ShieldOff } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { Input } from '@renderer/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from '@renderer/components/ui/select'
import { EmptyState } from '@renderer/components/common/StateView'
import { ApiError } from '@renderer/api/client'
import RiskTermList, { type RiskTermListItem } from './RiskTermList'
import {
  DEFAULT_RISK_SEVERITY,
  RISK_SEVERITY_LABEL,
  type RiskTermKind,
  type RiskTermSeverity
} from '@renderer/types/riskTerm'

/**
 * 개인 사전 한 종류(금칙어 또는 허용어)의 입력 + 목록.
 *
 * 문구를 페이지가 넘기지 않고 `kind`로 여기서 고른다 — 두 목록의 성격 차이가 곧 문구
 * 차이라서, 설명이 UI 옆에 있는 편이 어긋나지 않는다.
 *
 * 길이 검증을 화면에서 하지 않는다. 서버 판정 기준이 원문이 아니라 **정규화된** 길이이고
 * 그 규칙은 AI 서버에 있다 — `"아!"`는 두 글자지만 정규화하면 한 글자라 거절된다.
 * 흉내 내면 어긋나므로 서버 메시지를 그대로 보여준다.
 */

const COPY: Record<
  RiskTermKind,
  { title: string; description: string; emptyTitle: string; emptyDescription: string }
> = {
  BLOCK: {
    title: '금칙어',
    description: '이 표현이 나오면 바로 알려요. 기본 감지에 더해서 잡아요.',
    emptyTitle: '아직 추가한 금칙어가 없어요',
    emptyDescription: '기본 감지는 그대로 동작하고, 여기에 추가하면 함께 잡아요.'
  },
  ALLOW: {
    title: '허용어',
    description: '잘못 감지된 표현을 적어 두면 다음부터 알리지 않아요.',
    emptyTitle: '아직 등록한 허용어가 없어요',
    emptyDescription: '방송 중 경고에서 본 표현을 그대로 넣어 보세요.'
  }
}

const ICON: Record<RiskTermKind, typeof ShieldAlert> = {
  BLOCK: ShieldAlert,
  ALLOW: ShieldOff
}

const SEVERITIES: RiskTermSeverity[] = ['HIGH', 'MEDIUM', 'LOW']

interface RiskTermPanelProps {
  kind: RiskTermKind
  items: RiskTermListItem[]
  /** 서버가 내려준 종류당 상한. 화면에 "N / 200"을 하드코딩하지 않는다. */
  maxPerKind: number
  /** 등록 성공 시 부모가 목록을 교체한다. 실패는 throw로 올려 여기서 문구를 보여준다. */
  onAdd: (term: string, severity?: RiskTermSeverity) => Promise<void>
  onRemove: (id: number) => Promise<void>
}

export default function RiskTermPanel({
  kind,
  items,
  maxPerKind,
  onAdd,
  onRemove
}: RiskTermPanelProps): React.JSX.Element {
  const [draft, setDraft] = useState('')
  const [severity, setSeverity] = useState<RiskTermSeverity>(DEFAULT_RISK_SEVERITY)
  const [busy, setBusy] = useState(false)
  const [pendingId, setPendingId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const copy = COPY[kind]
  const full = items.length >= maxPerKind

  const handleAdd = async (): Promise<void> => {
    const trimmed = draft.trim()
    if (trimmed === '') return
    setBusy(true)
    setError(null)
    try {
      await onAdd(trimmed, kind === 'BLOCK' ? severity : undefined)
      setDraft('')
      // 등급은 유지한다 — 같은 등급으로 여러 개를 연달아 넣는 경우가 많다.
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '등록하지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  const handleRemove = async (id: number): Promise<void> => {
    setPendingId(id)
    setError(null)
    try {
      await onRemove(id)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '삭제하지 못했어요.')
    } finally {
      setPendingId(null)
    }
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle className="text-sm font-semibold">{copy.title}</CardTitle>
          <span className="shrink-0 font-mono text-caption text-muted-foreground">
            {items.length} / {maxPerKind}
          </span>
        </div>
        <p className="text-caption leading-relaxed text-muted-foreground">{copy.description}</p>
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        {/* 입력을 목록 위에 둔다 — 방금 추가한 항목이 바로 아래에 보인다 */}
        <div className="flex flex-col gap-2">
          <div className="flex items-end gap-2">
            <Input
              value={draft}
              onChange={(e) => {
                setDraft(e.target.value)
                setError(null)
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') void handleAdd()
              }}
              placeholder="표현 입력"
              aria-label={`${copy.title} 입력`}
              disabled={full}
              className="min-w-0 flex-1"
            />

            {/* 등급은 금칙어에만 있다 — 허용어는 감지를 없애는 쪽이라 등급 개념이 없다 */}
            {kind === 'BLOCK' && (
              <Select
                value={severity}
                onValueChange={(v) => setSeverity(v as RiskTermSeverity)}
                disabled={full}
              >
                <SelectTrigger className="w-[92px]" aria-label="알림 등급">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SEVERITIES.map((value) => (
                    <SelectItem key={value} value={value}>
                      {RISK_SEVERITY_LABEL[value]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}

            <Button
              type="button"
              size="sm"
              onClick={() => void handleAdd()}
              disabled={busy || full || draft.trim() === ''}
            >
              <Plus className="size-[14px]" strokeWidth={1.8} aria-hidden />
              {busy ? '추가 중…' : '추가'}
            </Button>
          </div>

          {full && (
            <p className="text-caption text-warning">
              상한에 도달했어요. 새로 추가하려면 기존 항목을 지워 주세요.
            </p>
          )}
          {error !== null && <p className="text-xs text-destructive">{error}</p>}
        </div>

        {items.length === 0 ? (
          <EmptyState
            icon={ICON[kind]}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
            className="rounded-lg bg-muted/40 py-8"
          />
        ) : (
          <RiskTermList
            items={items}
            pendingId={pendingId}
            onRemove={(id) => void handleRemove(id)}
          />
        )}
      </CardContent>
    </Card>
  )
}
