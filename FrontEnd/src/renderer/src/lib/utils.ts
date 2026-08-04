import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

/**
 * design.md §1.3 커스텀 타이포 스케일(assets/globals.css의 --text-*)은 tailwind-merge
 * 기본 설정의 `font-size` 그룹(xs/sm/md/lg/xl류만 인식)에 안 걸린다. 매칭 실패한
 * `text-*`는 "아무 문자열이나 색으로 인정"하는 `text-color` 그룹(isAny)으로 샌다 —
 * Tailwind v4는 임의 팔레트를 쓸 수 있어 tailwind-merge가 색 이름을 특정할 수 없기
 * 때문이다. 그 결과 `cn('text-micro', 'text-accent-foreground')`처럼 커스텀 크기
 * 토큰 뒤에 진짜 색 클래스가 오면 같은 그룹 충돌로 오인돼 크기 토큰이 조용히
 * 사라진다. 커스텀 토큰을 font-size 그룹에 명시적으로 등록해 막는다.
 */
const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      text: ['micro', 'caption', 'body', 'subtitle', 'title', 'display', 'metric-sm', 'metric']
    }
  }
})

/** Tailwind 클래스 병합 유틸 (shadcn 표준). */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}
