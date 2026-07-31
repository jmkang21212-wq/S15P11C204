package com.example.laive.broadcast.questions.dto.response;

import java.time.Instant;

/**
 * 답변 완료 처리 결과다.
 *
 * @param dismissedAt 처리 시각. 이미 처리된 질문에 다시 호출하면(멱등) 첫 처리 시각이 온다
 */
public record RepeatedQuestionDismissResponse(long questionId, Instant dismissedAt) {
}
