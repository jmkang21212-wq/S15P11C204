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
  PanelLeftClose,
  PanelLeftOpen,
  type LucideIcon
} from 'lucide-react'
import logo from '@renderer/assets/logo.png'
import appIcon from '@renderer/assets/app-icon.png'
import { cn } from '@renderer/lib/utils'
import { PATHS } from '@renderer/routes/paths'
import { useAuth } from '@renderer/auth/authStore'
import { logout } from '@renderer/api/auth'
import { usePlatformAccounts } from '@renderer/hooks/usePlatformAccounts'
import { useGnbCollapsed } from '@renderer/hooks/useGnbCollapsed'
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
  /** 진행 중인 방송이 있으면 상단 LIVE 배너를 노출한다. */
  live?: boolean
  /** live일 때 배너 클릭 시 이동할 세션 ID. */
  liveSessionId?: number
}

/**
 * 접힘↔펼침 시 라벨 텍스트. grid-template-columns를 0fr↔1fr로 트랜지션해
 * 텍스트 자체의 폭이 nav 폭 애니메이션과 같은 속도로 커지게 한다.
 * 조건부 마운트/언마운트로 텍스트가 순간적으로 '팝인'하며 nav 폭보다 먼저
 * 렌더되는 것을 막기 위함 — 항상 마운트해두고 폭만 트랜지션한다.
 */
function GnbLabel({
  show,
  children,
  className,
  wrapperClassName
}: {
  show: boolean
  children: React.ReactNode
  className?: string
  wrapperClassName?: string
}): React.JSX.Element {
  return (
    <span
      className={cn(
        'grid overflow-hidden transition-[grid-template-columns] duration-200 ease-out',
        show ? 'grid-cols-[1fr]' : 'grid-cols-[0fr]',
        wrapperClassName
      )}
    >
      <span className={cn('overflow-hidden whitespace-nowrap', className)}>{children}</span>
    </span>
  )
}

/**
 * Global Navigation Bar — 로그인 후 앱 화면들이 공유하는 좌측 주 메뉴.
 * 펼침 232px / 접힘 60px. 화면이 아니라 사용자가 토글 버튼으로 직접 전환하며,
 * 상태는 앱 전체가 공유한다(useGnbCollapsed).
 */
