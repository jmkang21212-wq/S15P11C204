/**
 * 새로 받은 객체에서 값이 바뀌지 않은 조각은 이전 참조를 그대로 물려준다.
 *
 * 폴링 응답은 매번 새로 파싱되므로 값이 같아도 참조가 전부 달라진다. 그대로 내려보내면
 * `memo`가 걸린 컴포넌트도 얕은 비교에서 매번 "달라졌다"로 판정돼 전부 다시 렌더된다.
 *
 * 최상위 키 단위로만 비교하는 것이 핵심이다. 실제 방송에서는 `session.elapsedSeconds`가
 * 매 tick 바뀌므로 객체 전체를 한 덩어리로 비교하는 방식은 아무것도 걸러내지 못한다.
 * 조각별로 보면 여론·하이라이트·열성 시청자는 대부분의 tick에서 그대로다.
 *
 * 측정: perf/console-render.bench.tsx
 */

/** 순수 JSON 값(객체·배열·원시값)에 대한 구조 비교. */
function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (a === null || b === null) return false
  if (typeof a !== 'object' || typeof b !== 'object') return false

  if (Array.isArray(a) || Array.isArray(b)) {
    if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) return false
    return a.every((item, i) => deepEqual(item, b[i]))
  }

  const aRecord = a as Record<string, unknown>
  const bRecord = b as Record<string, unknown>
  const aKeys = Object.keys(aRecord)
  if (aKeys.length !== Object.keys(bRecord).length) return false
  // 키 순서는 보지 않는다 — 서버 직렬화 순서에 기대면 안 된다.
  return aKeys.every((key) => Object.hasOwn(bRecord, key) && deepEqual(aRecord[key], bRecord[key]))
}

/**
 * @param prev 직전에 쓰던 객체. 없으면 `next`를 그대로 돌려준다.
 * @param next 새로 받은 객체.
 * @returns 바뀐 조각만 새 참조인 객체. 전부 그대로면 `prev` 자체를 돌려준다.
 */
export function reuseUnchanged<T extends object>(prev: T | null | undefined, next: T): T {
  if (!prev) return next

  const prevRecord = prev as Record<string, unknown>
  const nextRecord = next as Record<string, unknown>
  const nextKeys = Object.keys(nextRecord)

  let changed = nextKeys.length !== Object.keys(prevRecord).length
  const merged: Record<string, unknown> = {}

  for (const key of nextKeys) {
    const before = prevRecord[key]
    const after = nextRecord[key]
    if (Object.hasOwn(prevRecord, key) && deepEqual(before, after)) {
      merged[key] = before
    } else {
      merged[key] = after
      changed = true
    }
  }

  // 전부 그대로면 새 객체를 만들지 않는다 — 이걸 돌려줘야 상위에서도 setState가 생략된다.
  return changed ? (merged as T) : prev
}
