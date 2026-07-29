package com.example.laive.broadcast.collect;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.collect.domain.ChatMessageItem;
import com.example.laive.broadcast.collect.domain.CollectionGap;
import com.example.laive.broadcast.collect.domain.CollectionGapReason;
import com.example.laive.broadcast.collect.domain.ViewerMetricItem;
import com.example.laive.broadcast.collect.service.CollectionIngestService;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL 스키마에서 수집 데이터의 영속화와 중복 처리(명세 13.3)를 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class CollectionIngestIntegrationTest {

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

    // --- 채팅 ---

    @Test
    void createsViewerProfileFromHashAndStoresChat() {
        long sessionId = createSession();
        Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String hash = "sha256:" + UUID.randomUUID();

        BatchIngestResult result = ingestService.appendChatMessages(sessionId, List.of(
                new ChatMessageItem(hash, "이거 클립 박제각", sentAt)
        ));

        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(chatCount(sessionId)).isEqualTo(1);
        assertThat(viewerHashesOf(sessionId)).containsExactly(hash);
    }

    @Test
    void reusesSingleViewerProfileForRepeatedHashInSameBatch() {
        // 같은 명령에서 같은 충돌 대상을 두 번 갱신하면 PostgreSQL이 거부한다.
        // 서비스가 배치 안의 해시를 접어서 보내는지 확인한다.
        long sessionId = createSession();
        Instant sentAt = Instant.now();
        String hash = "sha256:" + UUID.randomUUID();

        BatchIngestResult result = ingestService.appendChatMessages(sessionId, List.of(
                new ChatMessageItem(hash, "ㅋㅋㅋ", sentAt),
                new ChatMessageItem(hash, "ㅋㅋㅋㅋㅋ", sentAt.plusMillis(120)),
                new ChatMessageItem(hash, "ㅋㅋㅋㅋㅋㅋㅋ", sentAt.plusMillis(300))
        ));

        assertThat(result.acceptedCount()).isEqualTo(3);
        assertThat(chatCount(sessionId)).isEqualTo(3);
        assertThat(viewerProfileCount(sessionId)).isEqualTo(1);
    }

    @Test
    void reusesExistingViewerProfileAcrossBatches() {
        long sessionId = createSession();
        String hash = "sha256:" + UUID.randomUUID();

        ingestService.appendChatMessages(sessionId,
                List.of(new ChatMessageItem(hash, "첫 배치", Instant.now())));
        ingestService.appendChatMessages(sessionId,
                List.of(new ChatMessageItem(hash, "둘째 배치", Instant.now())));

        assertThat(chatCount(sessionId)).isEqualTo(2);
        assertThat(viewerProfileCount(sessionId)).isEqualTo(1);
    }

    @Test
    void advancesLastSeenToLatestMessageOfBatch() {
        long sessionId = createSession();
        String hash = "sha256:" + UUID.randomUUID();
        Instant early = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant late = Instant.now();

        // 배치 안의 순서가 시간순이 아니어도 가장 늦은 시각이 last_seen_at이 되어야 한다.
        ingestService.appendChatMessages(sessionId, List.of(
                new ChatMessageItem(hash, "나중 것", late),
                new ChatMessageItem(hash, "먼저 것", early)
        ));

        Instant lastSeen = jdbcTemplate.queryForObject(
                "SELECT last_seen_at FROM viewer_profiles WHERE viewer_hash = ?",
                Instant.class, hash);
        Instant firstSeen = jdbcTemplate.queryForObject(
                "SELECT first_seen_at FROM viewer_profiles WHERE viewer_hash = ?",
                Instant.class, hash);

        assertThat(lastSeen).isCloseTo(late, within10Millis());
        assertThat(firstSeen).isCloseTo(early, within10Millis());
    }

    @Test
    void storesBlankMessageWithoutRejectingBatch() {
        // 이모티콘만 있는 채팅은 content가 빈 문자열로 온다. 버리면 안 된다.
        long sessionId = createSession();

        BatchIngestResult result = ingestService.appendChatMessages(sessionId, List.of(
                new ChatMessageItem("sha256:" + UUID.randomUUID(), "", Instant.now())
        ));

        assertThat(result.acceptedCount()).isEqualTo(1);
    }

    @Test
    void rejectsChatForUnknownSession() {
        assertThatThrownBy(() -> ingestService.appendChatMessages(-1L, List.of(
                new ChatMessageItem("sha256:x", "메시지", Instant.now())
        ))).isInstanceOf(BroadcastSessionException.class);
    }

    // --- 시청자 지표 ---

    @Test
    void storesViewerMetrics() {
        long sessionId = createSession();
        Instant measuredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        BatchIngestResult result = ingestService.appendViewerMetrics(sessionId, List.of(
                new ViewerMetricItem(measuredAt, 2417)
        ));

        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isZero();
        assertThat(viewerMetricCount(sessionId)).isEqualTo(1);
    }

    @Test
    void absorbsRetriedViewerMetricAsDuplicate() {
        // (session_id, measured_at) Unique가 재시도 중복을 흡수한다.
        long sessionId = createSession();
        Instant measuredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var items = List.of(new ViewerMetricItem(measuredAt, 2417));

        ingestService.appendViewerMetrics(sessionId, items);
        BatchIngestResult retry = ingestService.appendViewerMetrics(sessionId, items);

        assertThat(retry.acceptedCount()).isZero();
        assertThat(retry.duplicateCount()).isEqualTo(1);
        assertThat(viewerMetricCount(sessionId)).isEqualTo(1);
    }

    @Test
    void collapsesDuplicateMeasuredAtWithinOneBatch() {
        long sessionId = createSession();
        Instant measuredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        BatchIngestResult result = ingestService.appendViewerMetrics(sessionId, List.of(
                new ViewerMetricItem(measuredAt, 2417),
                new ViewerMetricItem(measuredAt, 2418)
        ));

        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(viewerMetricCount(sessionId)).isEqualTo(1);
    }

    // --- 수집 공백 ---

    @Test
    void opensGapWithoutEndThenClosesItOnSameStart() {
        long sessionId = createSession();
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant endedAt = startedAt.plusSeconds(42);

        ingestService.recordCollectionGap(sessionId,
                new CollectionGap(startedAt, null, CollectionGapReason.DISCONNECTED));
        assertThat(openGapCount(sessionId)).isEqualTo(1);

        ingestService.recordCollectionGap(sessionId,
                new CollectionGap(startedAt, endedAt, CollectionGapReason.DISCONNECTED));

        // 새 행이 생기지 않고 같은 행이 닫혀야 한다.
        assertThat(gapCount(sessionId)).isEqualTo(1);
        assertThat(openGapCount(sessionId)).isZero();
    }

    @Test
    void keepsClosedGapWhenLateOpenReportArrives() {
        // 닫힌 구간에 열린 보고가 늦게 도착해도 ended_at을 지우면 안 된다.
        long sessionId = createSession();
        Instant startedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        ingestService.recordCollectionGap(sessionId, new CollectionGap(
                startedAt, startedAt.plusSeconds(10), CollectionGapReason.DISCONNECTED));
        ingestService.recordCollectionGap(sessionId,
                new CollectionGap(startedAt, null, CollectionGapReason.DISCONNECTED));

        assertThat(openGapCount(sessionId)).isZero();
    }

    @Test
    void closesGapsStillOpenAtSessionEnd() {
        // 수집기가 공백 도중에 죽으면 아무도 닫지 않는다. 세션 종료가 마지막 방어선이다.
        long sessionId = createSession();
        Instant startedAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
        ingestService.recordCollectionGap(sessionId,
                new CollectionGap(startedAt, null, CollectionGapReason.COLLECTOR_STOPPED));

        int closed = ingestService.closeOpenCollectionGaps(sessionId, Instant.now());

        assertThat(closed).isEqualTo(1);
        assertThat(openGapCount(sessionId)).isZero();
    }

    // --- Fixture ---

    private static org.assertj.core.data.TemporalUnitOffset within10Millis() {
        return org.assertj.core.api.Assertions.within(10, ChronoUnit.MILLIS);
    }

    private long createSession() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        return transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId, null, "수집 테스트 방송", null, "ko")
        ).sessionId();
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                null, "수집 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "collect-test-" + UUID.randomUUID(),
                "테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }

    private int chatCount(long sessionId) {
        return count("SELECT count(*) FROM chat_messages WHERE session_id = ?", sessionId);
    }

    private int viewerMetricCount(long sessionId) {
        return count("SELECT count(*) FROM viewer_metrics WHERE session_id = ?", sessionId);
    }

    private int gapCount(long sessionId) {
        return count("SELECT count(*) FROM collection_gaps WHERE session_id = ?", sessionId);
    }

    private int openGapCount(long sessionId) {
        return count(
                "SELECT count(*) FROM collection_gaps WHERE session_id = ? AND ended_at IS NULL",
                sessionId);
    }

    private int viewerProfileCount(long sessionId) {
        return count("""
                SELECT count(DISTINCT c.viewer_id)
                  FROM chat_messages c
                 WHERE c.session_id = ?
                """, sessionId);
    }

    private List<String> viewerHashesOf(long sessionId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT v.viewer_hash
                  FROM chat_messages c
                  JOIN viewer_profiles v ON v.id = c.viewer_id
                 WHERE c.session_id = ?
                """, String.class, sessionId);
    }

    private int count(String sql, long sessionId) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, sessionId);
        return value == null ? 0 : value;
    }
}
