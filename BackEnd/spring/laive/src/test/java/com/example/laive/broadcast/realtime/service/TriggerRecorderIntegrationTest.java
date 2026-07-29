package com.example.laive.broadcast.realtime.service;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.collect.domain.ChatMessageItem;
import com.example.laive.broadcast.collect.domain.ViewerMetricItem;
import com.example.laive.broadcast.collect.service.CollectionIngestService;
import com.example.laive.broadcast.realtime.domain.highlight.HighlightCandidate;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionResult;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 확정 구간의 통계 집계 INSERT가 V8의 CHECK 제약(8종 합 == analyzed_chat 등)을
 * 실제 스키마에서 통과하고, 같은 시작점 재삽입이 무시되는지 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class TriggerRecorderIntegrationTest {

    @Autowired
    private TriggerRecorder triggerRecorder;

    @Autowired
    private CollectionIngestService ingestService;

    @Autowired
    private BroadcastSessionTransactionService transactionService;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aggregatesSegmentStatisticsIntoTriggerRow() {
        StartBroadcastSessionResult session = createSession();
        long sessionId = session.sessionId();
        Instant base = session.startedAt();

        // 구간 [base+10s, base+70s] 안: JOY 2건, ANGER 1건, 미분류 1건 (2명의 시청자)
        String viewerA = "sha256:" + UUID.randomUUID();
        String viewerB = "sha256:" + UUID.randomUUID();
        ingestService.appendChatMessages(sessionId, List.of(
                new ChatMessageItem(viewerA, "ㅋㅋㅋ", base.plusSeconds(20), "JOY"),
                new ChatMessageItem(viewerA, "개웃기네", base.plusSeconds(30), "JOY"),
                new ChatMessageItem(viewerB, "이건 좀", base.plusSeconds(40), "ANGER"),
                new ChatMessageItem(viewerB, "", base.plusSeconds(50), null),
                // 구간 밖 채팅은 집계되지 않아야 한다.
                new ChatMessageItem(viewerA, "구간 밖", base.plusSeconds(120), "JOY")
        ));
        ingestService.appendViewerMetrics(sessionId, List.of(
                new ViewerMetricItem(base.plusSeconds(20), 100),
                new ViewerMetricItem(base.plusSeconds(60), 200)
        ));

        triggerRecorder.record(sessionId, base, new HighlightCandidate(
                base.plusSeconds(10), base.plusSeconds(70), 5.2, 0.31));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM triggers WHERE session_id = ?", sessionId);
        assertThat(row.get("trigger_type")).isEqualTo("CHAT_SURGE");
        assertThat(((Number) row.get("start_ms")).longValue()).isEqualTo(10_000L);
        assertThat(((Number) row.get("end_ms")).longValue()).isEqualTo(70_000L);
        assertThat(((Number) row.get("chat_count")).intValue()).isEqualTo(4);
        assertThat(((Number) row.get("unique_chatters")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("analyzed_chat")).intValue()).isEqualTo(3);
        assertThat(((Number) row.get("joy_count")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("anger_count")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("avg_viewers")).intValue()).isEqualTo(150);
        assertThat(((Number) row.get("peak_z")).doubleValue()).isEqualTo(5.2);
        assertThat(row.get("narrative")).isNull();
    }

    @Test
    void ignoresDuplicateSegmentWithSameStart() {
        StartBroadcastSessionResult session = createSession();
        Instant base = session.startedAt();
        HighlightCandidate candidate =
                new HighlightCandidate(base.plusSeconds(10), base.plusSeconds(70), 5.2, 0.31);

        triggerRecorder.record(session.sessionId(), base, candidate);
        triggerRecorder.record(session.sessionId(), base, candidate);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM triggers WHERE session_id = ?",
                Integer.class, session.sessionId());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void clampsLeadBackdatedStartToSessionStart() {
        // 리드 소급이 방송 시작 전으로 나가면 start_ms >= 0 CHECK에 걸리지 않게 자른다.
        StartBroadcastSessionResult session = createSession();
        Instant base = session.startedAt();

        triggerRecorder.record(session.sessionId(), base, new HighlightCandidate(
                base.minusSeconds(20), base.plusSeconds(40), 4.0, 0.2));

        Long startMs = jdbcTemplate.queryForObject(
                "SELECT start_ms FROM triggers WHERE session_id = ?",
                Long.class, session.sessionId());
        assertThat(startMs).isZero();
    }

    @Test
    void emptySegmentStillSatisfiesChecks() {
        // 채팅이 한 건도 없는 구간도 0으로 채워 저장된다(합 0 == analyzed 0).
        StartBroadcastSessionResult session = createSession();
        Instant base = session.startedAt();

        triggerRecorder.record(session.sessionId(), base, new HighlightCandidate(
                base.plusSeconds(10), base.plusSeconds(70), 3.5, 0.1));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT chat_count, analyzed_chat, avg_viewers FROM triggers WHERE session_id = ?",
                session.sessionId());
        assertThat(((Number) row.get("chat_count")).intValue()).isZero();
        assertThat(((Number) row.get("analyzed_chat")).intValue()).isZero();
        assertThat(((Number) row.get("avg_viewers")).intValue()).isZero();
    }

    // --- Fixture ---

    private StartBroadcastSessionResult createSession() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        return transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId, null, "트리거 테스트 방송", null, "ko")
        );
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                null, "트리거 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "trigger-test-" + UUID.randomUUID(),
                "테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }
}
