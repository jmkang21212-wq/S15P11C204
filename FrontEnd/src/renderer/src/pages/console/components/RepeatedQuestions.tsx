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
