package com.example.laive.broadcast.summary.service;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.live.repository.mybatis.LiveBriefingRow;
import com.example.laive.broadcast.live.repository.mybatis.LiveSnapshotMapper;
import com.example.laive.broadcast.realtime.domain.ChatAiSummary;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionResult;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.BeforeEach;
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
 * 여론 요약 브리핑 저장의 dedupe(직전 텍스트 비교)와 live-snapshot 브리핑 피드
 * 조회를 실제 스키마에서 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class ChatSummaryBriefingServiceIntegrationTest {

    private static final Instant GENERATED_AT = Instant.parse("2026-07-30T12:00:00Z");

    @Autowired
    private ChatSummaryBriefingService briefingService;

    @Autowired
    private LiveSnapshotMapper liveSnapshotMapper;

    @Autowired
    private BroadcastSessionTransactionService transactionService;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long sessionId;

    @BeforeEach
    void setUp() {
        sessionId = createSession().sessionId();
    }

    @Test
    void appendInsertsChatSummaryBriefing() {
        boolean saved = briefingService.append(sessionId, summary("환호가 집중되고 있습니다."));

        assertThat(saved).isTrue();
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT briefing_type, message FROM live_briefings WHERE session_id = ?", sessionId);
        assertThat(row.get("briefing_type")).isEqualTo("CHAT_SUMMARY");
        assertThat(row.get("message")).isEqualTo("환호가 집중되고 있습니다.");
    }

    @Test
    void appendSkipsSameTextAsLatest() {
        // AUTO·MANUAL 충돌이나 COOLDOWN 회수가 같은 요약을 두 번 물어오는 경우다.
        assertThat(briefingService.append(sessionId, summary("같은 요약"))).isTrue();
        assertThat(briefingService.append(sessionId, summary("같은 요약"))).isFalse();

        assertThat(countBriefings()).isEqualTo(1);
    }

    @Test
    void appendInsertsWhenTextChanged() {
        briefingService.append(sessionId, summary("첫 번째 요약"));
        boolean saved = briefingService.append(sessionId, summary("두 번째 요약"));

        assertThat(saved).isTrue();
        assertThat(countBriefings()).isEqualTo(2);
    }

    @Test
    void appendComparesOnlyLatestSoRecurringOpinionIsKept() {
        // 여론이 A→B→A로 돌아온 경우: 직전(B)과만 비교하므로 A는 다시 저장된다.
        briefingService.append(sessionId, summary("여론 A"));
        briefingService.append(sessionId, summary("여론 B"));
        assertThat(briefingService.append(sessionId, summary("여론 A"))).isTrue();

        assertThat(countBriefings()).isEqualTo(3);
    }

    @Test
    void appendRejectsBlankSummary() {
        assertThat(briefingService.append(sessionId, summary(" "))).isFalse();
        assertThat(countBriefings()).isZero();
    }

    @Test
    void findRecentBriefingsReturnsNewestFirstAndHonorsLimit() {
        briefingService.append(sessionId, summary("요약 1"));
        briefingService.append(sessionId, summary("요약 2"));
        briefingService.append(sessionId, summary("요약 3"));

        List<LiveBriefingRow> rows = liveSnapshotMapper.findRecentBriefings(sessionId, 2);

        // created_at이 같은 초에 몰려도 id 보조 정렬로 최신순이 유지된다.
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).message()).isEqualTo("요약 3");
        assertThat(rows.get(1).message()).isEqualTo("요약 2");
        assertThat(rows.get(0).briefingType()).isEqualTo("CHAT_SUMMARY");
        assertThat(rows.get(0).createdAt()).isNotNull();
    }

    @Test
    void findRecentBriefingsIncludesOtherBriefingTypes() {
        // AI 분석이 저장하는 RISK·PROMISE도 같은 피드에 실려야 한다.
        jdbcTemplate.update(
                "INSERT INTO live_briefings (session_id, briefing_type, message) VALUES (?, 'RISK', ?)",
                sessionId, "위험 발언 경고");
        briefingService.append(sessionId, summary("여론 요약"));

        List<LiveBriefingRow> rows = liveSnapshotMapper.findRecentBriefings(sessionId, 20);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).briefingType()).isEqualTo("CHAT_SUMMARY");
        assertThat(rows.get(1).briefingType()).isEqualTo("RISK");
    }

    // --- Fixture ---

    private ChatAiSummary summary(String text) {
        return new ChatAiSummary(
                text, "EXCITEMENT", Map.of("EXCITEMENT", 0.34), 4210, 180,
                GENERATED_AT, GENERATED_AT.plusSeconds(60));
    }

    private int countBriefings() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM live_briefings WHERE session_id = ?", Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    private StartBroadcastSessionResult createSession() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        return transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId, null, "여론 요약 테스트 방송", null, "ko")
        );
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                null, "여론 요약 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "chat-summary-test-" + UUID.randomUUID(),
                "테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }
}
