import { Link } from 'react-router-dom'
import { Card } from '@renderer/components/ui/card'
import { EmptyState } from '@renderer/components/common/StateView'
import { PATHS } from '@renderer/routes/paths'
import { formatCount, formatMonthDay } from '@renderer/lib/format'
import type { RecentSession } from '@renderer/types/dashboard'

interface RecentSessionCardProps {
  session: RecentSession | null
}

export default function RecentSessionCard({ session }: RecentSessionCardProps): React.JSX.Element {
  return (
    <Card className="gap-3 p-5">
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold text-muted-foreground">최근 세션 성과</span>
        {session && (
          <span className="font-mono text-xs text-muted-foreground">
            {formatMonthDay(session.endedAt)}
          </span>
        )}
      </div>

      {session ? (
        <div className="flex flex-1 flex-col">
          <p className="text-sm font-semibold">{session.title}</p>
          <div className="mt-3 flex gap-6">
            <div>
              <div className="font-mono text-2xl font-extrabold text-primary">
                {formatCount(session.peakViewers)}
              </div>
              <div className="text-xs text-muted-foreground">피크 시청자</div>
            </div>
            <div>
              <div className="font-mono text-2xl font-extrabold">
                {formatCount(session.avgViewers)}
              </div>
              <div className="text-xs text-muted-foreground">평균 시청자</div>
            </div>
          </div>
          {/*
            BE가 `COALESCE(r.summary, '')`로 내려주기 때문에 요약이 없으면 빈 문자열이 온다.
            그대로 그리면 아무것도 없는 회색 박스가 남아 고장처럼 보인다 — 리포트가 아직
            만들어지는 중인지, AI 요약이 없는 것인지 대신 알려준다.
          */}
          {session.summary ? (
            <p className="mt-3 rounded-lg bg-muted px-3 py-2 text-xs leading-relaxed text-muted-foreground">
              {session.summary}
            </p>
          ) : (
            <p className="mt-3 text-xs text-muted-foreground">
              {session.reportStatus === 'PROCESSING'
                ? '리포트를 만들고 있어요.'
                : 'AI 요약이 없어요.'}
            </p>
          )}
          <Link
            to={`${PATHS.sessionReport}/${session.sessionId}`}
            className="mt-auto pt-3 text-xs font-medium text-muted-foreground hover:text-foreground"
          >
            리포트 보기 →
          </Link>
        </div>
      ) : (
        <EmptyState
          title="아직 완료된 방송이 없어요"
          className="flex-1 rounded-lg bg-muted/40 py-8"
        />
      )}
    </Card>
  )
}
