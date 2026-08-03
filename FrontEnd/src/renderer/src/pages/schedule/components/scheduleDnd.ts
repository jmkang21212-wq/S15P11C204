/**
 * 캘린더 날짜 칸으로 끌어다 놓는 드래그 규약 — 약속과 일정 둘 다 다룬다.
 *
 * 별도 DnD 라이브러리를 쓰지 않고 HTML5 native drag-and-drop만 쓴다 — design.md가
 * shadcn/ui 외 UI 라이브러리를 금지하고, 필요한 동작이 "칩 하나를 날짜 칸에 떨어뜨린다"
 * 뿐이라 라이브러리를 들일 이유가 없다.
 *
 * 커스텀 MIME을 쓰는 이유는 드롭 대상을 좁히기 위해서다. `text/plain`을 쓰면 브라우저
 * 밖에서 끌어온 텍스트·파일까지 날짜 칸이 받아버린다.
 */

export const SCHEDULE_DND_MIME = 'application/x-laive-schedule-item'

/**
 * 약속 드래그의 출처. 떨어뜨렸을 때의 동작이 갈린다.
 *  - `unscheduled` 기한 미정 목록에서 왔다 → 시간을 받아 **일정으로 전환**한다
 *  - `calendar`    이미 기한이 있는 칩이다 → 기한만 다른 날짜로 **옮긴다**
 *
 * 같은 드래그처럼 보이지만 "일정을 확정한다"와 "잡힌 기한을 옮긴다"는 다른 행위라
 * 출처를 담아 구분한다. 드롭 쪽에서 목록을 되짚어 추측하지 않는다.
 */
export type PromiseDragSource = 'unscheduled' | 'calendar'

export type ScheduleDragPayload =
  { kind: 'promise'; id: number; source: PromiseDragSource } | { kind: 'event'; id: number }

export function setScheduleDragData(
  dataTransfer: DataTransfer,
  payload: ScheduleDragPayload
): void {
  dataTransfer.setData(SCHEDULE_DND_MIME, JSON.stringify(payload))
  dataTransfer.effectAllowed = 'move'
}

/**
 * 드래그 중인 것이 캘린더 항목인지. `dragover`/`dragenter`에서는 보안상 `getData()`가
 * 빈 문자열을 돌려주고 `types`만 읽을 수 있어, 값이 아니라 타입 존재로 판단해야 한다.
 */
export function isScheduleDrag(dataTransfer: DataTransfer | null): boolean {
  return dataTransfer !== null && Array.from(dataTransfer.types).includes(SCHEDULE_DND_MIME)
}

/** `drop`에서만 호출한다. 형식이 어긋나면 null — 남의 드래그를 캘린더 항목으로 오해하지 않는다. */
export function readScheduleDragData(dataTransfer: DataTransfer): ScheduleDragPayload | null {
  const raw = dataTransfer.getData(SCHEDULE_DND_MIME)
  if (raw === '') return null

  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return null
  }
  if (typeof parsed !== 'object' || parsed === null) return null

  const { kind, id, source } = parsed as Record<string, unknown>
  if (typeof id !== 'number' || !Number.isInteger(id) || id <= 0) return null
  if (kind === 'event') return { kind, id }
  if (kind === 'promise' && (source === 'unscheduled' || source === 'calendar')) {
    return { kind, id, source }
  }
  return null
}
