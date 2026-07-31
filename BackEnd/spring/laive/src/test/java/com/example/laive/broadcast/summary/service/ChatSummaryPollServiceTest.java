package com.example.laive.broadcast.summary.service;

import com.example.laive.broadcast.realtime.domain.ChatAiSummary;
import com.example.laive.broadcast.realtime.domain.ChatAiSummaryCall;
import com.example.laive.broadcast.realtime.domain.SummaryMode;
import com.example.laive.broadcast.realtime.integration.ChatAiGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AUTO 폴링 한 주기의 분기 — 성공 저장, COOLDOWN 회수(백스톱), 정상 거절 스킵 —
 * 를 검증한다.
 */
class ChatSummaryPollServiceTest {

    private static final long SESSION_ID = 9002L;
    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

    private ChatAiGateway gateway;
    private ChatSummaryBriefingService briefingService;
    private ChatSummaryPollService pollService;

    @BeforeEach
    void setUp() {
        gateway = mock(ChatAiGateway.class);
        briefingService = mock(ChatSummaryBriefingService.class);
        pollService = new ChatSummaryPollService(gateway, briefingService);
    }

    @Test
    void successIsAppended() {
        ChatAiSummary summary = summary("새 요약");
        when(gateway.requestSummary(SESSION_ID, SummaryMode.AUTO))
                .thenReturn(new ChatAiSummaryCall.Success(summary));

        pollService.pollOnce(SESSION_ID);

        verify(briefingService).append(SESSION_ID, summary);
    }

    @Test
    void cooldownRecoversLastSummaryViaGet() {
        // MANUAL이 생성만 하고 저장 전에 죽은 요약을 다음 주기가 회수하는 백스톱이다.
        ChatAiSummary cached = summary("캐시된 요약");
        when(gateway.requestSummary(SESSION_ID, SummaryMode.AUTO))
                .thenReturn(new ChatAiSummaryCall.Cooldown(42, NOW));
        when(gateway.getSummary(SESSION_ID)).thenReturn(Optional.of(cached));

        pollService.pollOnce(SESSION_ID);

        verify(briefingService).append(SESSION_ID, cached);
    }

    @Test
    void cooldownWithoutCachedSummaryDoesNothing() {
        when(gateway.requestSummary(SESSION_ID, SummaryMode.AUTO))
                .thenReturn(new ChatAiSummaryCall.Cooldown(42, NOW));
        when(gateway.getSummary(SESSION_ID)).thenReturn(Optional.empty());

        pollService.pollOnce(SESSION_ID);

        verifyNoInteractions(briefingService);
    }

    @Test
    void inFlightSkipsWithoutPersisting() {
        // 생성 중인 쪽(MANUAL)이 저장한다. 놓쳐도 다음 주기의 COOLDOWN 회수가 잡는다.
        when(gateway.requestSummary(SESSION_ID, SummaryMode.AUTO))
                .thenReturn(new ChatAiSummaryCall.InFlight());

        pollService.pollOnce(SESSION_ID);

        verifyNoInteractions(briefingService);
    }

    @Test
    void normalRejectionsAndFailureSkipWithoutPersisting() {
        when(gateway.requestSummary(SESSION_ID, SummaryMode.AUTO))
                .thenReturn(new ChatAiSummaryCall.NotEnoughChats(12, 30))
                .thenReturn(new ChatAiSummaryCall.UnknownBroadcast())
                .thenReturn(new ChatAiSummaryCall.Failure());

        pollService.pollOnce(SESSION_ID);
        pollService.pollOnce(SESSION_ID);
        pollService.pollOnce(SESSION_ID);

        verifyNoInteractions(briefingService);
    }

    private ChatAiSummary summary(String text) {
        return new ChatAiSummary(
                text, "EXCITEMENT", Map.of("EXCITEMENT", 0.34), 4210, 180,
                NOW, NOW.plusSeconds(60));
    }
}
