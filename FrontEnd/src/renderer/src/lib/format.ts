/**
 * 표시용 포맷 유틸 — 여러 화면에서 공유한다.
 *
 * design.md §1에 따라 여기서 나온 값(수치·시간·ID)은 화면에서 `font-mono`로 렌더한다.
 */

/** 밀리초 오프셋을 `1:23:45` / `12:30` 형태로. 방송 시작 기준 상대 시각에 쓴다. */
export function formatMs(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  const mm = h > 0 ? String(m).padStart(2, '0') : String(m)
  return h > 0 ? `${h}:${mm}:${String(s).padStart(2, '0')}` : `${mm}:${String(s).padStart(2, '0')}`
}

/** 구간 길이를 `41초` / `3분 20초` 형태로. 하이라이트 길이 표기용. */
export function formatClipLength(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000))
  const m = Math.floor(total / 60)
  const s = total % 60
  return m > 0 ? `${m}분 ${s}초` : `${s}초`
}

/** 방송 길이를 `3시간 28분` 형태로. 진행 중(endedAt null)이면 '-'. */
export function formatDuration(startedAt: string, endedAt: string | null): string {
  if (endedAt === null) return '-'
  const ms = new Date(endedAt).getTime() - new Date(startedAt).getTime()
  if (!Number.isFinite(ms) || ms <= 0) return '-'
  const totalMin = Math.round(ms / 60000)
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  if (h === 0) return `${m}분`
  return m === 0 ? `${h}시간` : `${h}시간 ${m}분`
}

/** 큰 수를 천 단위 구분자로. `48200` → `48,200` */
export function formatCount(n: number): string {
  return n.toLocaleString('ko-KR')
}

/** 0~1 비율을 퍼센트 문자열로. `0.64` → `64%` */
export function formatPercent(ratio: number, digits = 0): string {
  return `${(ratio * 100).toFixed(digits)}%`
}

const DATE_TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false
})

/** ISO 문자열을 `2026. 7. 20. (월) 22:04` 형태로. */
export function formatDateTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '-'
  return DATE_TIME_FORMAT.format(d)
}

const TIME_FORMAT = new Intl.DateTimeFormat('ko-KR', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: false
})

/** ISO 문자열을 `22:04` 형태로. 차트 축·툴팁용. */
export function formatTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '-'
  return TIME_FORMAT.format(d)
}

/** ISO 문자열을 `07.20` 형태로. 카드의 짧은 날짜 표기용. */
export function formatMonthDay(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '-'
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${mm}.${dd}`
}
