package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.fandom.repository.mybatis.ViewerScoreMapper;
import com.example.laive.broadcast.session.config.BroadcastSessionProperties;
import com.example.laive.broadcast.session.event.BroadcastEndedEvent;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import com.example.laive.broadcast.session.repository.mybatis.BroadcastPlatformSessionMapper;
import org.springframework.stereotype.Service;
import com.example.laive.report.service.ReportGenerationService;

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
    private final ReportGenerationService reportGenerationService;
    private final BroadcastPlatformSessionMapper platformSessionMapper;
    private final ViewerScoreMapper viewerScoreMapper;

    public BroadcastSessionFinalizationService(
            ChatCollectorGateway chatCollectorGateway,
            AiSessionGateway aiSessionGateway,
            SessionRedisGateway redisGateway,
            BroadcastSessionTransactionService transactionService,
            BroadcastSessionProperties properties,
            ReportGenerationService reportGenerationService,
            BroadcastPlatformSessionMapper platformSessionMapper,
            ViewerScoreMapper viewerScoreMapper
    ) {
        this.chatCollectorGateway = chatCollectorGateway;
        this.aiSessionGateway = aiSessionGateway;
        this.redisGateway = redisGateway;
        this.transactionService = transactionService;
        this.properties = properties;
        this.reportGenerationService = reportGenerationService;
        this.platformSessionMapper = platformSessionMapper;
        this.viewerScoreMapper = viewerScoreMapper;
    }

    public void finalizeSession(BroadcastEndedEvent event) {
        var platformSessions = platformSessionMapper.findByBroadcastSessionId(event.sessionId());
        if (platformSessions.isEmpty()) {
            chatCollectorGateway.stopAndFlush(
                    event.sessionId(),
                    event.endedAt(),
                    properties.finalizationGracePeriod()
            );
        } else {
            for (var platformSession : platformSessions) {
                chatCollectorGateway.stopPlatformSession(
                        platformSession.platformSessionId(),
                        event.endedAt(),
                        properties.finalizationGracePeriod()
                );
                platformSessionMapper.markCapabilitiesCompleted(
                        platformSession.platformSessionId(), event.endedAt());
            }
        }
        aiSessionGateway.stopAndFlush(
                event.sessionId(),
                event.endedAt(),
                properties.finalizationGracePeriod()
        );
        transactionService.finalizeStatistics(event.reportId(), event.sessionId());
        // 열성 시청자 점수 확정(FR-F-03). 수집기 flush 뒤라 채팅이 전부 들어와 있고,
        // chat_messages는 90일 뒤 지워지므로 이 행이 유일한 영구 기록이다.
        // ON CONFLICT 단문이라 아웃박스 재시도에도 멱등하다.
        viewerScoreMapper.persistViewerScores(event.sessionId());
        reportGenerationService.start(event.sessionId(), event.reportId());
        redisGateway.finish(event.sessionId(), event.endedAt());
    }

    public void markFailed(long reportId) {
        transactionService.markReportFailed(reportId);
    }
}

