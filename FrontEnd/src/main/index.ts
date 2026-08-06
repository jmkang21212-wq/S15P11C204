import { app, shell, BrowserWindow, ipcMain, protocol, net } from 'electron'
import { join, resolve, relative, isAbsolute } from 'path'
import { pathToFileURL } from 'url'
import { electronApp, optimizer, is } from '@electron-toolkit/utils'
import icon from '../../resources/icon.png?asset'
import { registerObsIpc } from './obs'
import { registerOAuthIpc } from './oauth'
import { registerBriefingIpc } from './briefing'
import { registerSessionIpc } from './session'
import { registerSnapshotIpc } from './snapshot'
import { registerQuestionIpc } from './question'
import { registerClipIpc } from './clip'

/**
 * 렌더러가 넘긴 URL이 외부로 내보내도 되는 주소인지 검증한다.
 * shell.openExternal에 임의 프로토콜(file:, javascript: 등)이 들어가면 위험하므로
 * 허용 목록 방식으로 막는다.
 *
 * `mailto:`는 포트폴리오 공개 뷰(SC-10)의 비즈니스 문의 버튼에서 쓴다 — 기본 메일
 * 클라이언트를 열 뿐이라 안전하다. 다운로드는 여기에 해당하지 않으므로 http/https만 받는다.
 */
function assertAllowedUrl(url: string, allowed: readonly string[]): void {
  const parsed = new URL(url)
  if (!allowed.includes(parsed.protocol)) {
    throw new Error('UNSUPPORTED_PROTOCOL')
  }
}

const OPEN_EXTERNAL_PROTOCOLS = ['http:', 'https:', 'mailto:'] as const
const DOWNLOAD_PROTOCOLS = ['http:', 'https:'] as const

/**
 * 패키지 앱에서 렌더러를 서빙할 커스텀 스킴. `laive://app`
 *
 * <b>왜 file://을 쓰지 않는가</b> — `file://`로 로드하면 브라우저가 페이지의 출처를
 * 특정할 수 없어 API 요청에 `Origin: null`을 붙인다. 백엔드의 CORS 허용 오리진은
 * `OAUTH_ALLOWED_RETURN_URIS`를 파싱해 scheme+host로 만들어지므로(AuthProperties의
 * allowedOrigins) `null`은 목록에 등록할 방법이 아예 없다 — 설치 앱에서 로그인은
 * 물론 공개 조회까지 전부 403이던 원인이다.
 *
 * 호스트를 가진 스킴으로 서빙하면 렌더러가 `laive://app`이라는 진짜 오리진을 갖고,
 * 그 문자열은 허용 목록에 적을 수 있다. `null`을 허용하는 것과 달리 안전하다 —
 * 사용자가 임의로 만든 HTML 파일은 이 오리진을 가질 수 없다.
 *
 * ⚠ 이 값은 세 곳에 같이 박힌다. 바꾸면 셋을 함께 고쳐야 한다.
 *   1. 여기
 *   2. FrontEnd/.env.production 의 VITE_OAUTH_RETURN_URI
 *   3. 서버 .env 의 OAUTH_ALLOWED_RETURN_URIS
 *
 * 개발(`pnpm dev`)은 Vite가 http://localhost:5173으로 서빙하므로 이 경로를 타지
 * 않는다. 그래서 이 변경은 `pnpm build:win`으로만 검증된다.
 */
const RENDERER_SCHEME = 'laive'
const RENDERER_HOST = 'app'
const RENDERER_ORIGIN = `${RENDERER_SCHEME}://${RENDERER_HOST}`

/**
 * 스킴 권한 등록. **`app.whenReady()` 이전에 호출해야 한다** — 그래서 모듈 최상단이다.
 * 나중에 부르면 조용히 무시되고 `file://`과 다를 바 없는 상태가 된다.
 */
protocol.registerSchemesAsPrivileged([
  {
    scheme: RENDERER_SCHEME,
    privileges: {
      // 이 옵션이 핵심이다. 호스트를 가진 스킴으로 취급되어 오리진이 생긴다.
      standard: true,
      // 보안 컨텍스트로 취급된다 — Secure 쿠키(Refresh Token)를 받을 수 있고
      // crypto.subtle 같은 API도 동작한다. SameSite=None은 Secure가 전제다.
      secure: true,
      supportFetchAPI: true,
      // <video>/<audio>의 Range 요청 허용.
      stream: true
    }
  }
])

