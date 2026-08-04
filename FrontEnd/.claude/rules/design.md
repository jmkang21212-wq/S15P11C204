---
paths:
  - "**/*.tsx"
  - "**/*.jsx"
  - "**/*.html"
  - "**/*.css"
  - "src/components/**/*"
---

# LAIVE — UI 디자인 정의서

Studio Light 디자인 시스템 규칙. 컴포넌트/화면 구현 시 참고.

## 0. ⚠️ 지켜야 할 것

- **컴포넌트는 목적에 맞게 분리한다**: 화면 단위로 몰아넣지 말고, 재사용 가능한 단위(버튼, 카드, 배지 등)로 나눈다. GNB처럼 여러 화면에서 쓰이는 요소는 반드시 공용 컴포넌트로 뺀다. 다만 처음부터 공용으로 만들지는 않는다 — **같은 패턴의 두 번째 사용처가 생기면 그때 `components/common/`으로 승격**한다(`MetricCard`·`Avatar`·`StatusBadge` 등이 이렇게 만들어졌다).
- **모더레이션 관련 요소는 구현하지 않는다**: 신고 피드, 처리 큐, OBS 오버레이 경고 문구 등은 스펙에서 제외됨.
- **로딩/빈 상태/에러 상태를 모두 구현한다**: 데이터가 없거나 로딩 중이거나 실패했을 때의 UI를 빠뜨리지 않는다. 빈/에러 상태는 `components/common/StateView.tsx`의 `EmptyState`/`ErrorState`를 쓴다.
- **UI 컴포넌트는 shadcn/ui만 사용한다**: 커스텀 UI 라이브러리(MUI, Ant Design, Chakra 등)나 직접 만든 컴포넌트로 대체하지 않는다. base는 Base UI를 사용한다. 차트/KPI 카드 등 shadcn/ui가 커버하지 못하는 데이터 시각화 컴포넌트는 Tremor를 병행 허용한다. `components/ui/`는 CLI 생성물이라 손으로 고치지 않는다 — 디자인 결정이 필요하면 `components/common/`에 감싸는 컴포넌트를 만든다.
- **색·타이포 크기는 항상 토큰 클래스로 쓴다**: `text-[18px]`·`bg-[#4338ca]`·`shadow-[0_1px_2px_...]`처럼 임의값을 직접 쓰지 않는다. 아래 §1의 토큰 클래스를 쓴다. ESLint `no-restricted-syntax`가 경고로 잡아준다.

## 1. 디자인 토큰 — Studio Light

밝은 뉴트럴 배경 위에 인디고 액센트. 방송 중 상태만 강한 레드로 구분한다.

### 1.1 토큰 구조 (3층)

토큰은 한 파일이 아니라 3층으로 나뉘어 있다 — 디자인 시스템을 통째로 바꾸더라도 화면 코드는 건드리지 않기 위해서다.

```
assets/theme/palette-studio-light.css   1층. 원시값("무슨 색인가"). 시스템을 바꾸면 이 파일만 교체
        ↓
assets/theme/semantic.css               2층. 역할("어디에 쓰는가"). [data-theme]/[data-surface]로 전환
        ↓
assets/globals.css (@theme inline)      3층. 실제 클래스(text-title, bg-primary 등)
```

화면 코드는 3층의 유틸리티 클래스만 알면 된다. 팔레트를 바꾸는 작업은 1층 파일 교체만으로 끝나야 하고, 실제로 그렇게 동작하는지 팔레트 전체를 스왑해서 검증된 상태다.

### 1.2 색

| 토큰 | 값 | 용도 |
|---|---|---|
| Primary | `#4338ca` | 액센트, 활성 내비, 주요 버튼, 링크 |
| Primary Soft | `#eef0fb` | 활성 내비 배경, 태그 |
| Success | `#16a34a` | 완료, 증가 지표, 연결됨 |
| Danger | `#e11d48` | LIVE, 위험, 경고 배너 |
| Warning | `#d97706` | 주의, 토큰 만료 |
| Surface | `#fff` / `#f5f6f8` | 카드 / 화면 배경 |
| Text | `#191c22` / `#4a4f5a` / `#8a909c` | 본문 / 보조 / 캡션 |
| Border | `rgba(0,0,0,.08~.10)` | 구분선, 카드 테두리 |

