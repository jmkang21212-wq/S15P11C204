package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.event.BroadcastEndedEvent;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
import com.example.laive.broadcast.session.integration.ReportGenerationGateway;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import org.springframework.stereotype.Service;

/**
 * 종료 후 외부 수집기를 정리하고 로컬 통계와 FastAPI 리포트 생성을 연결한다.
 */
@Service
public class BroadcastSessionFinalizationService {

    private final ChatCollectorGateway chatCollectorGateway;
    private final AiSessionGateway aiSessionGateway;
    private final SessionRedisGateway redisGateway;
    private final ReportGenerationGateway reportGenerationGateway;
    private final BroadcastSessionTransactionService transactionService;
    private final BroadcastSessionProperties properties;

    public BroadcastSessionFinalizationService(
            ChatCollectorGateway chatCollectorGateway,
            AiSessionGateway aiSessionGateway,
            SessionRedisGateway redisGateway,
            ReportGenerationGateway reportGenerationGateway,
            BroadcastSessionTransactionService transactionService,
            BroadcastSessionProperties properties
    ) {
        this.chatCollectorGateway = chatCollectorGateway;
        this.aiSessionGateway = aiSessionGateway;
        this.redisGateway = redisGateway;
        this.reportGenerationGateway = reportGenerationGateway;
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

        boolean requested = reportGenerationGateway.requestReport(
                event.reportId(),
                event.sessionId(),
                event.eventId()
        );
        if (!requested) {
            transactionService.markReportPartial(event.reportId());
        }
        redisGateway.finish(event.sessionId(), event.endedAt());
    }

    public void markFailed(long reportId) {
        transactionService.markReportFailed(reportId);
    }
}

