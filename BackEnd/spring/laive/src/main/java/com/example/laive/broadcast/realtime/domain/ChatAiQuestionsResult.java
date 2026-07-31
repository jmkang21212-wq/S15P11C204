package com.example.laive.broadcast.realtime.domain;

import java.time.Instant;
import java.util.List;

/**
 * chat-ai 반복 질문 폴링(계약 §3) 응답이다.
 *
 * @param questions        askers 내림차순 Top N. 최소 3명 미만 질문은 AI가 이미 걸렀다
 * @param totalQuestions   방송 전체에서 질문으로 분류된 메시지 총수
 * @param pendingEmbedding 아직 클러스터에 반영되지 않은 대기 건수. 0이 아니면 목록이 조금 늦다
 * @param updatedAt        AI측 집계 갱신 시각
 */
public record ChatAiQuestionsResult(
        List<Question> questions,
        long totalQuestions,
        long pendingEmbedding,
        Instant updatedAt
) {

    /**
     * @param questionId AI가 발급하는 불투명 식별자(BIGINT 16자리). 해석하지 말 것
     * @param type       "FAQ" | "CONTEXT" | null(판정 전). 모르는 값은 게이트웨이가 null로 눌러쓴다
     * @param askers     유니크 질문자 수 — 정렬·재질문 판정 기준
     * @param messages   총 메시지 수. askers와 크게 벌어지면 한 명의 도배
     */
    public record Question(
            long questionId,
            String text,
            String type,
            int askers,
            int messages,
            Instant firstAskedAt,
            Instant lastAskedAt
    ) {
    }
}
