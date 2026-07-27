import { NavLink } from 'react-router-dom'
import {
  LayoutDashboard,
  MonitorPlay,
  CalendarDays,
  FileText,
  Film,
  TrendingUp,
  UserRound,
  Handshake,
  type LucideIcon,
} from 'lucide-react'
import { cn } from '@renderer/lib/utils'
import { PATHS } from '@renderer/routes/paths'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

/** GNB 메뉴 8개 (design.md §3). 새 화면 추가 시 여기에 등록한다. */
const NAV_ITEMS: NavItem[] = [
  { to: PATHS.dashboard, label: '대시보드', icon: LayoutDashboard },
  { to: PATHS.console, label: '방송 콘솔', icon: MonitorPlay },
  { to: PATHS.schedule, label: '일정', icon: CalendarDays },
  { to: PATHS.sessionReport, label: '세션 리포트', icon: FileText },
  { to: PATHS.clips, label: '클립', icon: Film },
  { to: PATHS.growth, label: '성장 리포트', icon: TrendingUp },
  { to: PATHS.portfolio, label: '포트폴리오', icon: UserRound },
  { to: PATHS.matching, label: '비즈니스 매칭', icon: Handshake },
]

interface GNBProps {
  /** 방송 콘솔(SC-03)에서 아이콘만 남기고 축소한다. */
  collapsed?: boolean
  /** 진행 중인 방송이 있으면 상단 LIVE 배너를 노출한다. */
  live?: boolean
}

/**
 * Global Navigation Bar — 로그인 후 앱 화면들이 공유하는 좌측 주 메뉴.
 * 일반 화면은 232px, 방송 콘솔에서는 collapsed로 60px 축소된다.
 */
export default function GNB({ collapsed = false, live = false }: GNBProps): React.JSX.Element {
  return (
    <nav
      className={cn(
        'flex h-full shrink-0 flex-col border-r bg-card',
        collapsed ? 'w-[60px] items-center' : 'w-[232px]',
      )}
    >
      {/* 로고 */}
      <div className={cn('flex items-center gap-2 p-4', collapsed && 'justify-center')}>
        <span className="flex size-8 items-center justify-center rounded-md bg-primary font-bold text-primary-foreground">
          L
        </span>
        {!collapsed && <span className="text-lg font-bold">LAIVE</span>}
      </div>

      {/* LIVE 배너 — 방송 중일 때만 노출, 클릭 시 콘솔로 이동 */}
      {live && (
        <button
          type="button"
          // TODO: 현재 진행 중인 세션의 콘솔(/console/:sessionId)로 이동
          className={cn(
            'mx-2 mb-2 flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs font-semibold text-destructive',
            collapsed && 'mx-1 justify-center px-0',
          )}
        >
          <span
            className="size-2 rounded-full bg-destructive shadow-[0_0_0_3px_rgba(225,29,72,0.25)]"
            aria-hidden
          />
          {!collapsed && <span>LIVE · 콘솔 열기</span>}
        </button>
      )}

      {/* 메뉴 */}
      <ul className="flex flex-1 flex-col gap-1 px-2">
        {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
          <li key={to}>
            <NavLink
              to={to}
              title={collapsed ? label : undefined}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                  isActive
                    ? 'bg-accent font-semibold text-accent-foreground'
                    : 'font-medium text-muted-foreground hover:bg-accent/50',
                  collapsed && 'justify-center px-0',
                )
              }
            >
              <Icon className="size-[18px] shrink-0" strokeWidth={1.8} aria-hidden />
              {!collapsed && <span>{label}</span>}
            </NavLink>
          </li>
        ))}
      </ul>

      {/* 하단 — 플랫폼 연동 상태 + 계정 (현재는 정적 placeholder) */}
      <div className={cn('flex flex-col gap-3 border-t p-4', collapsed && 'items-center p-2')}>
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span className="size-2 rounded-full bg-success" aria-hidden />
          {!collapsed && <span>연동됨</span>}
        </div>
        {!collapsed && (
          <div className="flex items-center gap-2">
            <span className="size-7 rounded-full bg-muted" aria-hidden />
            <span className="text-sm font-medium">한별TV</span>
          </div>
        )}
      </div>
    </nav>
  )
}
