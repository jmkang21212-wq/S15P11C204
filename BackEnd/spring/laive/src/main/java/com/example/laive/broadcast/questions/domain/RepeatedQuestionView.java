package com.example.laive.broadcast.questions.domain;

import java.time.Instant;

/**
 * 콘솔에 노출할 반복 질문 한 건이다. dismissed(답변 완료)는 조회 단계에서 이미 걸러진다.
 *
 * @param type    FAQ | CONTEXT | null(판정 전). "전체 / FAQ만" 필터는 프론트 몫
 * @param reasked 답변 완료 후 질문이 다시 늘어 재노출됐다 — 답이 시청자에게 닿지 않았다는 신호
 */
public record RepeatedQuestionView(
        long questionId,
        String text,
        String type,
        int askers,
        int messages,
        boolean reasked,
        Instant firstAskedAt,
        Instant lastAskedAt
) {
}
