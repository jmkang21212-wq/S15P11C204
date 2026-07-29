import { useState } from 'react'

interface AnnouncementDraftProps {
  content: string
}

/**
 * 저장 직후 노출되는 AI 공지 문구 제안 카드.
 * 복사는 클립보드로 동작하고, 게시는 실제 플랫폼 게시(TODO)로 남겨둔다.
 */
export default function AnnouncementDraft({ content }: AnnouncementDraftProps): React.JSX.Element {
  const [copied, setCopied] = useState(false)

  const handleCopy = (): void => {
    void navigator.clipboard.writeText(content).then(() => {
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1500)
    })
  }

  return (
    <div className="mt-auto rounded-xl border border-success/40 bg-success/5 p-4">
      <div className="mb-2 flex items-center gap-2">
        <span className="rounded bg-success px-1.5 py-0.5 text-micro font-bold text-success-foreground">
          AI 생성
        </span>
        <span className="text-xs font-bold text-success">공지 문구 제안</span>
      </div>
      <p className="mb-3 rounded-lg border bg-card px-3 py-2.5 text-xs leading-relaxed">{content}</p>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={handleCopy}
          className="flex-1 rounded-lg border border-success/40 bg-card py-2 text-xs font-semibold text-success"
        >
          {copied ? '복사됨 ✓' : '복사'}
        </button>
        {/* TODO: POST /schedule-announcements/{id}/publish */}
        <button
          type="button"
          className="flex-1 rounded-lg bg-success py-2 text-xs font-semibold text-success-foreground"
        >
          게시
        </button>
      </div>
      <p className="mt-2 text-micro text-muted-foreground">
        * 게시 대상 플랫폼은 연동 권한 범위에 따라 결정
      </p>
    </div>
  )
}
