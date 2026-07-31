/**
 * ffmpeg 인자 생성 — 순수 함수만 둔다. Electron·Node 내장 모듈에도 의존하지 않는다.
 *
 * `scripts/clip-test.mjs`가 이 파일을 곧바로 import해서 실제 앱이 만드는 것과 똑같은
 * 인자로 컷 테스트를 돌린다. Electron을 import하는 코드가 섞이면 일반 node로 실행하는
 * 검증 스크립트가 깨지므로, 그 경계를 이 파일이 지킨다(`ffmpeg.ts` 참고).
 */

export interface ClipCutInput {
  /** OBS 로컬 녹화본 경로. */
  input: string
  /** 서버가 계산해 준, 보정이 끝난 컷 시작 지점(초). */
  startSec: number
  durationSec: number
  output: string
}

/**
 * 하이라이트 구간을 잘라 720p mp4로 인코딩한다.
 * `-movflags +faststart`는 빼지 않는다 — 없으면 브라우저가 파일을 다 받아야 재생이 시작된다.
 */
export function buildClipArgs({ input, startSec, durationSec, output }: ClipCutInput): string[] {
  return [
    '-y',
    '-ss',
    String(startSec),
    '-i',
    input,
    '-t',
    String(durationSec),
    '-c:v',
    'libx264',
    '-preset',
    'veryfast',
    '-crf',
    '23',
    '-vf',
    'scale=-2:720',
    '-c:a',
    'aac',
    '-b:a',
    '128k',
    '-movflags',
    '+faststart',
    output
  ]
}

/**
 * 구간 중앙 프레임을 360p 썸네일로 뽑는다.
 * `-update 1`이 없으면 image2 먹서가 "파일명에 %d 패턴이 없다"는 경고를 stderr에 남긴다 —
 * 이번 실행은 성공해도, 그 경고가 실패 시 stderr 꼬리(runFfmpeg)에 섞여 진짜 실패 원인과
 * 헷갈릴 수 있다. 정지 이미지 한 장을 쓸 거라는 의도를 명시해 경고 자체를 없앤다.
 */
export function buildThumbnailArgs({
  input,
  startSec,
  durationSec,
  output
}: ClipCutInput): string[] {
  const seekSec = startSec + durationSec / 2
  return [
    '-y',
    '-ss',
    String(seekSec),
    '-i',
    input,
    '-frames:v',
    '1',
    '-update',
    '1',
    '-vf',
    'scale=-2:360',
    '-q:v',
    '3',
    output
  ]
}
