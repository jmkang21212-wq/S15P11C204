import { useEffect, useRef, useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  MonitorPlay,
  CalendarDays,
  FileText,
  Film,
  TrendingUp,
  UserRound,
  Handshake,
  LogOut,
  type LucideIcon
} from 'lucide-react'
import { cn } from '@renderer/lib/utils'
import { PATHS } from '@renderer/routes/paths'
import { useAuth } from '@renderer/auth/authStore'
import { logout } from '@renderer/api/auth'
import { usePlatformAccounts } from '@renderer/hooks/usePlatformAccounts'
import Avatar from '@renderer/components/common/Avatar'

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
  { to: PATHS.matching, label: '비즈니스 매칭', icon: Handshake }
]

interface GNBProps {
  /** 방송 콘솔(SC-03)에서 아이콘만 남기고 축소한다. */
  collapsed?: boolean
  /** 진행 중인 방송이 있으면 상단 LIVE 배너를 노출한다. */
  live?: boolean
  /** live일 때 배너 클릭 시 이동할 세션 ID. */
  liveSessionId?: number
}

/**
 * Global Navigation Bar — 로그인 후 앱 화면들이 공유하는 좌측 주 메뉴.
 * 일반 화면은 232px, 방송 콘솔에서는 collapsed로 60px 축소된다.
 */
export default function GNB({
  collapsed = false,
  live = false,
  liveSessionId
}: GNBProps): React.JSX.Element {
  const navigate = useNavigate()
  const { streamer } = useAuth()
  const { accounts } = usePlatformAccounts()
  const [menuOpen, setMenuOpen] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const accountRef = useRef<HTMLDivElement>(null)

  // 메뉴 밖을 누르면 닫는다. 좁은 GNB에서 메뉴가 열린 채 남으면 다른 조작을 가린다.
  useEffect(() => {
    if (!menuOpen) return
    const handlePointerDown = (event: MouseEvent): void => {
      if (!accountRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    const handleEscape = (event: KeyboardEvent): void => {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [menuOpen])

  /**
   * 로그아웃. authStore가 비워지면 RequireAuth가 로그인 화면으로 보내므로
   * 여기서 별도로 이동시키지 않는다.
   * 서버 요청이 실패해도 logout()이 로컬 상태는 정리한다.
   */
  const handleLogout = async (): Promise<void> => {
    setLoggingOut(true)
    try {
      await logout()
    } finally {
      setMenuOpen(false)
      setLoggingOut(false)
    }
  }
  const primary = accounts?.find((a) => a.connectionStatus === 'CONNECTED') ?? accounts?.[0] ?? null
  const linkStatus: 'connected' | 'reauth' | 'none' =
    primary === null
      ? 'none'
      : primary.connectionStatus === 'REAUTH_REQUIRED'
        ? 'reauth'
        : 'connected'
  return (
    <nav
      className={cn(
        'flex h-full shrink-0 flex-col border-r bg-card',
        collapsed ? 'w-[60px] items-center' : 'w-[232px]'
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
          onClick={() => {
            if (liveSessionId !== undefined) {
              navigate(PATHS.consoleSession.replace(':sessionId', String(liveSessionId)))
            } else {
              navigate(PATHS.console)
            }
          }}
          className={cn(
            'mx-2 mb-2 flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs font-semibold text-destructive',
            collapsed && 'mx-1 justify-center px-0'
          )}
        >
          <span className="size-2 rounded-full bg-destructive shadow-live-ring" aria-hidden />
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
                  collapsed && 'justify-center px-0'
                )
              }
            >
              <Icon className="size-[18px] shrink-0" strokeWidth={1.8} aria-hidden />
              {!collapsed && <span>{label}</span>}
            </NavLink>
          </li>
        ))}
      </ul>

      {/* 하단 — 플랫폼 연동 상태 + 계정. 만료·미연동이면 연동 화면으로 가는 CTA를 겸한다(design.md §6) */}
      <div className={cn('flex flex-col gap-3 border-t p-4', collapsed && 'items-center p-2')}>
        {linkStatus === 'connected' ? (
          <div className="flex items-center gap-2 text-xs text-muted-foreground">
            <span className="size-2 rounded-full bg-success" aria-hidden />
            {!collapsed && <span>연동됨{primary && ` · ${primary.channelName}`}</span>}
          </div>
        ) : (
          <button
            type="button"
            onClick={() => navigate(PATHS.platformConnect)}
            className={cn(
              'flex items-center gap-2 text-xs font-medium',
              linkStatus === 'reauth' ? 'text-warning' : 'text-muted-foreground'
            )}
          >
            <span
              className={cn(
                'size-2 rounded-full',
                linkStatus === 'reauth' ? 'bg-warning' : 'bg-muted-foreground'
              )}
              aria-hidden
            />
            {!collapsed && <span>{linkStatus === 'reauth' ? '재연동 필요' : '미연동'}</span>}
          </button>
        )}
        {!collapsed && (
          <div ref={accountRef} className="relative">
            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              className="flex w-full items-center gap-2 rounded-md p-1 transition-colors hover:bg-accent/50"
            >
              <Avatar
                src={streamer?.profileImageUrl ?? null}
                alt={streamer?.nickname ?? '프로필'}
                size="size-7"
              />
              <span className="flex-1 truncate text-left text-sm font-medium">
                {streamer?.nickname ?? '-'}
              </span>
            </button>

            {menuOpen && (
              <div
                role="menu"
                className="absolute bottom-full left-0 z-50 mb-1 w-full overflow-hidden rounded-md border bg-card shadow-md"
              >
                <button
                  type="button"
                  role="menuitem"
                  disabled={loggingOut}
                  onClick={() => void handleLogout()}
                  className="flex w-full items-center gap-2 px-3 py-2 text-sm text-destructive transition-colors hover:bg-accent/50 disabled:opacity-60"
                >
                  <LogOut className="size-4" strokeWidth={1.8} aria-hidden />
                  {loggingOut ? '로그아웃 중…' : '로그아웃'}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </nav>
  )
}
