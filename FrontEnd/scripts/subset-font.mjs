/**
 * Wanted Sans Variable 서브셋 생성기.
 *
 * 원본 woff2는 1.26MB다. 한글 음절 11,172자를 전부 담고 있어서인데, 공개 웹
 * (`/discover`·`/p/:slug`)은 전송량의 71%를 이 한 파일이 차지한다.
 *
 * 그래서 KS X 1001 완성형 2,350자 + 라틴·기호만 남긴 서브셋을 만들어 기본 서체로 쓰고,
 * 원본은 폴백 패밀리로 남긴다(assets/fonts/WantedSansVariable.css 참고).
 *
 *   font-family: 'Wanted Sans Variable', 'Wanted Sans Variable Full', ...
 *
 * unicode-range 대신 폴백 패밀리를 쓰는 이유: KS X 1001은 2,350자가 1,613개 구간으로
 * 흩어져 있어 unicode-range로 적으면 CSS에만 14KB가 붙는다. 폴백 패밀리는 브라우저가
 * 글리프 커버리지를 보고 문자 단위로 알아서 고르므로 CSS 비용이 0이다.
 *
 * 트레이드오프: 서브셋에 없는 문자(희귀 음절·한자 등)가 화면에 하나라도 있으면 그때
 * 원본 1.26MB를 받는다. 최악의 경우가 지금과 같아지는 것이라 손해는 아니지만,
 * 스트리머 닉네임처럼 임의 문자가 들어오는 자리가 있어 완전히 없앨 수는 없다.
 *
 * 실행: node scripts/subset-font.mjs
 * 결과: src/renderer/src/assets/fonts/woff2/WantedSansVariable.subset.woff2
 */
import { readFile, writeFile, stat } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import subsetFont from 'subset-font'

const here = dirname(fileURLToPath(import.meta.url))
const FONT_DIR = resolve(here, '../src/renderer/src/assets/fonts/woff2')
const SOURCE = resolve(FONT_DIR, 'WantedSansVariable.woff2')
const OUTPUT = resolve(FONT_DIR, 'WantedSansVariable.subset.woff2')

/**
 * 서브셋에 남길 비(非)한글 영역.
 *
 * 넉넉하게 잡는다 — 여기서 한 글자라도 빠지면 그 문자가 화면에 뜨는 순간 폴백으로
 * 원본 1.26MB를 받게 되므로, 몇 KB 아끼려다 1.26MB를 부르는 쪽이 훨씬 손해다.
 */
const RANGES = [
  [0x0000, 0x00ff], // Basic Latin + Latin-1 Supplement
  [0x0100, 0x017f], // Latin Extended-A
  [0x0180, 0x024f], // Latin Extended-B
  [0x02b0, 0x02ff], // Spacing Modifier Letters
  [0x0300, 0x036f], // Combining Diacritical Marks
  [0x2000, 0x206f], // General Punctuation (…, —, 따옴표)
  [0x2070, 0x209f], // Super/Subscripts
  [0x20a0, 0x20cf], // Currency Symbols (₩)
  [0x2100, 0x214f], // Letterlike Symbols (™, ℃)
  [0x2150, 0x218f], // Number Forms
  [0x2190, 0x21ff], // Arrows (▲▼ 옆 화살표 계열)
  [0x2200, 0x22ff], // Mathematical Operators
  [0x2300, 0x23ff], // Miscellaneous Technical
  [0x2460, 0x24ff], // Enclosed Alphanumerics
  [0x25a0, 0x25ff], // Geometric Shapes (▲▼◆)
  [0x2600, 0x26ff], // Miscellaneous Symbols (★☆)
  [0x2700, 0x27bf], // Dingbats (✓)
  [0x3000, 0x303f], // CJK Symbols and Punctuation (「」·)
  [0x3130, 0x318f], // Hangul Compatibility Jamo (ㄱ, ㅏ)
  [0xff00, 0xffef] // Halfwidth and Fullwidth Forms
]

function expand(ranges) {
  let out = ''
  for (const [start, end] of ranges) {
    for (let c = start; c <= end; c++) out += String.fromCodePoint(c)
  }
  return out
}

const kb = (n) => `${(n / 1024).toFixed(1)}kB`

/*
 * KS X 1001 완성형 2,350자. 아래로 생성했다(파이썬 euc_kr 코덱은 CP949 확장까지
 * 받아 11,172자가 전부 통과하므로, 인코딩 결과가 KS X 1001 본래 영역인 것만 남긴다):
 *
 *   def ks(c):
 *       try: b = chr(c).encode('euc-kr')
 *       except Exception: return False
 *       return len(b) == 2 and 0xA1 <= b[0] <= 0xFE and 0xA1 <= b[1] <= 0xFE
 *   ''.join(chr(c) for c in range(0xAC00, 0xD7A4) if ks(c))
 */
const hangul = await readFile(resolve(here, 'data/ksx1001-hangul.txt'), 'utf-8')
const text = expand(RANGES) + hangul

const source = await readFile(SOURCE)
// targetFormat만 지정하고 variationAxes는 건드리지 않는다 — 축을 고정하면 가변 폰트가
// 아니게 되어 font-weight 400~1000 구간이 전부 같은 굵기로 렌더된다.
const subset = await subsetFont(source, text, { targetFormat: 'woff2' })
await writeFile(OUTPUT, subset)

const before = (await stat(SOURCE)).size
const after = (await stat(OUTPUT)).size
console.log(`원본   ${kb(before)}  ${SOURCE}`)
console.log(`서브셋 ${kb(after)}  ${OUTPUT}`)
console.log(`감소   ${kb(before - after)} (${(100 - (after / before) * 100).toFixed(1)}%)`)
console.log(`문자수 ${hangul.length} 한글 + ${text.length - hangul.length} 기타`)
