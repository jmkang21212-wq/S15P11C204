package com.example.laive.broadcast.summary.service;

import com.example.laive.broadcast.realtime.domain.ChatAiSummaryCall;
import com.example.laive.broadcast.realtime.domain.SummaryMode;
import com.example.laive.broadcast.realtime.integration.ChatAiGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AUTO 여론 요약 한 세션 폴링의 진입점이다. HTTP 호출(트랜잭션 밖)과 DB 반영
 * (트랜잭션)을 여기서 가른다 — LLM 응답 대기(최대 15초)가 DB 커넥션을 잡고
 * 있으면 안 된다. {@code RepeatedQuestionPollService}와 같은 분리다.
 */
@Service
public class ChatSummaryPollService {

    private static final Logger log = LoggerFactory.getLogger(ChatSummaryPollService.class);

    private final ChatAiGateway chatAiGateway;
    private final ChatSummaryBriefingService briefingService;

    public ChatSummaryPollService(ChatAiGateway chatAiGateway, ChatSummaryBriefingService briefingService) {
        this.chatAiGateway = chatAiGateway;
        this.briefingService = briefingService;
    }

    /**
     * 한 세션의 요약을 1회 생성·저장한다.
     *
     * <p>COOLDOWN이면 GET으로 마지막 요약을 회수해 dedupe-append한다 — MANUAL이
     * 생성만 하고 저장 전에 죽었거나(타임아웃·재시작) 다른 트리거가 이미 저장한
     * 경우를 다음 주기가 복구하는 백스톱이다. IN_FLIGHT는 생성 중인 쪽이 저장할
     * 것이므로 건너뛴다 — 놓쳐도 다음 주기의 COOLDOWN 회수가 잡는다.
     */
    public void pollOnce(long sessionId) {
        ChatAiSummaryCall call = chatAiGateway.requestSummary(sessionId, SummaryMode.AUTO);
        switch (call) {
            case ChatAiSummaryCall.Success success ->
                    briefingService.append(sessionId, success.summary());
            case ChatAiSummaryCall.Cooldown cooldown ->
                    chatAiGateway.getSummary(sessionId)
                            .ifPresent(summary -> briefingService.append(sessionId, summary));
            case ChatAiSummaryCall.InFlight inFlight ->
                    log.debug("여론 요약 생성 진행 중이라 이번 주기를 건너뜁니다: sessionId={}", sessionId);
            case ChatAiSummaryCall.NotEnoughChats notEnough ->
                    log.debug("여론 요약 대상 채팅 부족(정상): sessionId={} chatCount={}",
                            sessionId, notEnough.chatCount());
            case ChatAiSummaryCall.UnknownBroadcast unknown ->
                    log.debug("chat-ai에 ingest 이력이 없어 요약을 건너뜁니다: sessionId={}", sessionId);
            case ChatAiSummaryCall.Failure failure -> {
                // 게이트웨이가 이미 warn을 남겼다. 계약: 실패하면 다음 주기에 다시 시도.
            }
        }
    }
}
