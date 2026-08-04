package com.example.laive.broadcast.summary.dto.response;

import com.example.laive.broadcast.realtime.domain.ChatAiSummary;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 수동(MANUAL) 여론 요약 요청의 결과다. 쿨다운·생성 중·채팅 부족은 오류가 아니라
 * (계약 §2) 버튼 상태를 그리는 정보라서, 오류 envelope 대신 200과 status 유니온으로
 * 내려보낸다 — {@code ApiErrorResponse}에는 retryAfterSec을 실을 자리가 없다.
 *
 * @param status          CREATED | COOLDOWN | IN_FLIGHT | NOT_ENOUGH_CHATS
 * @param summary         생성된 요약 텍스트. CREATED에만 있다
 * @param generatedAt     요약 생성 시각. CREATED에만 있다
 * @param nextAvailableAt 다음 요약이 가능해지는 시각. 버튼 비활성화+카운트다운의
 *                        근거다(CREATED·COOLDOWN)
 * @param retryAfterSec   다시 시도까지 남은 초(COOLDOWN·IN_FLIGHT). IN_FLIGHT는
 *                        약 2초 뒤 완성분이 live-snapshot 브리핑으로 내려온다
 * @param chatCount       현재 윈도우 채팅 수. NOT_ENOUGH_CHATS에만 있다
 * @param required        요약에 필요한 최소 채팅 수. NOT_ENOUGH_CHATS에만 있다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatSummaryCreateResponse(
        String status,
        String summary,
        Instant generatedAt,
        Instant nextAvailableAt,
        Integer retryAfterSec,
        Long chatCount,
        Integer required
) {

    /** 계약: IN_FLIGHT를 받으면 재시도하지 말고 2초쯤 뒤 결과를 회수한다. */
    private static final int IN_FLIGHT_RETRY_SEC = 2;

    public static ChatSummaryCreateResponse created(ChatAiSummary summary) {
        return new ChatSummaryCreateResponse(
                "CREATED", summary.summary(), summary.generatedAt(), summary.nextAvailableAt(),
                null, null, null);
    }

    public static ChatSummaryCreateResponse cooldown(int retryAfterSec, Instant nextAvailableAt) {
        return new ChatSummaryCreateResponse(
                "COOLDOWN", null, null, nextAvailableAt, retryAfterSec, null, null);
    }

    public static ChatSummaryCreateResponse inFlight() {
        return new ChatSummaryCreateResponse(
                "IN_FLIGHT", null, null, null, IN_FLIGHT_RETRY_SEC, null, null);
    }

    public static ChatSummaryCreateResponse notEnoughChats(long chatCount, int required) {
        return new ChatSummaryCreateResponse(
                "NOT_ENOUGH_CHATS", null, null, null, null, chatCount, required);
    }
}
