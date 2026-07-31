#!/usr/bin/env node
// 실제 OBS 녹화본으로 컷 위치를 눈으로 검증하는 수동 도구.
// 앱 파이프라인을 전부 연결하기 전에 이걸로 먼저 확인한다 — 자른 위치가 틀린 걸
// 나중에 발견하면 확인 한 번에 방송 한 번이 필요해진다.
//
// buildClipArgs/buildThumbnailArgs를 src/main/ffmpegArgs.ts에서 직접 import한다.
// 인자 문자열을 따로 베껴 쓰지 않고 앱이 실제로 쓰는 코드 경로 그대로 검증하기 위해서다.
//
// 사용법:
//   node --experimental-strip-types scripts/clip-test.mjs <recordingPath> <startSec> <durationSec> [outDir]
//
// 예:
//   node --experimental-strip-types scripts/clip-test.mjs "D:/rec/2026-07-31.mkv" 4495.78 80

import { spawn } from 'node:child_process'
import { mkdirSync, existsSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import ffmpegPath from 'ffmpeg-static'
import { buildClipArgs, buildThumbnailArgs } from '../src/main/ffmpegArgs.ts'

const [, , recordingPath, startSecArg, durationSecArg, outDirArg] = process.argv

if (!recordingPath || !startSecArg || !durationSecArg) {
  console.error(
    '사용법: node --experimental-strip-types scripts/clip-test.mjs <recordingPath> <startSec> <durationSec> [outDir]'
  )
  process.exit(1)
}

if (!existsSync(recordingPath)) {
  console.error(`녹화본을 찾을 수 없어요: ${recordingPath}`)
  process.exit(1)
}

if (!ffmpegPath) {
  console.error(
    'ffmpeg-static이 이 플랫폼용 바이너리를 못 찾았어요. `pnpm install`을 다시 실행해 보세요.'
  )
  process.exit(1)
}

const startSec = Number(startSecArg)
const durationSec = Number(durationSecArg)
const outDir = outDirArg ?? join(tmpdir(), 'laive-clip-test')
mkdirSync(outDir, { recursive: true })

const clipOutput = join(outDir, 'clip.mp4')
const thumbOutput = join(outDir, 'thumb.jpg')

/** stdio를 상속해 ffmpeg 진행 로그를 그대로 터미널에 보여준다 — 사람이 지켜보는 도구라
 * 프로덕션의 runFfmpeg(stderr 꼬리만 버퍼링)와 달리 실시간으로 다 보이는 게 낫다. */
function run(label, args) {
  console.log(`\n--- ${label} ---`)
  console.log(ffmpegPath, args.join(' '))
  return new Promise((resolve, reject) => {
    const child = spawn(ffmpegPath, args, { stdio: 'inherit' })
    child.on('error', reject)
    child.on('close', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`${label} 실패 (exit ${code})`))
    })
  })
}

const input = { input: recordingPath, startSec, durationSec, output: clipOutput }

try {
  await run('클립 컷', buildClipArgs(input))
  await run('썸네일', buildThumbnailArgs({ ...input, output: thumbOutput }))

  const clipSize = statSync(clipOutput).size
  const thumbSize = statSync(thumbOutput).size

  console.log('\n=== 결과 ===')
  console.log(`클립:   ${clipOutput}  (${(clipSize / 1024 / 1024).toFixed(1)} MB)`)
  console.log(`썸네일: ${thumbOutput}  (${(thumbSize / 1024).toFixed(0)} KB)`)
  console.log('\n직접 확인할 것:')
  console.log('  1. 클립을 재생해 시작 지점이 실제 사건과 맞는지 (앞뒤로 밀리지 않았는지)')
  console.log('  2. 오디오와 영상이 어긋나지 않는지')
  console.log('  3. faststart 적용 여부 — ffprobe(별도 설치 필요, ffmpeg-static엔 없음)로:')
  console.log(`     ffprobe -v trace ${clipOutput} 2>&1 | grep -m1 type:.moov`)
  console.log('     moov가 파일 앞쪽에 있으면(=mdat보다 먼저 나오면) 정상. ffprobe가 없으면')
  console.log('     생략해도 된다 — 브라우저에서 재생해 봐서 바로 시작되면 충분하다.')
} catch (e) {
  console.error('\n실패:', e instanceof Error ? e.message : e)
  process.exit(1)
}
