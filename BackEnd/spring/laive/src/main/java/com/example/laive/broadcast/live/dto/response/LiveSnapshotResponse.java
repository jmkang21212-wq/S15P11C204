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
        Highlight highlight,
        List<RepeatedQuestion> repeatedQuestions,
        List<TopViewer> topViewers,
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
     * CHAT_SURGE 하이라이트 판정기의 현재 상태다(관측 전용 — HIGHLIGHT_SPEC §5가
     * 강권하는 "판정 품질을 눈으로 보는" 창구). 파라미터가 백테스트 미검증이라
     * z가 화면에 보여야 시연·튜닝 때 "왜 안 잡히나"를 즉석에서 알 수 있다.
     *
     * @param phase           IDLE | ACTIVE | COOLDOWN. 판정기 미등록(방송 전·재시작 직후)이면 null
     * @param z               마지막 정상 판정 틱의 z-score. 가드에 걸린 틱이면 null
     * @param zEnter          진입 임계 — z가 이 값 이상으로 연속되면 하이라이트 진입
     * @param engagement      판정 윈도우 유니크 채터 ÷ 시청자 (여론의 180초 engagement와 다른 값)
     * @param windowChats     판정 윈도우 안 채팅 수
     * @param uniqueChatters  판정 윈도우 안 유니크 채터 수
     * @param skipReason      마지막 틱이 가드에 걸린 이유
     *                        (gap | no-viewers | zero-viewers | warmup | empty-baseline | quiet).
     *                        no-viewers는 미수신(수집기 장애), zero-viewers는 0명(송출 중
     *                        아님·시청자 없음). 정상 판정이면 null
     * @param warmupUntil     워밍업이 끝나 판정이 시작되는 시각
     * @param engageWindowSec 판정 윈도우 길이(초). 환경변수로 튜닝되는 값이라 화면이
     *                        "최근 N초"를 표기할 때 이 값을 써야 한다
     * @param detectedCount   이 세션에서 지금까지 확정된 하이라이트 구간 수
     * @param lastDetectedAt  마지막으로 확정된 구간의 끝 시각. 없으면 null
     */
    public record Highlight(
            String phase,
            Double z,
            Double zEnter,
            Double engagement,
            Integer windowChats,
            Integer uniqueChatters,
            String skipReason,
            Instant warmupUntil,
            Integer engageWindowSec,
            int detectedCount,
            Instant lastDetectedAt
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

    /**
     * 열성 시청자 한 명이다 (FR-F-03, 총점 내림차순 최대 5명).
     *
     * <p>점수는 도배 방지 산식이다 — 분당 3개 캡 채팅 점수 + 참여 분 수×2 +
     * 후원 로그 가중 (ViewerScoreMapper.xml 참고). 스트리머 본인·매니저는 제외돼 있다.
     *
     * @param nickname    표시용 닉네임. 아직 관측 전이면 null — 그때는 viewerAlias를 쓴다
     * @param viewerAlias 해시에서 파생한 익명 별칭(viewer-a91c). 항상 채워진다
     */
    public record TopViewer(
            long viewerId,
            String nickname,
            String viewerAlias,
            int chatScore,
            int watchScore,
            double totalScore
    ) {
    }
}