화면에서는 위 값을 직접 쓰지 않고 `bg-primary`·`text-success`·`border-border` 같은 유틸리티 클래스로 쓴다. 상태 배지(성공/경고/위험/중립) 색은 `components/common/StatusBadge.tsx`가 단일 소유자다 — 배지 색 규칙을 바꿀 일이 있으면 그 파일 하나만 고친다.

### 1.3 타이포

본문 Wanted Sans, 수치·ID JetBrains Mono. 자간 `-.01em ~ -.03em`.

| 토큰 클래스 | 크기 | 용도 |
|---|---|---|
| `text-display` | 24px | 공개 뷰(비인증 웹) 히어로 제목 — 앱 내부 제목과 구분 |
| `text-title` | 18px/700 | 화면 제목 |
| `text-subtitle` | 14px | 카드·프로필 이름 헤딩 |
| `text-body` | 13px | 본문 |
| `text-caption` | 11px | 캡션 (design.md 기준 11–12px 대) |
| `text-micro` | 10px | 뱃지·미니 위젯처럼 캡션보다도 작은 지점 |
| `text-metric` | 28px/800 | KPI 카드 등 큰 지표 숫자 |
| `text-metric-sm` | 20px | 카드 안에 작게 들어가는 보조 지표 숫자 |

숫자·ID는 항상 `font-mono`로 렌더한다.

### 1.4 형태 · elevation

카드 `radius 14px` + `shadow-card`(`0 10px 34px rgba(0,0,0,.09)`), 내부 요소 `9~12px`, 칩·배지 `20px`. 간격은 4px 배수(Tailwind 기본 스케일로 충분 — 별도 간격 토큰 없음). LIVE 표시 점 둘레 링은 `shadow-live-ring`.

### 1.5 방송 오버레이 표면 (data-surface)

미니 위젯(SC-03 부속)과 OBS 오버레이는 방송 화면 위에 얹히는 표면이라 앱 테마와 무관하게 **항상 어둡다**. `[data-theme]`(앱 전체 라이트/다크)와는 별개 축으로, `data-surface="overlay"`(일반) / `data-surface="overlay-alert"`(HIGH 위험 강조)를 루트에 붙이면 그 안에서는 `bg-card`·`text-foreground`·`bg-primary` 같은 평범한 유틸리티 클래스가 자동으로 어두운 값을 받는다. 미니 위젯 전용 강조색(`text-briefing-risk` 등)과 OBS 게이지 그라디언트(`from-gauge-from to-gauge-to`)도 이 스코프 안에서만 값이 채워진다.

### 1.6 외부 브랜드 색은 토큰화하지 않는다

네이버·치지직·유튜브 색은 각 서비스의 브랜드 가이드라인이 정한 값이라 우리 디자인 시스템을 바꿔도 그대로여야 한다. `assets/theme/*`에 넣지 않고 `lib/brand.ts` 한 곳에 상수로 모아 "의도적으로 토큰이 아님"을 드러낸다.

## 2. 레이아웃

- Electron 창: 최소 폭 1240px 기준으로 디자인하고, 그 이상 폭에서는 여백 또는 사이드바 확장으로 대응한다.
- 좌측 GNB는 펼침 232px / 접힘 60px 두 폭을 사용자가 토글 버튼으로 직접 전환한다(상태는 앱 전체 공유, 세션 한정). 우측 콘텐츠는 유동 폭.
- 콘텐츠는 2열(주 + 사이드 320~360px) 또는 카드 그리드.

## 3. 공용 컴포넌트 (`components/common/`)

여러 화면이 공유하는 컴포넌트는 전부 여기 있다. 새 화면에서 아래와 겹치는 UI가 필요하면 먼저 이 목록을 확인한다.

