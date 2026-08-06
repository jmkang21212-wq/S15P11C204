package com.example.laive.broadcast.collect;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.collect.domain.ChatMessageItem;
import com.example.laive.broadcast.collect.domain.ViewerMetricItem;
import com.example.laive.broadcast.collect.service.CollectionRelayService;
import com.example.laive.broadcast.realtime.domain.ChatAiIngestResult;
import com.example.laive.broadcast.realtime.domain.SentimentWindow;
import com.example.laive.broadcast.realtime.integration.ChatAiGateway;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.integration.SessionRedisGateway;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 채팅 배치가 chat-ai를 거쳐 라벨과 함께 저장되고(명세 13.6), 최신 여론 지표가
 * 콘솔 스냅샷 Hash에 남는지(명세 10.6) 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class CollectionRelayIntegrationTest {

    @Autowired
    private CollectionRelayService relayService;

    @Autowired
    private BroadcastSessionTransactionService transactionService;

    @Autowired
    private SessionRedisGateway sessionRedisGateway;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ChatAiGateway chatAiGateway;

    /** Redis는 테스트 트랜잭션 롤백의 보호를 받지 않아 직접 지운다. */
    private final List<String> redisKeysToClean = new ArrayList<>();

    @AfterEach
    void cleanUpRedis() {
        redisKeysToClean.forEach(redisTemplate::delete);
        redisKeysToClean.clear();
    }

    @Test
    void persistsSentimentLabelsInBatchOrder() {
        long sessionId = createSession();
        Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        when(chatAiGateway.ingest(anyLong(), any(), any())).thenReturn(Optional.of(
                new ChatAiIngestResult(Arrays.asList("JOY", null), null)
        ));

        BatchIngestResult result = relayService.appendChatMessages(sessionId, List.of(
                new ChatMessageItem("sha256:" + UUID.randomUUID(), "ㅋㅋㅋ 개웃기네", sentAt),
                new ChatMessageItem("sha256:" + UUID.randomUUID(), "?????", sentAt.plusMillis(200))
        ));

        assertThat(result.acceptedCount()).isEqualTo(2);
        List<String> labels = jdbcTemplate.queryForList(
                "SELECT sentiment_label FROM chat_messages WHERE session_id = ? ORDER BY sent_at",
                String.class, sessionId);
        assertThat(labels).containsExactly("JOY", null);
    }

    @Test
    void storesChatWithoutLabelsWhenAiSkipsBatch() {
        // AI가 죽어도 원본 저장은 계속되어야 한다(계약 — 실패 시 배치 스킵).
        long sessionId = createSession();
        when(chatAiGateway.ingest(anyLong(), any(), any())).thenReturn(Optional.empty());

        BatchIngestResult result = relayService.appendChatMessages(sessionId, List.of(
                new ChatMessageItem("sha256:" + UUID.randomUUID(), "메시지", Instant.now())
        ));

        assertThat(result.acceptedCount()).isEqualTo(1);
        Integer labeled = jdbcTemplate.queryForObject(
                "SELECT count(sentiment_label) FROM chat_messages WHERE session_id = ?",
                Integer.class, sessionId);
        assertThat(labeled).isZero();
    }

    @Test
    void recordsLatestSentimentWindowInSessionStateHash() {
        long sessionId = createSession();
        String stateKey = trackState(sessionId);
        sessionRedisGateway.initialize(sessionId, 1L, Instant.now());
        when(chatAiGateway.ingest(anyLong(), any(), any())).thenReturn(Optional.of(
                new ChatAiIngestResult(
                        List.of("JOY"),
                        new SentimentWindow(180, 6185, 0.214, "JOY", Map.of("JOY", 1.0))
                )
        ));

        relayService.appendChatMessages(sessionId, List.of(
                new ChatMessageItem("sha256:" + UUID.randomUUID(), "ㅋㅋ", Instant.now())
        ));

        Object sentiment = redisTemplate.opsForHash().get(stateKey, "sentiment");
        assertThat(sentiment).isNotNull();
        assertThat(sentiment.toString()).contains("\"analyzedChat\":6185");
        assertThat(redisTemplate.opsForHash().get(stateKey, "sentimentAt")).isNotNull();
    }

    @Test
    void recordsLatestViewerCountInSessionStateHash() {
        long sessionId = createSession();
        String stateKey = trackState(sessionId);
        sessionRedisGateway.initialize(sessionId, 1L, Instant.now());
        Instant measuredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        relayService.appendViewerMetrics(sessionId, List.of(
                new ViewerMetricItem(measuredAt.minusSeconds(30), 2000),
                new ViewerMetricItem(measuredAt, 2417)
        ));

        assertThat(redisTemplate.opsForHash().get(stateKey, "currentViewers"))
                .hasToString("2417");
    }

    @Test
    void platformViewerMetricsPersistAndFeedSnapshot() {
        // 수집기가 실제로 때리는 경로(플랫폼 세션 단위)다 — 레거시 세션 단위와 달리
        // 삽입 후 플랫폼 합산(combinedViewerCount)을 거쳐 판정기·스냅샷에 반영된다.
        long sessionId = createSession();
        String stateKey = trackState(sessionId);
        sessionRedisGateway.initialize(sessionId, 1L, Instant.now());
        Long platformSessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM broadcast_platform_sessions WHERE broadcast_session_id = ?",
                Long.class, sessionId);
        Instant measuredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        BatchIngestResult result = relayService.appendPlatformViewerMetrics(
                platformSessionId, List.of(new ViewerMetricItem(measuredAt, 7)));

        assertThat(result.acceptedCount()).isEqualTo(1);
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT viewer_count FROM viewer_metrics WHERE platform_session_id = ?",
                Integer.class, platformSessionId);
        assertThat(stored).isEqualTo(7);
        assertThat(redisTemplate.opsForHash().get(stateKey, "currentViewers")).hasToString("7");
    }

    // --- Fixture (CollectionIngestIntegrationTest와 동일 구성) ---

    private String trackState(long sessionId) {
        String key = SessionRedisGateway.stateKey(sessionId);
        redisKeysToClean.add(key);
        return key;
    }

    private long createSession() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        return transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId, null, "중계 테스트 방송", null, "ko")
        ).sessionId();
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                null, "중계 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "relay-test-" + UUID.randomUUID(),
                "테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }
}
