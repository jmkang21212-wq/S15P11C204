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
 * <p>briefings는 live_briefings의 최근 20건(최신순)이다 — AI 분석이 저장하는
 * RISK·PROMISE와 여론 요약 CHAT_SUMMARY가 실린다.
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
     * @param microphone 마이크 연결 상태. 음성 경로는 프론트 ↔ AI 직결(WS)이라 Spring이
     *                   관측하지 못해 항상 DISCONNECTED — 실제 표시는 프론트 몫
     * @param stt        STT 상태. 같은 이유로 항상 STOPPED
     * @param ai         채팅 감정 분류 파이프라인 상태. 최근 여론 스냅샷 갱신 여부로 판단
     * @param collector  치지직 채팅 수집기 상태. 끊기면 그 구간 채팅이 유실되므로 화면에 노출
     */
    public record Services(String microphone, String stt, String ai, String collector) {
    }

    /**
     * @param promiseId PROMISE 브리핑이 가리키는 promises.id — 새로고침·재입장으로
     *                  복구된 히스토리 카드가 약속 확정/무시(10.8)를 호출할 때 쓴다.
     *                  실시간 카드 몫은 AI WS가 싣는다. 그 외 유형은 null
     */
    public record Briefing(
            long briefingId,
            String briefingType,
            String message,
            Long promiseId,
            Instant createdAt
    ) {
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

    /**
     * 반복 질문 한 건이다(계약: AI/CHAT_AI_API.md §3). dismissed(답변 완료)는 이미
     * 걸러져 있고 askers 내림차순 최대 50건이다. "전체 / FAQ만" 필터와 상위 N 컷은
     * 프론트가 한다 — FAQ는 소수라 5개를 채우려면 넉넉히 받아 걸러야 한다.
     *
     * @param questionId AI 발급 식별자. 16자리 long — JS Number 정밀도(2^53)를 넘지는
     *                   않지만 해석하지 말고 불투명 id로 다룰 것
     * @param text       질문 묶음의 대표 표현
     * @param type       FAQ | CONTEXT | null(판정 전)
     * @param askers     몇 명이 물었나(유니크). 화면에 표시할 숫자이자 정렬 기준
     * @param messages   총 메시지 수. askers와 크게 벌어지면 한 명의 도배
     * @param reasked    답변 완료 후 다시 늘어 재노출됐다 — 답이 시청자에게 닿지 않은 신호
     */
    public record RepeatedQuestion(
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
}
