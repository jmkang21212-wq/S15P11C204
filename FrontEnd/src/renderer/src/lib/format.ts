/** 화면 표시용 포맷 유틸. */

/** 1234567 → "1,234,567" */
export function formatNumber(n: number): string {
  return n.toLocaleString('en-US')
}

/** 0.12 → "12%", -0.04 → "-4%" */
export function formatPercent(rate: number): string {
  return `${Math.round(rate * 100)}%`
}

/** ISO 문자열 → "21:00" (로컬 시간) */
export function formatClock(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

/** ISO 문자열 → "07.20" */
export function formatMonthDay(iso: string): string {
  const d = new Date(iso)
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}.${dd}`
}

/** 밀리초 → "0:42" */
export function formatDuration(ms: number): string {
  const totalSec = Math.round(ms / 1000)
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}
