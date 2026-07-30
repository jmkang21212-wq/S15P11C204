package com.example.laive.broadcast.questions.service;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.questions.domain.RepeatedQuestionView;
import com.example.laive.broadcast.questions.repository.mybatis.RepeatedQuestionMapper;
import com.example.laive.broadcast.realtime.domain.ChatAiQuestionsResult;
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
 * 재질문 감지 3컬럼 알고리즘(계약 AI/CHAT_AI_API.md §3 ①②③)을 실제 스키마에서
 * 시간순 시나리오로 검증한다. {@code apply(sessionId, result, now)}의 now 주입이
 * 시각 제어점이라 Clock 목킹 없이 유예 경과를 몰 수 있다.
 *
 * <p>설정 기본값 전제: dismiss-grace 60s, reexpose-threshold 10.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class RepeatedQuestionSyncServiceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-07-30T12:00:00Z");
    private static final long Q1 = 1753690000000007L;
    private static final long Q2 = 1753690000000012L;

    @Autowired
    private RepeatedQuestionSyncService syncService;

    @Autowired
    private RepeatedQuestionMapper mapper;

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
    void upsertInsertsAndSkipsUnchangedRows() {
        syncService.apply(sessionId, response(question(Q1, "오늘 몇 시까지 하세요?", "FAQ", 12, 15)), T0);

        // 같은 값 재폴링: IS DISTINCT FROM 조건 덕에 행을 건드리지 않아야 한다.
        // type null 케이스도 함께 — (null, null)은 DISTINCT가 아니다.
        syncService.apply(sessionId, response(question(Q1, "오늘 몇 시까지 하세요?", "FAQ", 12, 15)), T0.plusSeconds(15));

        Map<String, Object> row = questionRow(Q1);
        assertThat(row.get("updated_at")).isEqualTo(java.sql.Timestamp.from(T0));

        // 값이 바뀌면 갱신된다.
        syncService.apply(sessionId, response(question(Q1, "오늘 몇 시까지 하세요?", "FAQ", 13, 16)), T0.plusSeconds(30));
        row = questionRow(Q1);
        assertThat(((Number) row.get("askers")).intValue()).isEqualTo(13);
        assertThat(row.get("updated_at")).isEqualTo(java.sql.Timestamp.from(T0.plusSeconds(30)));
    }

    @Test
    void unchangedNullTypeRowIsNotRewritten() {
        syncService.apply(sessionId, response(question(Q1, "저게 안맞아?", null, 5, 5)), T0);
        syncService.apply(sessionId, response(question(Q1, "저게 안맞아?", null, 5, 5)), T0.plusSeconds(15));

        assertThat(questionRow(Q1).get("updated_at")).isEqualTo(java.sql.Timestamp.from(T0));
    }

    @Test
    void upsertNeverTouchesDismissStateOrFirstAskedAt() {
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 12, 15)), T0);
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(10));

        // 다음 폴링이 값을 갱신해도 dismissed_at은 살아 있어야 한다(전량 교체 금지의 요체).
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 14, 18)), T0.plusSeconds(20));

        Map<String, Object> row = questionRow(Q1);
        assertThat(row.get("dismissed_at")).isNotNull();
        assertThat(((Number) row.get("askers")).intValue()).isEqualTo(14);
        assertThat(row.get("first_asked_at")).isEqualTo(java.sql.Timestamp.from(FIRST_ASKED));
    }

    @Test
    void baselineStaysNullDuringGraceAndConfirmsFromResponseAfterGrace() {
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 12, 15)), T0);
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(10)); // ① askers_at_dismiss = 12

        // 유예(60초) 안: baseline 미확정.
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 15, 20)), T0.plusSeconds(69));
        assertThat(questionRow(Q1).get("baseline_askers")).isNull();

        // 유예 경과: 이번 응답의 askers로 확정 — 관성 유입(12→17)이 baseline에 흡수된다.
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 17, 23)), T0.plusSeconds(71));
        assertThat(((Number) questionRow(Q1).get("baseline_askers")).intValue()).isEqualTo(17);
    }

    @Test
    void baselineFallsBackToAskersAtDismissWhenQuestionLeftTopN() {
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 12, 15)), T0);
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(10)); // askers_at_dismiss = 12

        // 유예 경과 후 응답에 Q1이 없다(top-N 밖으로 밀림) → fallback 확정.
        syncService.apply(sessionId, response(question(Q2, "다른 질문", null, 30, 40)), T0.plusSeconds(80));

        assertThat(((Number) questionRow(Q1).get("baseline_askers")).intValue()).isEqualTo(12);
    }

    @Test
    void emptyResponseStillConfirmsBaselineByFallback() {
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 12, 15)), T0);
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(10));

        syncService.apply(sessionId, response(), T0.plusSeconds(80)); // 빈 questions

        assertThat(((Number) questionRow(Q1).get("baseline_askers")).intValue()).isEqualTo(12);
    }

    @Test
    void reexposesWhenAskersGrowBeyondThreshold() {
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 12, 15)), T0);
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(10));
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 17, 23)), T0.plusSeconds(80)); // baseline 17

        // +9는 부족.
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 26, 33)), T0.plusSeconds(100));
        assertThat(questionRow(Q1).get("dismissed_at")).isNotNull();

        // +10 도달 → 재노출: dismiss 3컬럼 초기화, reexposed_at 기록.
        Instant reexposeAt = T0.plusSeconds(120);
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 27, 35)), reexposeAt);

        Map<String, Object> row = questionRow(Q1);
        assertThat(row.get("dismissed_at")).isNull();
        assertThat(row.get("askers_at_dismiss")).isNull();
        assertThat(row.get("baseline_askers")).isNull();
        assertThat(row.get("reexposed_at")).isEqualTo(java.sql.Timestamp.from(reexposeAt));

        List<RepeatedQuestionView> visible = mapper.findVisible(sessionId, 50);
        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).reasked()).isTrue();
    }

    @Test
    void aggregationResetLowersBaselineAndSkipsJudgementThatCycle() {
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 30, 40)), T0);
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(10));
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 32, 45)), T0.plusSeconds(80)); // baseline 32

        // 집계 리셋: askers가 4로 곤두박질. 방어가 baseline을 4로 낮추고 이번 판정은 스킵.
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 4, 4)), T0.plusSeconds(100));
        Map<String, Object> row = questionRow(Q1);
        assertThat(((Number) row.get("baseline_askers")).intValue()).isEqualTo(4);
        assertThat(row.get("dismissed_at")).isNotNull(); // 재노출되지 않았다

        // 다음 폴링부터 낮아진 baseline 기준으로 판정한다: 4 → 14 = +10 → 재노출.
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 14, 15)), T0.plusSeconds(120));
        assertThat(questionRow(Q1).get("dismissed_at")).isNull();
    }

    @Test
    void redismissAfterReexposureRestartsCycleAndKeepsReexposedAt() {
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 12, 15)), T0);
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(10));
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 17, 23)), T0.plusSeconds(80));  // baseline 17
        Instant reexposeAt = T0.plusSeconds(120);
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 27, 35)), reexposeAt);          // 재노출

        // 재차 dismiss: ①이 새로 시작되고 reexposed_at은 보존된다(리포트 근거).
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(150));
        Map<String, Object> row = questionRow(Q1);
        assertThat(row.get("dismissed_at")).isEqualTo(java.sql.Timestamp.from(T0.plusSeconds(150)));
        assertThat(((Number) row.get("askers_at_dismiss")).intValue()).isEqualTo(27);
        assertThat(row.get("baseline_askers")).isNull();
        assertThat(row.get("reexposed_at")).isEqualTo(java.sql.Timestamp.from(reexposeAt));
    }

    @Test
    void dismissIsIdempotent() {
        syncService.apply(sessionId, response(question(Q1, "질문", "FAQ", 12, 15)), T0);

        assertThat(mapper.dismiss(sessionId, Q1, T0.plusSeconds(10))).isEqualTo(1);
        // 중복 클릭: 0행 — 첫 dismiss의 시각과 askers_at_dismiss가 보존된다.
        assertThat(mapper.dismiss(sessionId, Q1, T0.plusSeconds(20))).isZero();
        assertThat(questionRow(Q1).get("dismissed_at")).isEqualTo(java.sql.Timestamp.from(T0.plusSeconds(10)));
    }

    @Test
    void findVisibleFiltersDismissedAndOrdersByAskersDesc() {
        syncService.apply(sessionId, response(
                question(Q1, "첫째", "FAQ", 12, 15),
                question(Q2, "둘째", null, 40, 44)
        ), T0);
        mapper.dismiss(sessionId, Q1, T0.plusSeconds(10));

        List<RepeatedQuestionView> visible = mapper.findVisible(sessionId, 50);

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).questionId()).isEqualTo(Q2);
        assertThat(visible.get(0).askers()).isEqualTo(40);
        assertThat(visible.get(0).reasked()).isFalse();
        assertThat(visible.get(0).type()).isNull();
    }

    // --- Fixture ---

    private static final Instant FIRST_ASKED = Instant.parse("2026-07-30T11:50:00Z");

    private ChatAiQuestionsResult response(ChatAiQuestionsResult.Question... questions) {
        return new ChatAiQuestionsResult(List.of(questions), 100, 0, T0);
    }

    private ChatAiQuestionsResult.Question question(long id, String text, String type, int askers, int messages) {
        return new ChatAiQuestionsResult.Question(
                id, text, type, askers, messages, FIRST_ASKED, FIRST_ASKED.plusSeconds(300));
    }

    private Map<String, Object> questionRow(long questionId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM repeated_questions WHERE session_id = ? AND question_id = ?",
                sessionId, questionId);
    }

    private StartBroadcastSessionResult createSession() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        return transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId, null, "반복 질문 테스트 방송", null, "ko")
        );
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                null, "반복 질문 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "repeated-question-test-" + UUID.randomUUID(),
                "테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }
}
