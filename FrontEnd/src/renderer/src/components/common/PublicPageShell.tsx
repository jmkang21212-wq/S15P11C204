import BrowserChrome from './BrowserChrome'

/**
 * 공개 페이지(SC-10 공개 뷰 · SC-11 디렉터리)의 겉면.
 *
 * 같은 페이지가 두 환경에서 렌더된다:
 *  - Electron 앱 안 — 가짜 브라우저 크롬을 씌워 "링크를 받은 사람에게 이렇게 보인다"를
 *    스트리머가 확인하게 한다(design.md §5.2).
 *  - 웹 빌드(`src/web`) — 크롬을 걷어낸다. 진짜 브라우저 안에 가짜 브라우저를 또 그리면
 *    주소창이 두 개가 되고, "앱으로 돌아가기"는 갈 곳이 없다.
 *
 * 환경을 컴포넌트가 스스로 판별하지 않고 prop으로 받는다 — 판별 로직을 숨기면 어느
 * 진입점이 무엇을 렌더하는지 라우터만 보고 알 수 없게 된다.
 */

interface PublicPageShellProps {
  /** true면 크롬 없이 본문만 렌더한다(웹 빌드). */
  standalone: boolean
  /** 크롬 주소창에 표시할 주소. standalone이면 쓰이지 않는다. */
  url: string
  onBack: () => void
  children: React.ReactNode
}

export default function PublicPageShell({
  standalone,
  url,
  onBack,
  children
}: PublicPageShellProps): React.JSX.Element {
  if (standalone) {
    // 크롬이 없으면 스크롤 컨테이너도 없다 — 문서 자체가 스크롤되게 둔다.
    return <div className="min-h-screen bg-background">{children}</div>
  }
  return (
    <BrowserChrome url={url} onBack={onBack}>
      {children}
    </BrowserChrome>
  )
}
