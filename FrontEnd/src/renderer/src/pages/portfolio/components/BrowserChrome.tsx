import { ArrowLeft, Lock } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'

/**
 * 공개 뷰를 감싸는 가짜 브라우저 크롬.
 *
 * design.md §5.2 "브라우저 크롬을 씌운 비인증 뷰를 별도 구현" — 스트리머가
 * "링크를 받은 사람에게 이렇게 보인다"를 앱 안에서 확인하도록 만든 장식이다.
 * 실제 브라우저가 아니므로 주소창은 입력할 수 없다.
 */

interface BrowserChromeProps {
  /** 주소창에 보여줄 주소. 스킴 없이 넘긴다. */
  url: string
  onBack: () => void
  children: React.ReactNode
}

export default function BrowserChrome({
  url,
  onBack,
  children
}: BrowserChromeProps): React.JSX.Element {
  return (
    <div className="flex h-screen flex-col bg-muted/60 p-6">
      <div className="mx-auto flex h-full w-full max-w-[1100px] flex-col overflow-hidden rounded-xl border bg-card shadow-[0_10px_34px_rgba(0,0,0,.09)]">
        {/* 크롬 상단 바 */}
        <div className="flex shrink-0 items-center gap-3 border-b bg-muted/40 px-4 py-2.5">
          <div className="flex shrink-0 items-center gap-1.5" aria-hidden>
            <span className="size-2.5 rounded-full bg-destructive/60" />
            <span className="size-2.5 rounded-full bg-warning/60" />
            <span className="size-2.5 rounded-full bg-success/60" />
          </div>

          <div className="flex min-w-0 flex-1 items-center gap-1.5 rounded-full bg-background px-3 py-1.5">
            <Lock className="size-[12px] shrink-0 text-success" strokeWidth={2} aria-hidden />
            <span className="truncate font-mono text-[11px] text-muted-foreground">{url}</span>
          </div>

          <Button type="button" variant="ghost" size="sm" onClick={onBack} className="shrink-0">
            <ArrowLeft className="size-[14px]" strokeWidth={1.8} aria-hidden />
            앱으로 돌아가기
          </Button>
        </div>

        {/* 페이지 본문 */}
        <div className="min-h-0 flex-1 overflow-auto">{children}</div>
      </div>
    </div>
  )
}
