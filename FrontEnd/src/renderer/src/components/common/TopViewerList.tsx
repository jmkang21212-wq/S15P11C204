import { cn } from '@renderer/lib/utils'
import { viewerDisplayName, type TopViewer } from '@renderer/types/topViewer'

/**
 * 열성 시청자 순위 목록 (FR-F-03).
 *
 * 방송 콘솔(방송 중)과 세션 리포트(방송 후)가 같은 응답 모양을 쓰므로 목록 본체를
 * 공용으로 둔다. 카드 껍데기와 빈 상태는 두 화면의 관례가 달라(콘솔은 테두리 없는
 * 패널, 리포트는 `Card` + `EmptyState`) 각 화면이 갖는다.
 *
 * **총점을 화면에 쓰지 않는다.** `totalScore`는 단위 없는 내부 점수이고 가중치가
 * 아직 튜닝 대상이라, 숫자를 보여주면 사용자가 해석할 수 없는 값을 해석하려 든다.
 * 대신 1위 대비 상대 막대로 "얼마나 차이 나는지"만 전하고, 숫자로는 사람이 검증할
 * 수 있는 `watchScore`(채팅한 분 수)만 쓴다. `chatScore`는 분당 3개 캡이 걸린
 * 가공값이라 "채팅 N건"으로 표기하면 거짓이 되므로 노출하지 않는다.
 */

interface TopViewerListProps {
  /** 총점 내림차순으로 이미 정렬돼 온다(서버 계약). 화면에서 다시 정렬하지 않는다. */
  viewers: TopViewer[]
}

export default function TopViewerList({ viewers }: TopViewerListProps): React.JSX.Element {
  // 1위를 100%로 잡는 상대 막대. 점수 자체가 방송 길이에 비례해 커지므로 절대 스케일은
  // 5분 방송과 3시간 방송에서 전혀 다르게 보인다.
  const top = viewers[0]?.totalScore ?? 0

  return (
    <ol className="flex flex-col gap-2.5">
      {viewers.map((viewer, index) => {
        const name = viewerDisplayName(viewer)
        const ratio = top > 0 ? viewer.totalScore / top : 0
        const first = index === 0

        return (
          <li key={viewer.viewerId} className="flex flex-col gap-1">
            <div className="flex items-baseline gap-2">
              <span className="w-3 shrink-0 font-mono text-caption text-muted-foreground">
                {index + 1}
              </span>
              <span
                className={cn(
                  'min-w-0 flex-1 truncate text-body',
                  // 최다 참여자만 강조한다 — 나머지는 참고 값이다(SentimentPanel과 같은 규칙).
                  first ? 'font-semibold text-foreground' : 'text-muted-foreground'
                )}
                title={name}
              >
                {name}
              </span>
              <span className="shrink-0 font-mono text-caption text-muted-foreground">
                {viewer.watchScore}분
              </span>
            </div>
            {/* 막대는 장식이다 — 순위·분 수가 이미 텍스트로 있어 스크린리더에 중복 읽히지 않게 한다 */}
            <span className="ml-5 block h-1.5 overflow-hidden rounded-full bg-muted" aria-hidden>
              <span
                className={cn('block h-full rounded-full', first ? 'bg-primary' : 'bg-primary/40')}
                style={{ width: `${ratio * 100}%` }}
              />
            </span>
          </li>
        )
      })}
    </ol>
  )
}
