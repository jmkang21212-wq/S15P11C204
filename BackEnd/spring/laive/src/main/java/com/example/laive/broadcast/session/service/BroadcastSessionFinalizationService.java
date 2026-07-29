package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.event.BroadcastEndedEvent;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import org.springframework.stereotype.Service;

/**
 * 종료 후 외부 수집기를 정리하고 세션 통계를 확정한다.
 */
@Service
public class BroadcastSessionFinalizationService {

    private final ChatCollectorGateway chatCollectorGateway;
    private final AiSessionGateway aiSessionGateway;
    private final SessionRedisGateway redisGateway;
    private final BroadcastSessionTransactionService transactionService;
    private final BroadcastSessionProperties properties;

    public BroadcastSessionFinalizationService(
            ChatCollectorGateway chatCollectorGateway,
            AiSessionGateway aiSessionGateway,
            SessionRedisGateway redisGateway,
            BroadcastSessionTransactionService transactionService,
            BroadcastSessionProperties properties
    ) {
        this.chatCollectorGateway = chatCollectorGateway;
        this.aiSessionGateway = aiSessionGateway;
        this.redisGateway = redisGateway;
        this.transactionService = transactionService;
        this.properties = properties;
    }

    public void finalizeSession(BroadcastEndedEvent event) {
        chatCollectorGateway.stopAndFlush(
                event.sessionId(),
                event.endedAt(),
                properties.finalizationGracePeriod()
        );
        aiSessionGateway.stopAndFlush(
                event.sessionId(),
                event.endedAt(),
                properties.finalizationGracePeriod()
        );
        transactionService.finalizeStatistics(event.reportId(), event.sessionId());

        // 요약과 인사이트는 트리거 앵커를 기준으로 Spring 내부에서 생성한다(API 명세 13.7).
        // 트리거 판정과 LLM 분석 파이프라인이 붙기 전까지는 집계 통계만 확정하고 PARTIAL로
        // 남긴다. 명세 1.5가 정의한 "집계 지표는 있고 요약은 없는" 상태 그대로다.
        // 파이프라인이 생기면 이 자리에서 트리거별 분석 작업을 Outbox에 발행한다.
        transactionService.markReportPartial(event.reportId());
        redisGateway.finish(event.sessionId(), event.endedAt());
    }

    public void markFailed(long reportId) {
        transactionService.markReportFailed(reportId);
    }
}