export default function GNB({ live = false, liveSessionId }: GNBProps): React.JSX.Element {
  const navigate = useNavigate()
  const { collapsed, toggle } = useGnbCollapsed()
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
  // 다중 연동을 지원한다 — 하나라도 REAUTH_REQUIRED면 경고를 우선 보여준다.
  const connectedAccounts = accounts?.filter((a) => a.connectionStatus === 'CONNECTED') ?? []
  const reauthAccount = accounts?.find((a) => a.connectionStatus === 'REAUTH_REQUIRED') ?? null
  const linkStatus: 'connected' | 'reauth' | 'none' = reauthAccount
    ? 'reauth'
    : connectedAccounts.length > 0
      ? 'connected'
      : 'none'
  const connectedLabel =
    connectedAccounts.length >= 2
      ? `연동됨 · ${connectedAccounts.length}개 채널`
      : connectedAccounts.length === 1
        ? `연동됨 · ${connectedAccounts[0].channelName}`
        : '연동됨'
  return (
    <nav
      className={cn(
        'flex h-full shrink-0 flex-col border-r bg-card transition-[width] duration-200',
        collapsed ? 'w-[60px] items-center' : 'w-[232px]'
      )}
    >
      {/* 로고 + 펼침/접힘 토글 */}
      <div
        className={cn(
          'flex items-center p-4',
          collapsed ? 'flex-col gap-2 justify-center' : 'justify-between'
        )}
      >
        {collapsed ? (
          <img src={appIcon} alt="LAIVE" className="size-8 rounded-md" />
        ) : (
          <img src={logo} alt="LAIVE" className="h-7 w-auto" />
        )}
        <button
          type="button"
          onClick={toggle}
          title={collapsed ? '펼치기' : '접기'}
          aria-label={collapsed ? '펼치기' : '접기'}
          aria-expanded={!collapsed}
          className="rounded-md p-1.5 text-muted-foreground transition-colors hover:bg-accent/50"
        >
          {collapsed ? (
            <PanelLeftOpen className="size-4" strokeWidth={1.8} aria-hidden />
          ) : (
            <PanelLeftClose className="size-4" strokeWidth={1.8} aria-hidden />
          )}
        </button>
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
            'mx-2 mb-2 flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-xs font-semibold text-destructive transition-all duration-200',
            collapsed && 'mx-1 justify-center gap-0 px-0'
          )}
        >
          <span
            className="size-2 shrink-0 rounded-full bg-destructive shadow-live-ring"
            aria-hidden
          />
          <GnbLabel show={!collapsed}>LIVE · 콘솔 열기</GnbLabel>
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
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-all duration-200',
                  isActive
                    ? 'bg-accent font-semibold text-accent-foreground'
                    : 'font-medium text-muted-foreground hover:bg-accent/50',
                  collapsed && 'justify-center gap-0 px-0'
                )
              }
            >
              <Icon className="size-[18px] shrink-0" strokeWidth={1.8} aria-hidden />
              <GnbLabel show={!collapsed}>{label}</GnbLabel>
            </NavLink>
          </li>
        ))}
      </ul>

      {/* 하단 — 플랫폼 연동 상태 + 계정. 만료·미연동이면 연동 화면으로 가는 CTA를 겸한다(design.md §6) */}
      <div
        className={cn(
          'flex flex-col gap-3 border-t p-4 transition-all duration-200',
          collapsed && 'items-center p-2'
        )}
      >
        {linkStatus === 'connected' ? (
          <div
            className={cn(
              'flex items-center gap-2 text-xs text-muted-foreground transition-all duration-200',
              collapsed && 'gap-0'
            )}
          >
            <span className="size-2 shrink-0 rounded-full bg-success" aria-hidden />
            <GnbLabel show={!collapsed}>{connectedLabel}</GnbLabel>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => navigate(PATHS.platformConnect)}
            className={cn(
              'flex items-center gap-2 text-xs font-medium transition-all duration-200',
              linkStatus === 'reauth' ? 'text-warning' : 'text-muted-foreground',
              collapsed && 'gap-0'
            )}
          >
            <span
              className={cn(
                'size-2 shrink-0 rounded-full',
                linkStatus === 'reauth' ? 'bg-warning' : 'bg-muted-foreground'
              )}
              aria-hidden
            />
            <GnbLabel show={!collapsed}>
              {linkStatus === 'reauth' ? '재연동 필요' : '미연동'}
            </GnbLabel>
          </button>
        )}
        <div ref={accountRef} className="relative">
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            title={collapsed ? (streamer?.nickname ?? '프로필') : undefined}
            className={cn(
              'flex items-center gap-2 rounded-md p-1 transition-all duration-200 hover:bg-accent/50',
              collapsed ? 'justify-center gap-0' : 'w-full'
            )}
          >
            <Avatar
              src={streamer?.profileImageUrl ?? null}
              alt={streamer?.nickname ?? '프로필'}
              size="size-7"
            />
            <GnbLabel
              show={!collapsed}
              wrapperClassName="min-w-0 flex-1"
              className="text-left text-sm font-medium"
            >
              {streamer?.nickname ?? '-'}
            </GnbLabel>
          </button>

          {menuOpen && (
            <div
              role="menu"
              className={cn(
                'absolute z-50 overflow-hidden rounded-md border bg-card shadow-md',
                collapsed ? 'bottom-0 left-full ml-1 w-max' : 'bottom-full left-0 mb-1 w-full'
              )}
            >
              <button
                type="button"
                role="menuitem"
                disabled={loggingOut}
                onClick={() => void handleLogout()}
                className="flex w-full items-center gap-2 whitespace-nowrap px-3 py-2 text-sm text-destructive transition-colors hover:bg-accent/50 disabled:opacity-60"
              >
                <LogOut className="size-4" strokeWidth={1.8} aria-hidden />
                {loggingOut ? '로그아웃 중…' : '로그아웃'}
              </button>
            </div>
          )}
        </div>
      </div>
    </nav>
  )
}
