import { cn } from '@renderer/lib/utils'

/**
 * 화면 상단 제목 블록 — 세션 리포트·클립·성장 리포트·포트폴리오·비즈니스 매칭 5개
 * 페이지가 같은 마크업(`text-title font-bold` 제목 + 캡션 + 우측 액션)을 복붙하고 있어
 * 공용화했다. 컨테이너(`mx-auto max-w-[...] p-8`)는 페이지마다 값이 달라 여기서
 * 묶지 않는다 — 그러면 `max-w`를 prop으로 뚫어야 해 감싸는 의미가 없어진다.
 */

interface PageHeaderProps {
  title: string
  caption?: string
  /** 우측 슬롯 — 탭 전환, 세션 선택, 저장 버튼 등 화면별 액션. */
  actions?: React.ReactNode
  className?: string
}

export default function PageHeader({
  title,
  caption,
  actions,
  className
}: PageHeaderProps): React.JSX.Element {
  return (
    <header className={cn('flex flex-wrap items-start justify-between gap-4', className)}>
      <div className="flex flex-col gap-2">
        <h1 className="text-title font-bold tracking-[-0.02em]">{title}</h1>
        {caption && <p className="text-xs text-muted-foreground">{caption}</p>}
      </div>
      {actions}
    </header>
  )
}
