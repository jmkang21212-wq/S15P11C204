package com.example.laive.broadcast.live.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 실시간 콘솔 스냅샷 응답이다(명세 10.6).
 *
 * <p>프론트가 2~3초 주기로 폴링해 콘솔 화면 전체를 이 응답으로 그린다
 * (2026-07-29 결정 — WebSocket 대신 폴링).
 *
 * <p>briefings와 repeatedQuestions는 STT·반복 질문 파이프라인이 아직 없어
 * 항상 빈 배열이다. 필드를 미리 두는 이유: 프론트가 스키마를 한 번만 맞추면 된다.
 */
public record LiveSnapshotResponse(
        Session session,
        Services services,
        List<Briefing> briefings,
        Sentiment sentiment,
        List<RepeatedQuestion> repeatedQuestions,
        String lastEventId
) {

    /** @param elapsedSeconds 진행 중이면 현재까지, 끝났으면 종료까지의 경과 초 */
    public record Session(
            long sessionId,
            String status,
            Instant startedAt,
            long elapsedSeconds,
            int currentViewers
    ) {
    }

    /**
     * @param microphone 마이크 연결 상태. 음성 파이프라인 미구현으로 항상 DISCONNECTED
     * @param stt        STT 상태. 음성 파이프라인 미구현으로 항상 STOPPED
     * @param ai         채팅 감정 분류 파이프라인 상태. 최근 여론 스냅샷 갱신 여부로 판단
     * @param collector  치지직 채팅 수집기 상태. 끊기면 그 구간 채팅이 유실되므로 화면에 노출
     */
    public record Services(String microphone, String stt, String ai, String collector) {
    }

    public record Briefing(long briefingId, String briefingType, String message, Instant createdAt) {
    }

    /**
     * 13.6 채팅 분류 응답의 최근 윈도우 지표를 그대로 중계한 값이다.
     *
     * @param engagement   유니크 채터 ÷ 시청자. 시청자를 알 수 없으면 null
     * @param dominant     최다 감정 코드. analyzedChat이 0이면 null
     * @param distribution 8종 전부 내려보내고 상위 3종 선택은 프론트가 한다. analyzedChat이 0이면 null
     */
    public record Sentiment(
            int windowSec,
            long analyzedChat,
            Double engagement,
            String dominant,
            Map<String, Double> distribution
    ) {
    }

    public record RepeatedQuestion(long questionId, String question, int occurrenceCount) {
    }
}
