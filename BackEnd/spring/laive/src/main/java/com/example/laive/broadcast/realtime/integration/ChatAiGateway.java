package com.example.laive.broadcast.realtime.integration;

import com.example.laive.broadcast.realtime.domain.ChatAiIngestResult;
import com.example.laive.broadcast.realtime.domain.ChatAiQuestionsResult;
import com.example.laive.broadcast.realtime.domain.ChatAiSummary;
import com.example.laive.broadcast.realtime.domain.ChatAiSummaryCall;
import com.example.laive.broadcast.realtime.domain.IngestChat;
import com.example.laive.broadcast.realtime.domain.SummaryMode;

import java.util.List;
import java.util.Optional;

/**
 * 채팅 감정 분류 AI(chat-ai) Port다. 계약 원본은 {@code AI/CHAT_AI_API.md}.
 *
 * <p>{@code ChatCollectorGateway}와 같은 all-default 패턴이다. base-url이 비어
 * 있으면 no-op으로 남아 채팅은 라벨 없이 저장되고 나머지 기능은 그대로 돈다.
 */
public interface ChatAiGateway {

    /**
     * 채팅 배치를 분류한다. 실패는 던지지 않고 비어 있다 — 계약이 "실패 시 재시도
     * 없이 배치 스킵, 원본은 라벨 없이 저장"이므로 호출자는 빈 결과를 그대로
     * null 라벨로 처리하면 된다.
     *
     * @param viewers 최신 시청자 수. 모르면 null (chat-ai가 기존 값을 유지한다)
     */
    default Optional<ChatAiIngestResult> ingest(long sessionId, Integer viewers, List<IngestChat> chats) {
        return Optional.empty();
    }

    /**
     * 반복 질문 Top N을 폴링한다(계약 §3). 실패·404(ingest 이력 없음)는 던지지 않고
     * 비어 있다 — 호출자는 이번 주기를 건너뛰면 된다.
     *
     * @param limit 반환 개수. 계약 최대 50
     */
    default Optional<ChatAiQuestionsResult> getQuestions(long sessionId, int limit) {
        return Optional.empty();
    }

    /**
     * 여론 요약을 새로 생성한다(계약 §2). 409·422 거절은 본문을 가진 정상 응답이라
     * Optional이 아닌 {@link ChatAiSummaryCall}로 전 경우를 돌려준다. no-op에서는
     * AI 미연결과 같은 {@code Failure}다.
     */
    default ChatAiSummaryCall requestSummary(long sessionId, SummaryMode mode) {
        return new ChatAiSummaryCall.Failure();
    }

    /**
     * 마지막으로 생성된 요약을 다시 읽는다(계약 §2 — LLM을 부르지 않고 캐시만 준다).
     * 없으면(404 NO_SUMMARY) 비어 있다. COOLDOWN·IN_FLIGHT 뒤 회수 경로다.
     */
    default Optional<ChatAiSummary> getSummary(long sessionId) {
        return Optional.empty();
    }

    /** 방송 종료 시 chat-ai의 윈도우·캐시를 정리한다. 놓쳐도 10분 뒤 자동 정리된다. */
    default void endBroadcast(long sessionId) {
    }
}
