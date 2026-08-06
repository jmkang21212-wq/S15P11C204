import { Users } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import TopViewerList from '@renderer/components/common/TopViewerList'
import type { TopViewer } from '@renderer/types/topViewer'

/**
 * 이 방송의 열성 시청자 (FR-F-03).
 *
 * 목록 본체는 콘솔과 공유하고(`components/common/TopViewerList`), 여기서는 리포트
 * 사이드바 관례대로 `Card` + `EmptyState`로 감싼다 — `HighlightList`와 같은 모양이다.
 *
 * 방송 종료 시 `viewer_scores`에 확정 저장된 값이라 다시 열어도 순위가 변하지 않는다.
 * 채팅이 90일 후 삭제된 뒤에도 이 목록은 남는다.
 */
export default function TopViewerCard({ viewers }: { viewers: TopViewer[] }): React.JSX.Element {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-baseline justify-between gap-2">
          <CardTitle className="text-sm font-semibold">열성 시청자</CardTitle>
          {viewers.length > 0 && (
            <span className="shrink-0 font-mono text-caption text-muted-foreground">
              TOP {viewers.length}
            </span>
          )}
        </div>
        <p className="text-caption leading-relaxed text-muted-foreground">
          채팅한 분(minute) 수를 기준으로 매겼어요. 도배는 점수에 반영되지 않아요.
        </p>
      </CardHeader>
      <CardContent>
        {viewers.length === 0 ? (
          <EmptyState
            icon={Users}
            title="열성 시청자가 없어요"
            description="이번 방송에서는 채팅을 남긴 시청자가 없었습니다."
          />
        ) : (
          <TopViewerList viewers={viewers} />
        )}
      </CardContent>
    </Card>
  )
}
