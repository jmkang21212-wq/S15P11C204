import { app, ipcMain } from 'electron'
import { join } from 'path'
import { existsSync } from 'fs'
import { mkdir, readFile, rm } from 'fs/promises'
import { randomUUID } from 'crypto'
import { buildClipArgs, buildThumbnailArgs, resolveFfmpegPath, runFfmpeg } from './ffmpeg'

/**
 * 클립 컷 IPC — 서버 업로드는 렌더러가 한다(main은 HTTP 클라이언트·토큰을 갖지 않는다).
 * main은 ffmpeg 실행과 임시 파일 관리만 맡고, 잘라낸 바이트를 IPC로 렌더러에 넘긴다.
 */

export interface ClipCutRequest {
  /** 스트리머 PC의 OBS 녹화본 경로. */
  recordingPath: string
  /** 서버가 보정까지 끝낸 컷 시작 지점(초). */
  clipStartSec: number
  clipDurationSec: number
}

export type ClipCutResult =
  | { ok: true; clip: Uint8Array; thumbnail: Uint8Array }
  | {
      ok: false
      /** true면 재시도해도 같은 실패(녹화본 소실 등) — 즉시 포기해야 한다. */
      permanent: boolean
      reason: string
    }

export function registerClipIpc(): void {
  ipcMain.handle('clip:cut', async (_e, request: ClipCutRequest): Promise<ClipCutResult> => {
    const { recordingPath, clipStartSec, clipDurationSec } = request

    if (!existsSync(recordingPath)) {
      // 원본이 사라졌다 — 다시 시도해도 똑같이 실패하니 재시도 횟수를 쓰지 않는다.
      return { ok: false, permanent: true, reason: `녹화본을 찾을 수 없어요: ${recordingPath}` }
    }

    // 작업마다 고유 폴더를 써서, 직전 작업의 정리가 실패로 남아 있어도 서로 덮어쓰지 않는다.
    const workDir = join(app.getPath('temp'), 'laive-clip', randomUUID())
    const clipPath = join(workDir, 'clip.mp4')
    const thumbPath = join(workDir, 'thumb.jpg')

    try {
      await mkdir(workDir, { recursive: true })
      const ffmpegPath = resolveFfmpegPath()
      const input = {
        input: recordingPath,
        startSec: clipStartSec,
        durationSec: clipDurationSec,
        output: clipPath
      }

      await runFfmpeg(ffmpegPath, buildClipArgs(input))
      await runFfmpeg(ffmpegPath, buildThumbnailArgs({ ...input, output: thumbPath }))

      const [clip, thumbnail] = await Promise.all([readFile(clipPath), readFile(thumbPath)])
      return { ok: true, clip, thumbnail }
    } catch (e) {
      // ffmpeg 실패·일시적 디스크 문제 등 — 다시 해볼 만하다.
      return { ok: false, permanent: false, reason: e instanceof Error ? e.message : String(e) }
    } finally {
      // 원본은 절대 건드리지 않는다 — 지우는 건 workDir(우리가 만든 임시 파일)뿐이다.
      await rm(workDir, { recursive: true, force: true }).catch(() => undefined)
    }
  })
}
