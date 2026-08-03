import { app } from 'electron'
import { join } from 'path'
import { spawn } from 'child_process'

export { buildClipArgs, buildThumbnailArgs } from './ffmpegArgs'
export type { ClipCutInput } from './ffmpegArgs'

/**
 * 번들된 ffmpeg 실행 파일 경로.
 *
 * 개발 중에는 `ffmpeg-static`이 postinstall로 내려받은 사본을 그대로 쓴다.
 * 패키징된 앱에서는 그 사본이 asar 밖에 없다 — 있어도 asar 안 파일은 실행 권한이 없어
 * spawn이 안 먹는다. 그래서 electron-builder.yml의 extraResources가 별도로 복사해 둔
 * `resources/ffmpeg(.exe)`를 가리킨다.
 */
export function resolveFfmpegPath(): string {
  if (app.isPackaged) {
    return join(process.resourcesPath, process.platform === 'win32' ? 'ffmpeg.exe' : 'ffmpeg')
  }
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return require('ffmpeg-static') as string
}

/**
 * ffmpeg를 실행하고 종료를 기다린다. 실패하면 stderr 마지막 줄들을 담아 reject한다 —
 * ffmpeg는 진행 로그를 전부 stderr로 찍기 때문에, 마지막 몇 줄이 대개 실패 원인이다.
 */
export function runFfmpeg(binaryPath: string, args: string[]): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(binaryPath, args)
    const tail: string[] = []

    child.stderr.on('data', (chunk: Buffer) => {
      tail.push(...chunk.toString('utf-8').split('\n').filter(Boolean))
      if (tail.length > 20) tail.splice(0, tail.length - 20)
    })
    child.on('error', reject)
    child.on('close', (code) => {
      if (code === 0) {
        resolve()
      } else {
        reject(new Error(tail.slice(-5).join('\n') || `ffmpeg exited with code ${code}`))
      }
    })
  })
}
