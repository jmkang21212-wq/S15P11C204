package com.example.laive.broadcast.analysis;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.analysis.domain.PromiseDetection;
import com.example.laive.broadcast.analysis.domain.RiskAlert;
import com.example.laive.broadcast.analysis.domain.TranscriptItem;
import com.example.laive.broadcast.analysis.service.AnalysisIngestService;
import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AI 서버가 보낸 전사·위험 발언·약속이 실제 PostgreSQL에 저장되는지 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.internal.service-token=test-internal-service-token-32bytes!",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class AnalysisIngestIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-20T13:00:00Z");

    @Autowired
    private AnalysisIngestService service;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 전사를_배치로_저장하고_방송_시작_기준_밀리초로_환산한다() {
        long sessionId = createSession();

        BatchIngestResult result = service.appendTranscripts(sessionId, List.of(
                new TranscriptItem("첫 발화입니다",
                        STARTED_AT.plusSeconds(10), STARTED_AT.plusSeconds(13)),
                new TranscriptItem("둘째 발화입니다",
                        STARTED_AT.plusSeconds(20), STARTED_AT.plusSeconds(24))
        ));

        assertThat(result.acceptedCount()).isEqualTo(2);
        assertThat(result.duplicateCount()).isZero();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT start_ms, end_ms, transcript FROM transcripts "
                        + "WHERE session_id = ? ORDER BY start_ms", sessionId);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("start_ms", 10_000L)
                .containsEntry("end_ms", 13_000L)
                .containsEntry("transcript", "첫 발화입니다");
        assertThat(rows.get(1)).containsEntry("start_ms", 20_000L)
                .containsEntry("end_ms", 24_000L);
    }

    /** 재전송해도 (session_id, start_ms, end_ms) Unique가 흡수해야 한다. */
    @Test
    void 같은_전사를_다시_보내면_중복으로_센다() {
        long sessionId = createSession();
        List<TranscriptItem> items = List.of(new TranscriptItem(
                "재전송 발화", STARTED_AT.plusSeconds(5), STARTED_AT.plusSeconds(8)));

        assertThat(service.appendTranscripts(sessionId, items).acceptedCount()).isEqualTo(1);

        BatchIngestResult again = service.appendTranscripts(sessionId, items);
        assertThat(again.acceptedCount()).isZero();
        assertThat(again.duplicateCount()).isEqualTo(1);
        assertThat(countTranscripts(sessionId)).isEqualTo(1);
    }

    /** 같은 배치에 동일 구간이 섞여 오면 접어야 한다. 안 접으면 배치 전체가 실패한다. */
    @Test
    void 같은_배치_안의_중복은_접는다() {
        long sessionId = createSession();
        Instant from = STARTED_AT.plusSeconds(15);
        Instant to = STARTED_AT.plusSeconds(18);

        BatchIngestResult result = service.appendTranscripts(sessionId, List.of(
                new TranscriptItem("같은 발화", from, to),
                new TranscriptItem("같은 발화", from, to)
        ));

        assertThat(result.acceptedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(countTranscripts(sessionId)).isEqualTo(1);
    }

    /**
     * 위험 경고는 즉시, 전사는 배치라 경고가 먼저 도착한다.
     * 경고에 실린 원문으로 전사를 만들어 붙여야 순서를 안 따진다.
     */
    @Test
    void 전사보다_먼저_도착한_위험_경고도_저장된다() {
        long sessionId = createSession();

        long riskEventId = service.appendRiskEvent(sessionId, new RiskAlert(
                "DICTIONARY", "HIGH", "PROFANITY", "아 씨발 뭐야",
                STARTED_AT.plusSeconds(30), STARTED_AT.plusSeconds(33), null
        ));

        assertThat(riskEventId).isPositive();
        assertThat(countTranscripts(sessionId)).isEqualTo(1);

        Map<String, Object> risk = jdbcTemplate.queryForMap(
                "SELECT risk_level, risk_category, transcript_id FROM risk_events WHERE id = ?",
                riskEventId);
        assertThat(risk).containsEntry("risk_level", "HIGH")
                .containsEntry("risk_category", "PROFANITY");

        Map<String, Object> transcript = jdbcTemplate.queryForMap(
                "SELECT transcript, start_ms FROM transcripts WHERE id = ?",
                risk.get("transcript_id"));
        assertThat(transcript).containsEntry("transcript", "아 씨발 뭐야")
                .containsEntry("start_ms", 30_000L);

        assertThat(briefingTypes(sessionId)).containsExactly("RISK");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT promise_id FROM live_briefings WHERE session_id = ?", sessionId))
                .containsEntry("promise_id", null);
    }

    /** 뒤늦게 같은 발화가 배치로 와도 행이 늘면 안 된다. */
    @Test
    void 경고가_만든_전사를_배치가_덮어써도_행이_늘지_않는다() {
        long sessionId = createSession();
        Instant from = STARTED_AT.plusSeconds(40);
        Instant to = STARTED_AT.plusSeconds(43);

        service.appendRiskEvent(sessionId, new RiskAlert(
                "LLM", "MEDIUM", "DEFAMATION", "너 진짜 별로다", from, to, "모욕 표현"));

        BatchIngestResult batch = service.appendTranscripts(sessionId, List.of(
                new TranscriptItem("너 진짜 별로다", from, to)));

        assertThat(batch.duplicateCount()).isEqualTo(1);
        assertThat(countTranscripts(sessionId)).isEqualTo(1);
    }

    @Test
    void 약속을_DETECTED로_저장하고_기한을_날짜로_바꾼다() {
        long sessionId = createSession();

        long promiseId = service.appendPromise(sessionId, new PromiseDetection(
                "공포게임 방송",
                "2주 안으로 공포게임 할게요",
                Instant.parse("2026-08-03T12:00:00Z"),
                STARTED_AT.plusSeconds(60)
        ));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT content, status, due_date, streamer_id FROM promises WHERE id = ?",
                promiseId);

        assertThat(row).containsEntry("content", "공포게임 방송")
                .containsEntry("status", "DETECTED");
        assertThat(((java.sql.Date) row.get("due_date")).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(row.get("streamer_id")).isNotNull();

        // 브리핑이 약속 id를 실어야 스냅샷 복구 후에도 확정/무시 버튼이 산다.
        Map<String, Object> briefing = jdbcTemplate.queryForMap(
                "SELECT briefing_type, promise_id FROM live_briefings WHERE session_id = ?",
                sessionId);
        assertThat(briefing).containsEntry("briefing_type", "PROMISE")
                .containsEntry("promise_id", promiseId);
    }

    /**
     * 기한을 UTC로 자르면 스트리머가 기대한 날짜보다 하루 빨라진다.
     *
     * <p>2026-08-03T16:00:00Z는 한국 시각으로 8월 4일 새벽 1시다. 자정을 넘겨 방송하며
     * "2주 안에" 같은 약속을 하면 이 구간에 떨어진다. 위 테스트가 12:00Z를 쓰는 바람에
     * UTC·KST 날짜가 같아 이 결함을 잡지 못했다.
     */
    @Test
    void 기한은_한국_시각_기준_날짜로_저장한다() {
        long sessionId = createSession();

        long promiseId = service.appendPromise(sessionId, new PromiseDetection(
                "심야 약속",
                "2주 안으로 할게요",
                Instant.parse("2026-08-03T16:00:00Z"),
                STARTED_AT.plusSeconds(80)
        ));

        assertThat(((java.sql.Date) jdbcTemplate.queryForMap(
                "SELECT due_date FROM promises WHERE id = ?", promiseId).get("due_date"))
                .toLocalDate())
                .isEqualTo(LocalDate.of(2026, 8, 4));
    }

    @Test
    void 기한이_없는_약속은_due_date가_비어_있다() {
        long sessionId = createSession();

        long promiseId = service.appendPromise(sessionId, new PromiseDetection(
                "다음에 하기", "다음에 꼭 할게요", null, STARTED_AT.plusSeconds(70)));

        assertThat(jdbcTemplate.queryForMap(
                "SELECT due_date FROM promises WHERE id = ?", promiseId))
                .containsEntry("due_date", null);
    }

    /** 방송 시작 직전에 잡힌 발화는 음수가 되는데 CHECK가 0 이상을 요구한다. */
    @Test
    void 방송_시작보다_이른_발화는_0으로_당긴다() {
        long sessionId = createSession();

        service.appendTranscripts(sessionId, List.of(new TranscriptItem(
                "시작 직전 발화", STARTED_AT.minusSeconds(3), STARTED_AT.minusSeconds(1))));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT start_ms, end_ms FROM transcripts WHERE session_id = ?", sessionId);
        assertThat(row).containsEntry("start_ms", 0L);
        assertThat((Long) row.get("end_ms")).isGreaterThan(0L);
    }

    @Test
    void 없는_세션이면_404다() {
        assertThatThrownBy(() -> service.appendRiskEvent(999_999L, new RiskAlert(
                "DICTIONARY", "HIGH", "PROFANITY", "욕설",
                STARTED_AT, STARTED_AT.plusSeconds(1), null)))
                .isInstanceOf(BroadcastSessionException.class)
                .extracting("code")
                .isEqualTo("SESSION_NOT_FOUND");
    }

    private int countTranscripts(long sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM transcripts WHERE session_id = ?", Integer.class, sessionId);
    }

    private List<String> briefingTypes(long sessionId) {
        return jdbcTemplate.queryForList(
                "SELECT briefing_type FROM live_briefings WHERE session_id = ? ORDER BY created_at",
                String.class, sessionId);
    }

    private long createSession() {
        StreamerInsert insert = new StreamerInsert(
                null, "분석 수집 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        long streamerId = insert.getId();

        Instant now = Instant.now();
        long platformAccountId = platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId, "CHZZK", "analysis-test-" + UUID.randomUUID(), "테스트 채널",
                "encrypted-access", "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), "user:read"
        )).orElseThrow();

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, title, status, started_at)
                VALUES (?, ?, '분석 수집 테스트 방송', 'LIVE', ?)
                RETURNING id
                """,
                Long.class,
                streamerId, platformAccountId, java.sql.Timestamp.from(STARTED_AT));
    }
}