/**
 * 렌더러 산출물(out/renderer)을 커스텀 스킴으로 서빙한다.
 *
 * HashRouter를 쓰므로 요청되는 경로는 `/index.html`과 `/assets/**`뿐이다. SPA 폴백을
 * 두지 않는다 — 없는 경로를 index.html로 받아주면 자산 경로 오타가 조용히 삼켜져
 * "화면은 뜨는데 스타일이 없는" 상태를 만든다.
 */
function registerRendererProtocol(): void {
  const root = resolve(__dirname, '../renderer')

  protocol.handle(RENDERER_SCHEME, async (request) => {
    const { host, pathname } = new URL(request.url)
    if (host !== RENDERER_HOST) {
      return new Response('Not Found', { status: 404 })
    }

    // 앞에 '.'을 붙여 상대 경로로 만든다 — pathname이 '/'로 시작해 그대로 넘기면
    // resolve가 root를 무시하고 절대 경로로 해석한다.
    const requested = pathname === '/' ? '/index.html' : decodeURIComponent(pathname)
    const target = resolve(root, `.${requested}`)

    // 경로 이탈 방어. `laive://app/../../secrets` 같은 요청으로 앱 밖 파일을 읽지 못하게 한다.
    const rel = relative(root, target)
    if (rel === '' || rel.startsWith('..') || isAbsolute(rel)) {
      return new Response('Forbidden', { status: 403 })
    }

    // net.fetch가 file: URL을 읽고 확장자로 Content-Type까지 정해 준다.
    //
    // 없는 파일이면 ERR_FILE_NOT_FOUND로 **throw한다** — 잡지 않으면 처리되지 않은
    // 예외가 메인 프로세스 로그에 남는다. 실제로 favicon.ico(index.html에 선언이
    // 없어 Chromium이 자동 요청)와 가로채기 직전의 OAuth 복귀 주소가 여기로 온다.
    // 정상적인 "그런 자원 없음"이므로 404로 답하는 것이 맞다.
    try {
      return await net.fetch(pathToFileURL(target).toString())
    } catch {
      // 자산 경로 오타를 조용히 삼키지 않도록 남긴다 — 흰 화면의 유일한 단서가 된다.
      console.warn(`[renderer-protocol] 404 ${pathname}`)
      return new Response('Not Found', { status: 404 })
    }
  })
}

// 창 참조를 모듈 레벨로 둔다 — 미니 창에서 메인 창을 포커스하는 등 창 간 제어에 필요하다.
let mainWindow: BrowserWindow | null = null
let miniWindow: BrowserWindow | null = null

/** dev/prod에 맞춰 렌더러 특정 해시 라우트를 창에 로드한다. (HashRouter 기준) */
function loadRoute(win: BrowserWindow, hashRoute: string): void {
  if (is.dev && process.env['ELECTRON_RENDERER_URL']) {
    win.loadURL(`${process.env['ELECTRON_RENDERER_URL']}#${hashRoute}`)
  } else {
    win.loadURL(`${RENDERER_ORIGIN}/index.html#${hashRoute}`)
  }
}

function createWindow(): void {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    // design.md §2가 "최소 폭 1240px 기준으로 디자인한다"고 정해 둔 값을 창 자체로 강제한다.
    // 그 아래로 줄이면 GNB(펼침 232px) + 2열 본문(주 + 사이드 320~360px)이 들어갈 자리가
    // 없어 콘솔·리포트 화면이 깨진다. 지금까지는 규칙만 있고 막는 곳이 없었다.
    minWidth: 1240,
    minHeight: 720,
    show: false,
    autoHideMenuBar: true,
    icon,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: false
    }
  })
  mainWindow = win

  win.on('ready-to-show', () => win.show())
  win.on('closed', () => {
    if (mainWindow === win) mainWindow = null
  })

  win.webContents.setWindowOpenHandler((details) => {
    shell.openExternal(details.url)
    return { action: 'deny' }
  })

  // 개발은 Vite 개발 서버(HMR), 배포는 커스텀 스킴. 둘 다 오리진을 갖는다.
  if (is.dev && process.env['ELECTRON_RENDERER_URL']) {
    win.loadURL(process.env['ELECTRON_RENDERER_URL'])
  } else {
    win.loadURL(`${RENDERER_ORIGIN}/index.html`)
  }
}

