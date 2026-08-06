import TopViewerList from '@renderer/components/common/TopViewerList'
import type { TopViewer } from '@renderer/types/topViewer'

/**
 * 콘솔 열성 시청자 패널 (FR-F-03).
 *
 * 방송 중에 보여주는 이유는 **이름을 불러주기 위해서**다. "오늘 저승사자님 많이
 * 와주셨네요" 한마디가 이 기능의 값어치인데, 방송이 끝난 뒤 리포트로 보면 늦다.
 * 같은 목록을 리포트에도 두는 것은 방송 중에 놓친 경우의 복기용이다.
 *
 * 형제 패널(SentimentPanel·HighlightPanel)과 같이 테두리 없는 패널로 두고 빈 상태도
 * 인라인 문구로 맞춘다 — 좁은 사이드바에서 `EmptyState`의 아이콘·여백은 과하고,
 * 패널 하나만 카드가 되면 사이드바의 리듬이 깨진다.
 */
export default function TopViewerPanel({ viewers }: { viewers: TopViewer[] }): React.JSX.Element {
  return (
    <div>
      <div className="mb-3 flex items-baseline justify-between">
        <span className="text-xs font-semibold text-muted-foreground">
          열성 시청자{' '}
          <span className="font-normal text-muted-foreground/70">· 채팅한 분 수 기준</span>
        </span>
        {viewers.length > 0 && (
          <span className="font-mono text-micro text-muted-foreground">TOP {viewers.length}</span>
        )}
      </div>

      {viewers.length > 0 ? (
        <TopViewerList viewers={viewers} />
      ) : (
        <p className="rounded-lg bg-muted/40 px-3 py-4 text-center text-caption text-muted-foreground">
          아직 채팅한 시청자가 없어요
        </p>
      )}
    </div>
  )
}
