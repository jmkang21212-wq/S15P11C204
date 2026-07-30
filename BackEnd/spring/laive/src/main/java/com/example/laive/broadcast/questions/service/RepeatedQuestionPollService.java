package com.example.laive.broadcast.questions.service;

import com.example.laive.broadcast.questions.config.RepeatedQuestionProperties;
import com.example.laive.broadcast.realtime.integration.ChatAiGateway;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 반복 질문 한 세션 폴링의 진입점이다. HTTP 호출(트랜잭션 밖)과 DB 반영(트랜잭션)을
 * 여기서 가른다 — chat-ai 응답 대기가 DB 커넥션을 잡고 있으면 안 된다.
 * {@code CollectionRelayService}가 ingest에서 같은 이유로 같은 분리를 했다.
 */
@Service
public class RepeatedQuestionPollService {

    private final ChatAiGateway chatAiGateway;
    private final RepeatedQuestionSyncService syncService;
    private final RepeatedQuestionProperties properties;
    private final Clock clock;

    public RepeatedQuestionPollService(
            ChatAiGateway chatAiGateway,
            RepeatedQuestionSyncService syncService,
            RepeatedQuestionProperties properties,
            Clock clock
    ) {
        this.chatAiGateway = chatAiGateway;
        this.syncService = syncService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 한 세션을 1회 폴링해 반영한다. AI 실패·404는 게이트웨이가 빈 값으로 흡수하므로
     * 이번 주기를 통째로 건너뛴다 — 판정 근거인 응답이 없으면 baseline 확정도
     * 하지 않는 것이 맞다. 유예는 dismissed_at 기준이라 다음 성공 폴링에서 이어진다.
     */
    public void pollOnce(long sessionId) {
        chatAiGateway.getQuestions(sessionId, properties.limit())
                .ifPresent(result -> syncService.apply(sessionId, result, clock.instant()));
    }
}
