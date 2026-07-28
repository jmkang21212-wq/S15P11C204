/**
 * Electron 브릿지(`window.api`) 래퍼.
 *
 * 렌더러를 브라우저로 직접 열면(개발 중 localhost:5173) preload가 주입되지 않아
 * `window.api`가 없다. 그대로 호출하면 터지므로 `window.open`으로 떨어뜨린다.
 * Electron 안에서도 `window.open`은 main의 setWindowOpenHandler가 가로채
 * 기본 브라우저로 열어 주므로 두 환경에서 모두 동작한다.
 */

/** 기본 브라우저로 URL을 연다. */
export function openExternal(url: string): void {
  if (window.api?.openExternal) {
    void window.api.openExternal(url)
    return
  }
  window.open(url, '_blank', 'noopener')
}

/** 파일을 내려받는다. 브릿지가 없으면 브라우저 기본 동작에 맡긴다. */
export function downloadFile(url: string): void {
  if (window.api?.downloadFile) {
    void window.api.downloadFile(url)
    return
  }
  window.open(url, '_blank', 'noopener')
}
