import { defineConfig } from 'eslint/config'
import tseslint from '@electron-toolkit/eslint-config-ts'
import eslintConfigPrettier from '@electron-toolkit/eslint-config-prettier'
import eslintPluginReact from 'eslint-plugin-react'
import eslintPluginReactHooks from 'eslint-plugin-react-hooks'
import eslintPluginReactRefresh from 'eslint-plugin-react-refresh'

export default defineConfig(
  { ignores: ['**/node_modules', '**/dist', '**/out'] },
  // public/의 정적 자산(AudioWorklet 등)은 번들러·TS 변환을 거치지 않고 그대로 서빙된다.
  // AudioWorkletGlobalScope에서 도는 평범한 JS라 프로젝트 TS 규칙을 적용하지 않는다.
  { ignores: ['src/renderer/public/**'] },
  tseslint.configs.recommended,
  eslintPluginReact.configs.flat.recommended,
  eslintPluginReact.configs.flat['jsx-runtime'],
  {
    settings: {
      react: {
        version: 'detect'
      }
    }
  },
  {
    files: ['**/*.{ts,tsx}'],
    plugins: {
      'react-hooks': eslintPluginReactHooks,
      'react-refresh': eslintPluginReactRefresh
    },
    rules: {
      ...eslintPluginReactHooks.configs.recommended.rules,
      ...eslintPluginReactRefresh.configs.vite.rules,
      // 코드베이스가 "쓰지 않는 인자/변수는 _ 접두어"를 관례로 쓰고 있어
      // (예: api/schedule.ts의 _from/_to) 규칙에도 반영한다.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }
      ]
    }
  },
  eslintConfigPrettier,
  // shadcn/ui CLI가 생성하는 벤더 코드. 손으로 고치지 않고 CLI로 다시 받는 파일이라
  // 프로젝트 코드 규칙(명시적 반환 타입, 포매팅 등)을 강제하지 않는다.
  {
    files: ['src/renderer/src/components/ui/**/*.{ts,tsx}'],
    rules: {
      '@typescript-eslint/explicit-function-return-type': 'off',
      'react-refresh/only-export-components': 'off',
      'prettier/prettier': 'off',
      'no-restricted-syntax': 'off'
    }
  },
  // 디자인 토큰 우회 방지 — text-[Npx]·#hex·shadow-[...]를 직접 쓰는 대신
  // assets/globals.css·assets/theme/*의 토큰 클래스를 쓰도록 유도한다.
  // 정리 시점에 흡수하지 못한 잔여 임의값이 있어 에러가 아니라 경고로 둔다 —
  // 에러면 "lint 0 에러"가 무너지고, 경고면 새로 추가되는 것만 리뷰에서 눈에 띈다.
  {
    files: ['src/renderer/src/**/*.{ts,tsx}'],
    ignores: ['src/renderer/src/components/ui/**'],
    rules: {
      'no-restricted-syntax': [
        'warn',
        {
          selector: 'Literal[value=/text-\\[[\\d.]+px\\]/]',
          message:
            '타이포 임의값 대신 text-title/body/caption/metric 등 토큰 클래스를 쓰세요 (assets/globals.css).'
        },
        {
          selector: 'TemplateElement[value.raw=/text-\\[[\\d.]+px\\]/]',
          message:
            '타이포 임의값 대신 text-title/body/caption/metric 등 토큰 클래스를 쓰세요 (assets/globals.css).'
        },
        {
          selector: 'Literal[value=/#[0-9a-fA-F]{3,8}\\b/]',
          message: '하드코딩 색 대신 팔레트/시맨틱 토큰을 쓰세요 (assets/theme/*.css).'
        },
        {
          selector: 'TemplateElement[value.raw=/#[0-9a-fA-F]{3,8}\\b/]',
          message: '하드코딩 색 대신 팔레트/시맨틱 토큰을 쓰세요 (assets/theme/*.css).'
        },
        {
          selector: 'Literal[value=/shadow-\\[/]',
          message:
            '그림자 임의값 대신 shadow-card/shadow-live-ring 같은 토큰 클래스를 쓰세요 (assets/globals.css).'
        },
        {
          selector: 'TemplateElement[value.raw=/shadow-\\[/]',
          message:
            '그림자 임의값 대신 shadow-card/shadow-live-ring 같은 토큰 클래스를 쓰세요 (assets/globals.css).'
        }
      ]
    }
  },
  // lib/brand.ts는 외부 브랜드 hex를 의도적으로 담는 파일이라 제외한다.
  {
    files: ['src/renderer/src/lib/brand.ts'],
    rules: {
      'no-restricted-syntax': 'off'
    }
  }
)
