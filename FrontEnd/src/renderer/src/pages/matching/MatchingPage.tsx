import { useNavigate } from 'react-router-dom'
import PageHeader from '@renderer/components/common/PageHeader'
import PortfolioSearchPanel from './components/PortfolioSearchPanel'
import { PATHS } from '@renderer/routes/paths'

/**
 * SC-11 비즈니스 매칭 — 로그인 후 앱 안에서 쓰는 검색 화면.
 *
 * API는 공개 포트폴리오 탐색으로 한정된다(명세서 §1.2) — 매칭 요청·추천 점수는 없다.
 * 검색 UI 자체는 `/discover`(공개 디렉터리)와 `PortfolioSearchPanel`을 공유한다.
 */
export default function MatchingPage(): React.JSX.Element {
  const navigate = useNavigate()

  const openPortfolio = (publicSlug: string): void => {
    navigate(PATHS.publicPortfolio.replace(':publicSlug', publicSlug))
  }

  return (
    <div className="mx-auto flex max-w-[1400px] flex-col gap-6 p-8">
      <PageHeader
        title="비즈니스 매칭"
        caption="다른 스트리머의 공개 포트폴리오를 채널·태그·시청자 규모로 찾아보세요."
      />

      <PortfolioSearchPanel onSelectPortfolio={openPortfolio} />
    </div>
  )
}