/**
 * 방송 콘솔의 미니 모드 창.
 * 프레임 없는 항상-위 소형 창으로, 방송 화면 위에 두고 쓸 수 있게 한다.
 * 드래그 이동은 렌더러에서 `-webkit-app-region: drag`로 처리한다.
 */
function createMiniWindow(): void {
  if (miniWindow && !miniWindow.isDestroyed()) {
    miniWindow.focus()
    return
  }

  const win = new BrowserWindow({
    // 타이포 스케일을 전체적으로 ~1.15배 올리면서 창도 같은 비율로 키웠다(340×220 → 390×250).
    // resizable: false라 사용자가 넓힐 수 없어, 창이 그대로면 내용이 스크롤에 걸린다.
    width: 390,
    height: 250,
    show: false,
    frame: false,
    resizable: false,
    alwaysOnTop: true,
    skipTaskbar: true,
    webPreferences: {
      preload: join(__dirname, '../preload/index.js'),
      sandbox: false
    }
  })
  miniWindow = win

  win.on('ready-to-show', () => win.show())
  win.on('closed', () => {
    if (miniWindow === win) miniWindow = null
  })

  loadRoute(win, '/console/mini')
}

// This method will be called when Electron has finished
// initialization and is ready to create browser windows.
// Some APIs can only be used after this event occurs.
app.whenReady().then(() => {
  // Set app user model id for windows
  electronApp.setAppUserModelId('com.electron')

  // 창을 만들기 전에 등록해야 첫 로드가 실패하지 않는다.
  // 개발에서는 이 스킴으로 아무것도 로드하지 않으므로 등록해 두어도 무해하다.
  registerRendererProtocol()

  // Default open or close DevTools by F12 in development
  // and ignore CommandOrControl + R in production.
  // see https://github.com/alex8088/electron-toolkit/tree/master/packages/utils
  app.on('browser-window-created', (_, window) => {
    optimizer.watchWindowShortcuts(window)
  })

  // IPC test
  ipcMain.on('ping', () => console.log('pong'))

  // ── 렌더러 → 외부 브라우저 / 다운로드 브릿지 ──
  // SC-07 클립 재생·다운로드와 SC-01b 플랫폼 연동(인증 URL 열기)이 함께 쓴다.

  ipcMain.handle('app:open-external', async (_event, url: string) => {
    assertAllowedUrl(url, OPEN_EXTERNAL_PROTOCOLS)
    await shell.openExternal(url)
  })

  ipcMain.handle('app:download-file', (event, url: string) => {
    assertAllowedUrl(url, DOWNLOAD_PROTOCOLS)
    const window = BrowserWindow.fromWebContents(event.sender)
    if (!window) throw new Error('WINDOW_NOT_FOUND')
    // Electron 기본 다운로드 흐름(저장 다이얼로그)을 탄다.
    window.webContents.downloadURL(url)
  })

  // ── 미니 모드 창 제어 (SC-03 부속) ──
  ipcMain.on('mini:open', () => createMiniWindow())
  ipcMain.on('mini:close', () => {
    if (miniWindow && !miniWindow.isDestroyed()) miniWindow.close()
  })
  ipcMain.on('main:focus', () => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      if (mainWindow.isMinimized()) mainWindow.restore()
      mainWindow.focus()
    }
  })

  // ── OBS(obs-websocket) 연결·제어 ──
  registerObsIpc()

  // ── OAuth 인앱 창 (네이버 로그인 · 플랫폼 연동 공용) ──
  registerOAuthIpc()

  // ── 실시간 브리핑 중계 (콘솔 → 미니 창) ──
  registerBriefingIpc()

  // ── 현재 방송 세션 상태 릴레이 (메인 창 → 미니·오버레이 창) ──
  registerSessionIpc()

  // ── 실시간 콘솔 스냅샷 릴레이 (콘솔 창 → 미니·오버레이 창) ──
  registerSnapshotIpc()

  // ── 반복 질문 답변 완료 요청 릴레이 (미니 창 → 콘솔 창) ──
  registerQuestionIpc()

  // ── 하이라이트 클립 컷 (ffmpeg) ──
  registerClipIpc()

  createWindow()

  app.on('activate', function () {
    // On macOS it's common to re-create a window in the app when the
    // dock icon is clicked and there are no other windows open.
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

// Quit when all windows are closed, except on macOS. There, it's common
// for applications and their menu bar to stay active until the user quits
// explicitly with Cmd + Q.
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

// In this file you can include the rest of your app's specific main process
// code. You can also put them in separate files and require them here.
