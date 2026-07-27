import { NavLink } from 'react-router-dom'
import { PATHS } from '@renderer/routes/paths'

interface NavItem {
  to: string
  label: string
}

/** GNB 메뉴 항목 — 새 화면이 추가되면 여기에 한 줄씩 등록한다. */
const NAV_ITEMS: NavItem[] = [
  { to: PATHS.dashboard, label: '대시보드' },
  { to: PATHS.schedule, label: '일정' },
  { to: PATHS.sessionReport, label: '세션 리포트' },
  { to: PATHS.clips, label: '클립' },
  { to: PATHS.growth, label: '성장 리포트' },
  { to: PATHS.portfolio, label: '포트폴리오' },
  { to: PATHS.matching, label: '비즈니스 매칭' },
]

interface GNBProps {
  /** 방송 콘솔(SC-03)에서 아이콘만 남기고 축소한다. */
  collapsed?: boolean
}

/**
 * Global Navigation Bar — 로그인 후 앱 화면들이 공유하는 좌측 주 메뉴.
 * 일반 화면은 232px, 방송 콘솔에서는 collapsed로 60px 축소된다.
 */
export default function GNB({ collapsed = false }: GNBProps): React.JSX.Element {
  return (
    <nav
      className={`flex h-full shrink-0 flex-col border-r ${
        collapsed ? 'w-[60px] items-center' : 'w-[232px]'
      }`}
    >
      {/* 로고 */}
      <div className="flex items-center gap-2 p-4">
        <span className="flex h-8 w-8 items-center justify-center rounded-md bg-primary font-bold text-primary-foreground">
          L
        </span>
        {!collapsed && <span className="text-lg font-bold">LAIVE</span>}
      </div>

      {/* 메뉴 */}
      <ul className="flex flex-1 flex-col gap-1 px-2">
        {NAV_ITEMS.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              title={collapsed ? item.label : undefined}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-md px-3 py-2 text-sm ${
                  isActive
                    ? 'bg-accent font-semibold text-primary'
                    : 'text-muted-foreground'
                } ${collapsed ? 'justify-center' : ''}`
              }
            >
              {/* 아이콘 자리 — 팀원 아이콘 세트 적용 예정 */}
              <span className="h-4 w-4 shrink-0 rounded-sm border" aria-hidden />
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          </li>
        ))}
      </ul>

      {/* 하단 상태 표시 — 현재는 정적 placeholder (추후 실데이터 연결) */}
      <div className="flex items-center gap-2 p-4 text-xs text-muted-foreground">
        <span className="h-2 w-2 rounded-full bg-green-500" aria-hidden />
        {!collapsed && <span>연동됨</span>}
      </div>
    </nav>
  )
}
