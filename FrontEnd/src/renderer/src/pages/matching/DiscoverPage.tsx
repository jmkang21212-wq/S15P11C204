import { useNavigate } from 'react-router-dom'
import PublicPageShell from '@renderer/components/common/PublicPageShell'
import PortfolioSearchPanel from './components/PortfolioSearchPanel'
import { PUBLIC_WEB_ORIGIN } from '@renderer/api/config'
import { PATHS } from '@renderer/routes/paths'

/**
 * SC-11 비즈니스 매칭 — 공개 디렉터리 (`/discover`, 배포 도메인은 PUBLIC_WEB_ORIGIN).
 *
 * GNB 없는 최상위 라우트이며, 인증 없이 광고주·타 스트리머가 접근한다는 걸 보여주려고
 * SC-10과 같은 브라우저 크롬으로 감싼다(design.md §5.2). 검색 로직은 `/matching`과
 * `PortfolioSearchPanel`을 그대로 공유한다.
 */
interface DiscoverPageProps {
  /** 웹 빌드에서 true — 가짜 브라우저 크롬 없이 본문만 렌더한다. */
  standalone?: boolean
}

export default function DiscoverPage({ standalone = false }: DiscoverPageProps): React.JSX.Element {
  const navigate = useNavigate()

  const openPortfolio = (publicSlug: string): void => {
    navigate(PATHS.publicPortfolio.replace(':publicSlug', publicSlug))
  }

  // 공개 웹에서 "앱으로 돌아가기"가 뜻이 통하는 유일한 목적지는 로그인 화면이다.
  const goBackToApp = (): void => {
    navigate(PATHS.login)
  }

  return (
    <PublicPageShell
      standalone={standalone}
      url={`${PUBLIC_WEB_ORIGIN}/discover`}
      onBack={goBackToApp}
    >
      <div className="mx-auto flex max-w-275 flex-col gap-6 p-8">
        <header className="flex flex-col gap-1">
          <h1 className="text-display font-extrabold tracking-[-0.02em]">스트리머 디렉터리</h1>
          <p className="text-body text-muted-foreground">
            협업하거나 광고를 진행할 스트리머를 찾아보세요. 로그인 없이 둘러볼 수 있습니다.
          </p>
        </header>

        <PortfolioSearchPanel onSelectPortfolio={openPortfolio} />
      </div>
    </PublicPageShell>
  )
}
