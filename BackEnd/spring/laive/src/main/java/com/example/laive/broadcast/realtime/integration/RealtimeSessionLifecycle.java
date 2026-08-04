package com.example.laive.broadcast.realtime.integration;

import com.example.laive.broadcast.questions.service.RepeatedQuestionPollService;
import com.example.laive.broadcast.realtime.repository.mybatis.TriggerMapper;
import com.example.laive.broadcast.realtime.service.RealtimeSessionRegistry;
import com.example.laive.broadcast.realtime.service.TriggerRecorder;
import com.example.laive.broadcast.session.integration.AiSessionGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 방송 세션 생명주기를 실시간 판정기와 chat-ai에 연결한다.
 *
 * <p>{@link AiSessionGateway}의 실구현이라 이 Bean이 있으면
 * {@code BroadcastSessionConfig}의 no-op 기본 Port가 자동으로 빠진다.
 * 이후 STT 파이프라인이 자기 구현을 가져오면 이 클래스와 함께 위임하는
 * 컴포지트로 묶어야 한다 — Port는 하나의 Bean만 허용된다.
 */
@Component
public class RealtimeSessionLifecycle implements AiSessionGateway {

    private static final Logger log = LoggerFactory.getLogger(RealtimeSessionLifecycle.class);

    private final RealtimeSessionRegistry registry;
    private final TriggerRecorder triggerRecorder;
    private final TriggerMapper triggerMapper;
    private final ChatAiGateway chatAiGateway;
    private final RepeatedQuestionPollService repeatedQuestionPollService;
    private final Clock clock;

    public RealtimeSessionLifecycle(
            RealtimeSessionRegistry registry,
            TriggerRecorder triggerRecorder,
            TriggerMapper triggerMapper,
            ChatAiGateway chatAiGateway,
            RepeatedQuestionPollService repeatedQuestionPollService,
            Clock clock
    ) {
        this.registry = registry;
        this.triggerRecorder = triggerRecorder;
        this.triggerMapper = triggerMapper;
        this.chatAiGateway = chatAiGateway;
        this.repeatedQuestionPollService = repeatedQuestionPollService;
        this.clock = clock;
    }

    /** 준비 이벤트에서 판정 대상으로 등록한다. 워밍업 기준은 DB의 세션 시작 시각이다. */
    @Override
    public void start(long sessionId, String language) {
        Instant startedAt = triggerMapper.findActiveSessionStartedAt(sessionId)
                .orElseGet(clock::instant);
        registry.register(sessionId, startedAt);
        log.info("실시간 판정 등록: sessionId={} startedAt={}", sessionId, startedAt);
    }

    /**
     * 서버 재시작 후 도착한 배치가 판정을 되살린다(HIGHLIGHT_SPEC 가드 7).
     * 이미 끝난 세션의 지연 배치는 등록되지 않는다 — 조회가 PREPARING/LIVE만 본다.
     */
    public void ensureRegistered(long sessionId) {
        if (registry.isRegistered(sessionId)) {
            return;
        }
        triggerMapper.findActiveSessionStartedAt(sessionId)
                .ifPresent(startedAt -> {
                    registry.register(sessionId, startedAt);
                    log.info("실시간 판정 lazy 등록(재시작 복구): sessionId={} startedAt={}",
                            sessionId, startedAt);
                });
    }

    /** 종료 시 ACTIVE 구간을 강제 확정하고(가드 5) chat-ai 캐시를 정리한다. */
    @Override
    public void stopAndFlush(long sessionId, Instant endedAt, Duration gracePeriod) {
        registry.finalizeAndRemove(sessionId, endedAt)
                .ifPresent(finalized -> triggerRecorder.record(
                        sessionId, finalized.sessionStartedAt(), finalized.candidate()));
        // 계약 §4: 캐시 정리(DELETE) 전에 반복 질문을 마지막으로 한 번 폴링해
        // askers를 최종 수치로 마감한다 — 세션 리포트가 이 저장값을 쓴다.
        // 실패가 종료 흐름을 막으면 안 된다: HTTP 실패는 게이트웨이가 빈 값으로
        // 흡수하고, DB 반영 실패는 여기서 잡는다.
        try {
            repeatedQuestionPollService.pollOnce(sessionId);
        } catch (RuntimeException exception) {
            log.warn("반복 질문 종료 마감 실패(리포트가 마지막 폴링 값으로 남음): sessionId={}",
                    sessionId, exception);
        }
        chatAiGateway.endBroadcast(sessionId);
    }
}
