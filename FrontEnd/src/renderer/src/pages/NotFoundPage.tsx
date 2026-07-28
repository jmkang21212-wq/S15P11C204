import { useLocation, useNavigate } from 'react-router-dom'
import { Compass, Hammer } from 'lucide-react'
import { Button } from '@renderer/components/ui/button'
import { EmptyState } from '@renderer/components/common/StateView'
import { PATHS } from '@renderer/routes/paths'

/**
 * 매칭되는 라우트가 없을 때 보여주는 화면.
 *
 * GNB 메뉴는 8개인데 아직 구현된 화면은 그보다 적어서, 담당자가 라우트를 채우기 전까지
 * 미구현 메뉴를 누르면 React Router 기본 개발용 에러 페이지가 그대로 노출됐다.
 * 이미 GNB에 걸려 있는 경로라면 "준비 중", 그 외에는 "없는 페이지"로 구분해 안내한다.
 */
export default function NotFoundPage(): React.JSX.Element {
  const navigate = useNavigate()
  const { pathname } = useLocation()

  /** paths.ts에 정의된 경로면 아직 구현 안 된 화면이라는 뜻이다. */
  const planned = Object.values(PATHS).some((path) => path === pathname)

  return (
    <div className="flex h-full items-center justify-center p-8">
      <EmptyState
        icon={planned ? Hammer : Compass}
        title={planned ? '아직 준비 중인 화면이에요' : '없는 페이지예요'}
        description={
          planned
            ? '이 메뉴는 곧 만들어질 예정이에요. 다른 메뉴를 먼저 둘러보세요.'
            : '주소를 다시 확인해 주세요.'
        }
        action={
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => navigate(PATHS.dashboard)}
          >
            대시보드로 가기
          </Button>
        }
      />
    </div>
  )
}
