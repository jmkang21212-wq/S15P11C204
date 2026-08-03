import { app, shell, BrowserWindow, ipcMain } from 'electron'
import { join } from 'path'
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

// 창 참조를 모듈 레벨로 둔다 — 미니 창에서 메인 창을 포커스하는 등 창 간 제어에 필요하다.
let mainWindow: BrowserWindow | null = null
let miniWindow: BrowserWindow | null = null

/** dev/prod에 맞춰 렌더러 특정 해시 라우트를 창에 로드한다. (HashRouter 기준) */
function loadRoute(win: BrowserWindow, hashRoute: string): void {
  if (is.dev && process.env['ELECTRON_RENDERER_URL']) {
    win.loadURL(`${process.env['ELECTRON_RENDERER_URL']}#${hashRoute}`)
  } else {
    win.loadFile(join(__dirname, '../renderer/index.html'), { hash: hashRoute })
  }
}

function createWindow(): void {
  const win = new BrowserWindow({
    width: 1280,
    height: 800,
    show: false,
    autoHideMenuBar: true,
    ...(process.platform === 'linux' ? { icon } : {}),
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

  // HMR for renderer base on electron-vite cli.
  // Load the remote URL for development or the local html file for production.
  if (is.dev && process.env['ELECTRON_RENDERER_URL']) {
    win.loadURL(process.env['ELECTRON_RENDERER_URL'])
  } else {
    win.loadFile(join(__dirname, '../renderer/index.html'))
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
    width: 340,
    height: 220,
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
