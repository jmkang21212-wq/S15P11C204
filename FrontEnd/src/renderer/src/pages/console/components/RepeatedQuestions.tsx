import { formatCount } from '@renderer/lib/format'
import type { RepeatedQuestion } from '@renderer/types/console'

export default function RepeatedQuestions({
  questions
}: {
  questions: RepeatedQuestion[]
}): React.JSX.Element {
  return (
    <div className="border-t pt-4">
      <div className="mb-3 text-xs font-semibold text-muted-foreground">반복 질문 Top 5</div>
      {/* 백엔드 반복 질문 파이프라인이 아직 없어 실제로는 대부분 빈 상태다 */}
      {questions.length === 0 && (
        <p className="rounded-lg bg-muted/40 px-3 py-4 text-center text-caption text-muted-foreground">
          아직 반복되는 질문이 없어요
        </p>
      )}
      <ul className="flex flex-col gap-2.5">
        {questions.map((q) => (
          <li key={q.questionId} className="flex items-center gap-2.5">
            <span className="flex-1 text-body">{q.question}</span>
            <span className="rounded bg-accent px-1.5 py-0.5 font-mono text-caption font-bold text-accent-foreground">
              {formatCount(q.occurrenceCount)}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
