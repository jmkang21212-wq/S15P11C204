import { useCallback, useState } from 'react'
import { Skeleton } from '@renderer/components/ui/skeleton'
import { ErrorState } from '@renderer/components/common/StateView'
import PageHeader from '@renderer/components/common/PageHeader'
import RiskTermPanel from './components/RiskTermPanel'
import { createRiskTerm, deleteRiskTerm, fetchRiskTerms } from '@renderer/api/riskTerms'
import { useAsyncData } from '@renderer/hooks/useAsyncData'
import type { RiskTerms, RiskTermSeverity } from '@renderer/types/riskTerm'

/**
 * 설정 화면. 지금은 위험 발언 개인 사전만 다룬다.
 *
 * 앞으로 붙을 설정이 코드에 이미 예고돼 있어(방송 언어 `DEFAULT_LANGUAGE`의 TODO,
 * OBS 스트림 키, 하이라이트 민감도) 화면을 `설정`으로 열어 두고 섹션을 늘려간다.
 *
 * 금칙어와 허용어를 탭이 아니라 나란히 둔다. 서버가 "같은 표현이 반대쪽 목록에 있으면
 * 충돌"로 거절하므로(`RISK_TERM_CONFLICTING_KIND`), 두 목록이 함께 보이지 않으면 사용자가
 * 그 오류를 만났을 때 반대쪽을 확인하려고 화면을 왕복해야 한다.
 */
export default function SettingsPage(): React.JSX.Element {
  const loader = useCallback(() => fetchRiskTerms(), [])
  const { data, loading, error, refetch } = useAsyncData(loader)

  /**
   * 서버가 조회·등록·삭제에 모두 갱신된 전체 목록을 돌려주므로 그것으로 교체한다.
   * `useAsyncData`의 refetch를 쓰면 요청이 한 번 더 나가고 스켈레톤이 깜빡인다.
   */
  const [applied, setApplied] = useState<RiskTerms | null>(null)
  const terms = applied ?? data

  const handleAdd = async (
    kind: 'BLOCK' | 'ALLOW',
    term: string,
    severity?: RiskTermSeverity
  ): Promise<void> => {
    // 실패는 그대로 올린다 — 어느 패널에서 났는지 알아야 그 자리에 문구를 띄울 수 있다.
    setApplied(await createRiskTerm({ kind, term, severity }))
  }

  const handleRemove = async (id: number): Promise<void> => {
    setApplied(await deleteRiskTerm(id))
  }

  return (
    <div className="mx-auto flex max-w-350 flex-col gap-6 p-8">
      <PageHeader title="설정" caption="방송 중 위험 발언 감지에 쓰는 개인 사전을 관리하세요." />

      <section className="flex flex-col gap-3">
        <div className="flex items-baseline gap-2">
          <h2 className="text-sm font-semibold">위험 발언 감지</h2>
          <span className="text-caption text-muted-foreground">
            기본 감지는 항상 동작하고, 여기 등록한 표현으로 조정해요
          </span>
        </div>

        {loading && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <Skeleton className="h-80 w-full rounded-xl" />
            <Skeleton className="h-80 w-full rounded-xl" />
          </div>
        )}

        {!loading && error && (
          <ErrorState message="개인 사전을 불러오지 못했습니다." onRetry={refetch} />
        )}

        {terms !== null && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            <RiskTermPanel
              kind="BLOCK"
              items={terms.block}
              maxPerKind={terms.limits.maxPerKind}
              onAdd={(term, severity) => handleAdd('BLOCK', term, severity)}
              onRemove={handleRemove}
            />
            <RiskTermPanel
              kind="ALLOW"
              items={terms.allow}
              maxPerKind={terms.limits.maxPerKind}
              onAdd={(term) => handleAdd('ALLOW', term)}
              onRemove={handleRemove}
            />
          </div>
        )}
      </section>
    </div>
  )
}