| 컴포넌트 | 용도 |
|---|---|
| `GNB` | 좌측 내비. props: `live`(boolean), `liveSessionId`. 펼침/접힘은 `useGnbCollapsed` 훅으로 화면 간 공유하며 상단 토글 버튼(`->\|`/`\|<-`)으로 사용자가 전환한다. 상단 로고+토글 → LIVE 배너(`live`일 때만) → 메뉴 8개 → 하단 연동 상태 + 계정. 연동 계정이 여러 개면 `연동됨 · N개 채널`로 뭉쳐 보여준다. 활성 항목만 Primary Soft 배경 + 인디고 텍스트, 아이콘은 24 viewBox 스트로크 1.8. |
| `StateView` (`EmptyState`/`ErrorState`) | 빈/에러 상태 표준 컴포넌트. `EmptyState`는 `icon`/`title`/`description`/`action`, `ErrorState`는 `message`/`onRetry`를 받는다. 모든 화면이 이거 하나로 통일돼 있다. |
| `StatusBadge` | 상태 배지. 도메인 상태 타입을 직접 받지 않고 `tone`(`success`/`warning`/`danger`/`neutral`) + `label`만 받는다 — 도메인→tone 매핑은 각 화면이 갖는다. |
| `DeltaText` | 증감 표기 — §6 "Success/Danger 텍스트 + 화살표, 수치는 mono". `digits` prop으로 소수점 자리 조정. |
| `SentimentBar` | 긍정/중립/부정 3분할 스택 바. 범례는 화면마다 레이아웃이 달라 공용화하지 않고 각자 둔다. |
| `PageHeader` | 화면 상단 `title` + `caption` + 우측 `actions` 슬롯. 컨테이너(`max-w`)는 페이지마다 달라 여기서 묶지 않는다. |
| `MetricCard` | KPI 카드. `delta`(증감), `onSelect`/`selected`(차트 지표 선택기로도 사용) 지원. |
| `Avatar` | 프로필 이미지, CSP로 로드 실패 시 사람 아이콘 폴백. |
| `ClipThumbnail` | 클립 썸네일, 생성 중(`null`)이거나 로드 실패 시 필름 아이콘 폴백. |
| `SessionSelect` | 종료 세션 선택 드롭다운. |
| `BrowserChrome` | 공개 뷰(SC-10/SC-11)를 감싸는 가짜 브라우저 크롬 — §5.2 참고. |

## 4. 화면 목록

| ID | 화면 | 플랫폼 | 핵심 |
|---|---|---|---|
| SC-01a | 로그인 | Electron | 인증 게이트. 성공 → SC-01b |
| SC-01b | 플랫폼 연동 / 재연동 | Electron | 온보딩 필수 단계 겸 토큰 만료 시 재연동 |
| SC-01c | 회원가입 | Electron | 가입 완료 → 바로 연동 단계 |
| SC-02 | 대시보드 | Electron | 방송 전 30초 상태 파악 허브 |
| SC-03 | 방송 콘솔 (Track A) | Electron | 세컨드 모니터 상시 표시 |
| SC-03 부속 | 미니 모드 · OBS 오버레이 | Electron / OBS | 축소 위젯 + 방송 화면용 오버레이. §1.5 참고 |
| SC-04 | 일정 캘린더 | Electron | 일정 관리 + 공지 발행 |
| SC-06 | 세션 리포트 | Electron | 방송 직후 5분 복기 |
| SC-07 | 하이라이트 / 클립 | Electron | 생성 중·완료 클립 그리드 |
| SC-09 | 성장 리포트 | Electron | 주간/월간 추이 + 목표 로드맵 |
| SC-10 | 포트폴리오 | Electron + Web | 편집 화면 + 인증 불필요 공개 뷰 |
| SC-11 | 비즈니스 매칭 | Electron + Web | 스트리머·광고주 공용 · `streampilot.tv/discover` 공개 디렉터리 |

## 5. 주요 설계 결정

1. **SC-01 3분할** — 인증(01a) / 연동(01b) / 가입(01c)을 분리. 연동 화면을 재사용해 토큰 만료 시 재연동 진입점으로 씀.
2. **Web 공개 뷰 이원화** — SC-10 포트폴리오와 SC-11 디렉터리 모두 브라우저 크롬을 씌운 비인증 뷰를 별도 구현. SC-11에서 클릭 → SC-10 공개 뷰로 이동.

## 6. 상태 표현 규칙

- LIVE: 레드 도트 + `shadow-live-ring`, 배경 `#fef2f2` / 테두리 `#fecaca`.
- 연결됨: 그린 도트, 만료: 앰버 배너 + `연동 관리` CTA.
- 증가/감소: `DeltaText` 컴포넌트 — Success / Danger 텍스트 + 화살표, 수치는 mono.
- 진행 중(클립 생성 등): 뉴트럴 프로그레스 바 + 캡션.
- 상태 배지(완료/진행 중/실패 등): `StatusBadge` 컴포넌트 — tone별 색은 §1.2 참고.
