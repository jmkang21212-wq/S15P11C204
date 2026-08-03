package com.example.laive.promise;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.analysis.domain.PromiseDetection;
import com.example.laive.broadcast.analysis.service.AnalysisIngestService;
import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.calendar.dto.response.CalendarEventResponse;
import com.example.laive.calendar.service.CalendarEventCommandService;
import com.example.laive.calendar.service.CalendarEventQueryService;
import com.example.laive.common.api.PatchValue;
import com.example.laive.dashboard.repository.mybatis.DashboardMapper;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.PromiseCalendarItem;
import com.example.laive.promise.domain.PromiseStatus;
import com.example.laive.promise.domain.SchedulePromiseCommand;
import com.example.laive.promise.domain.UpdatePromiseCommand;
import com.example.laive.promise.dto.response.PromiseResponse;
import com.example.laive.promise.exception.PromiseException;
import com.example.laive.promise.service.PromiseCommandService;
import com.example.laive.promise.service.PromiseQueryService;
import com.example.laive.promise.service.PromiseScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 약속 → 일정 전환(V21)이 실제 PostgreSQL에서 동작하는지 검증한다.
 *
 * <p>이 기능의 핵심은 두 테이블을 한 트랜잭션으로 묶는 것과, 취소했을 때 약속이 미아가 되지
 * 않는 것이다. 두 성질 모두 SQL 제약(ux_calendar_events_promise, ck_promises_status)과
 * 도메인 전이 규칙이 맞물려야 성립하므로 실제 DB로 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.internal.service-token=test-internal-service-token-32bytes!",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class PromiseScheduleIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-20T13:00:00Z");
    private static final Instant EVENT_START = Instant.parse("2026-08-20T21:00:00Z");
    private static final Instant EVENT_END = Instant.parse("2026-08-20T23:00:00Z");

    @Autowired
    private AnalysisIngestService ingestService;

    @Autowired
    private PromiseScheduleService scheduleService;

    @Autowired
    private PromiseCommandService promiseCommandService;

    @Autowired
    private PromiseQueryService promiseQueryService;

    @Autowired
    private CalendarEventCommandService calendarCommandService;

    @Autowired
    private CalendarEventQueryService calendarQueryService;

    @Autowired
    private DashboardMapper dashboardMapper;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ 전환

    /** 전환의 계약 전체를 한 번에 확인한다. 하나라도 어긋나면 프론트가 잘못된 화면을 그린다. */
    @Test
    void 전환하면_일정이_생기고_약속은_SCHEDULED가_되며_기한은_비어_있다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "프로젝트 준비 완료");

        CalendarEvent event = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "프로젝트 준비 완료", null));

        assertThat(event.calendarEventId()).isNotNull();
        assertThat(event.promiseId()).isEqualTo(promiseId);
        assertThat(event.eventType()).isEqualTo(CalendarEventType.BROADCAST);
        assertThat(event.platformAccountId()).isEqualTo(f.platformAccountId());
        assertThat(event.status()).isEqualTo(CalendarEventStatus.SCHEDULED);
        assertThat(event.startAt()).isEqualTo(EVENT_START);
        assertThat(event.endAt()).isEqualTo(EVENT_END);

        assertThat(statusOf(promiseId)).isEqualTo("SCHEDULED");
        // 기한을 채우면 취소 후 "기한 미정" 목록에 돌아오지 못한다. NULL이어야 한다.
        assertThat(dueDateOf(promiseId)).isNull();
    }

    @Test
    void 제목을_생략하면_약속_내용을_200자로_잘라_쓴다() {
        Fixture f = createFixture();
        String longContent = "가".repeat(400);
        long promiseId = confirmedPromise(f, longContent);

        CalendarEvent event = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, null, null));

        assertThat(event.title()).hasSize(200);
        assertThat(event.title()).isEqualTo(longContent.substring(0, 200));
    }

    @Test
    void 짧은_내용은_그대로_제목이_된다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "구독자 추첨 이벤트");

        CalendarEvent event = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, null, "메모"));

        assertThat(event.title()).isEqualTo("구독자 추첨 이벤트");
        assertThat(event.memo()).isEqualTo("메모");
    }

    @Test
    void 보류_상태의_약속도_전환할_수_있다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "보류했던 약속");
        promiseCommandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.ON_HOLD), null, null));

        scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null));

        assertThat(statusOf(promiseId)).isEqualTo("SCHEDULED");
    }

    // ------------------------------------------------------------------ 검증

    @Test
    void 확정_전_약속은_전환할_수_없다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "확정 안 한 약속", 60);

        assertThatThrownBy(() -> scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null)))
                .isInstanceOf(PromiseException.class)
                .extracting("code")
                .isEqualTo("PROMISE_NOT_SCHEDULABLE");
    }

    @Test
    void 이미_완료된_약속은_전환할_수_없다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "이미 지킨 약속");
        promiseCommandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.COMPLETED), null, null));

        assertThatThrownBy(() -> scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null)))
                .isInstanceOf(PromiseException.class)
                .extracting("code")
                .isEqualTo("PROMISE_NOT_SCHEDULABLE");
    }

    /** 두 번 전환하면 같은 약속에 활성 일정이 둘 생긴다. 서비스가 먼저 막는다. */
    @Test
    void 이미_전환된_약속을_다시_전환하면_409다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "한 번만 전환할 약속");
        scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "첫 일정", null));

        // SCHEDULED라 상태 검사에서 먼저 걸린다.
        assertThatThrownBy(() -> scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "둘째 일정", null)))
                .isInstanceOf(PromiseException.class)
                .extracting("code")
                .isEqualTo("PROMISE_NOT_SCHEDULABLE");
    }

    /**
     * 상태 검사를 통과했는데도 활성 일정이 남아 있는 경우다.
     *
     * <p>전환 뒤 약속만 PENDING으로 PATCH하면 이 상태가 된다. 상태만 보면 전환 가능해 보이지만
     * 일정이 살아 있어 두 번째 일정이 생기면 안 된다.
     */
    @Test
    void 활성_일정이_남은_약속은_상태가_PENDING이어도_409다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "일정만 남은 약속");
        scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "살아 있는 일정", null));
        promiseCommandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.PENDING), null, null));

        assertThatThrownBy(() -> scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "둘째 일정", null)))
                .isInstanceOf(PromiseException.class)
                .extracting("code")
                .isEqualTo("PROMISE_ALREADY_SCHEDULED");
    }

    @Test
    void 플랫폼_연동이_끊겨_있으면_전환할_수_없다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "연동 없는 약속");
        jdbcTemplate.update(
                "UPDATE platform_accounts SET connection_status = 'DISCONNECTED' WHERE id = ?",
                f.platformAccountId());

        assertThatThrownBy(() -> scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null)))
                .isInstanceOf(PromiseException.class)
                .extracting("code")
                .isEqualTo("PROMISE_PLATFORM_ACCOUNT_REQUIRED");
    }

    /** refresh_token이 만료된 계정은 CONNECTED로 남아 있어도 쓸 수 없다. */
    @Test
    void 재인증이_필요한_계정은_연동으로_세지_않는다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "재인증 필요한 약속");
        jdbcTemplate.update(
                "UPDATE platform_accounts SET refresh_token_expires_at = now() - interval '1 day' WHERE id = ?",
                f.platformAccountId());

        assertThatThrownBy(() -> scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null)))
                .isInstanceOf(PromiseException.class)
                .extracting("code")
                .isEqualTo("PROMISE_PLATFORM_ACCOUNT_REQUIRED");
    }

    @Test
    void 종료가_시작보다_빠르면_400이다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "시간이 뒤집힌 약속");

        assertThatThrownBy(() -> scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_END, EVENT_START, "일정", null)))
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_INVALID_PERIOD");
    }

    @Test
    void 같은_시각이면_400이다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "길이 0인 약속");

        assertThatThrownBy(() -> scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_START, "일정", null)))
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_INVALID_PERIOD");
    }

    @Test
    void 남의_약속은_전환할_수_없다() {
        Fixture mine = createFixture();
        Fixture others = createFixture();
        long othersPromise = confirmedPromise(others, "남의 약속");

        assertThatThrownBy(() -> scheduleService.schedule(mine.streamerId(), othersPromise,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null)))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** 도메인·서비스 검사를 모두 우회해도 부분 UNIQUE 인덱스가 마지막으로 막는다. */
    @Test
    void DB_인덱스가_한_약속의_활성_일정_둘을_막는다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "인덱스로 막을 약속");
        scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "첫 일정", null));

        assertThatThrownBy(() -> insertEventDirectly(f, promiseId, "SCHEDULED"))
                .hasMessageContaining("ux_calendar_events_promise");
    }

    /** 취소된 일정은 인덱스에서 빠지므로 여러 건 쌓여도 충돌하지 않는다. */
    @Test
    void 취소된_일정은_인덱스_대상이_아니다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "여러 번 취소할 약속");

        insertEventDirectly(f, promiseId, "CANCELLED");
        insertEventDirectly(f, promiseId, "CANCELLED");

        assertThat(activeEventIdOf(promiseId)).isNull();
        // 취소본이 쌓여 있어도 새로 전환할 수 있다.
        scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "새 일정", null));
        assertThat(statusOf(promiseId)).isEqualTo("SCHEDULED");
    }

    // ------------------------------------------------------- 취소 → 약속 복귀

    @Test
    void 일정을_취소하면_약속이_PENDING으로_돌아온다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "취소할 약속");
        CalendarEvent event = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "취소될 일정", null));

        cancel(f, event.calendarEventId());

        assertThat(statusOf(promiseId)).isEqualTo("PENDING");
        // 기한이 비어 있어야 "기한 미정" 목록에서 다시 날짜를 정할 수 있다.
        assertThat(dueDateOf(promiseId)).isNull();
        assertThat(activeEventIdOf(promiseId)).isNull();
    }

    /** 미아 문제가 실제로 해결됐는지 — 취소한 뒤 다른 날짜로 다시 잡을 수 있어야 한다. */
    @Test
    void 취소한_약속을_다른_날짜로_다시_잡을_수_있다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "다시 잡을 약속");
        CalendarEvent first = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "첫 일정", null));
        cancel(f, first.calendarEventId());

        CalendarEvent second = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(
                        EVENT_START.plus(7, ChronoUnit.DAYS),
                        EVENT_END.plus(7, ChronoUnit.DAYS),
                        "다시 잡은 일정", null));

        assertThat(second.calendarEventId()).isNotEqualTo(first.calendarEventId());
        assertThat(statusOf(promiseId)).isEqualTo("SCHEDULED");
        assertThat(activeEventIdOf(promiseId)).isEqualTo(second.calendarEventId());
    }

    /**
     * 전환 뒤 약속을 이미 지켰다고 표시했다면, 나중의 일정 취소가 그 결정을 덮지 않는다.
     *
     * <p>덮으면 이행 완료한 약속이 일정 정리 때문에 미이행으로 되살아난다.
     */
    @Test
    void 이미_완료로_옮긴_약속은_일정_취소가_되돌리지_않는다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "먼저 완료할 약속");
        CalendarEvent event = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null));
        promiseCommandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.COMPLETED), null, null));

        cancel(f, event.calendarEventId());

        assertThat(statusOf(promiseId)).isEqualTo("COMPLETED");
    }

    /** 직접 만든 일정을 취소하는 기존 경로가 그대로 동작해야 한다. */
    @Test
    void 약속과_무관한_일정_취소는_영향이_없다() {
        Fixture f = createFixture();
        Long eventId = jdbcTemplate.queryForObject(
                """
                INSERT INTO calendar_events
                    (streamer_id, platform_account_id, event_type, title, start_at, end_at, status)
                VALUES (?, ?, 'BROADCAST', '직접 만든 일정', ?, ?, 'SCHEDULED')
                RETURNING id
                """,
                Long.class,
                f.streamerId(), f.platformAccountId(),
                java.sql.Timestamp.from(EVENT_START), java.sql.Timestamp.from(EVENT_END));

        cancel(f, eventId);

        assertThat(calendarQueryService.getEvent(f.streamerId(), eventId).status())
                .isEqualTo(CalendarEventStatus.CANCELLED);
    }

    // ------------------------------------------------------------------ 노출

    @Test
    void 응답이_양방향_링크를_담는다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "링크를 확인할 약속");
        CalendarEvent event = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null));

        CalendarEventResponse eventResponse = CalendarEventResponse.from(
                calendarQueryService.getEvent(f.streamerId(), event.calendarEventId()));
        assertThat(eventResponse.promiseId()).isEqualTo(promiseId);

        PromiseResponse promiseResponse = PromiseResponse.from(findPromise(f, promiseId));
        assertThat(promiseResponse.calendarEventId()).isEqualTo(event.calendarEventId());
        assertThat(promiseResponse.status()).isEqualTo(PromiseStatus.SCHEDULED);
    }

    @Test
    void 직접_만든_일정과_전환_전_약속은_링크가_null이다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "아직 전환 안 한 약속");

        assertThat(PromiseResponse.from(findPromise(f, promiseId)).calendarEventId()).isNull();
    }

    /** 취소하면 링크가 끊어져 보인다. 그래야 프론트가 "다시 잡기"를 열어 줄 수 있다. */
    @Test
    void 일정을_취소하면_약속의_링크가_사라진다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "링크가 끊길 약속");
        CalendarEvent event = scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null));

        cancel(f, event.calendarEventId());

        assertThat(PromiseResponse.from(findPromise(f, promiseId)).calendarEventId()).isNull();
    }

    /** 전환된 약속은 칩으로 그리지 않는다. 일정이 대신 보이므로 두 번 그리면 안 된다. */
    @Test
    void 전환된_약속은_캘린더_약속_목록에서_빠진다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "캘린더에서 빠질 약속");
        promiseCommandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(null, null, PatchValue.of(LocalDate.of(2026, 8, 20))));
        assertThat(calendarPromises(f)).hasSize(1);

        scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null));

        assertThat(calendarPromises(f)).isEmpty();
    }

    /** 일정만 잡혔을 뿐 아직 지키지 않은 약속이라 미이행 집계에서 빠지면 안 된다. */
    @Test
    void 대시보드_미이행_집계는_SCHEDULED를_포함한다() {
        Fixture f = createFixture();
        long promiseId = confirmedPromise(f, "미이행으로 세어야 하는 약속");
        assertThat(dashboardMapper.countPendingPromises(f.streamerId())).isEqualTo(1);

        scheduleService.schedule(f.streamerId(), promiseId,
                new SchedulePromiseCommand(EVENT_START, EVENT_END, "일정", null));

        assertThat(dashboardMapper.countPendingPromises(f.streamerId())).isEqualTo(1);
        assertThat(dashboardMapper.findPendingPromises(f.streamerId(), 3))
                .extracting("promiseId")
                .containsExactly(promiseId);
    }

    // -------------------------------------------------------------- 픽스처

    private void cancel(Fixture f, long calendarEventId) {
        calendarCommandService.update(f.streamerId(), calendarEventId,
                new UpdateCalendarEventCommand(
                        null, null, null, null, null, null,
                        PatchValue.of(CalendarEventStatus.CANCELLED)));
    }

    private List<PromiseCalendarItem> calendarPromises(Fixture f) {
        return promiseQueryService.getCalendarPromises(
                f.streamerId(),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"));
    }

    private Promise findPromise(Fixture f, long promiseId) {
        return promiseQueryService.getPromises(f.streamerId(), null, 0, 50).items().stream()
                .filter(p -> p.promiseId() == promiseId)
                .findFirst()
                .orElseThrow();
    }

    private String statusOf(long promiseId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM promises WHERE id = ?", String.class, promiseId);
    }

    private LocalDate dueDateOf(long promiseId) {
        return jdbcTemplate.queryForObject(
                "SELECT due_date FROM promises WHERE id = ?", LocalDate.class, promiseId);
    }

    private Long activeEventIdOf(long promiseId) {
        return jdbcTemplate.queryForObject(
                "SELECT max(id) FROM calendar_events WHERE promise_id = ? AND status <> 'CANCELLED'",
                Long.class, promiseId);
    }

    /** 서비스 검증을 우회해 DB 제약만 확인할 때 쓴다. */
    private void insertEventDirectly(Fixture f, long promiseId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO calendar_events
                    (streamer_id, platform_account_id, event_type, title, start_at, end_at, status, promise_id)
                VALUES (?, ?, 'BROADCAST', '우회 삽입', ?, ?, ?, ?)
                """,
                f.streamerId(), f.platformAccountId(),
                java.sql.Timestamp.from(EVENT_START), java.sql.Timestamp.from(EVENT_END),
                status, promiseId);
    }

    /** AI 수신 경로로 약속을 만든다. transcript_id와 detectedAt 산식이 실제로 채워진다. */
    private long detectPromise(Fixture f, String content, long spokenAfterSeconds) {
        return ingestService.appendPromise(f.sessionId(), new PromiseDetection(
                content,
                content + " 할게요",
                null,
                STARTED_AT.plusSeconds(spokenAfterSeconds)
        ));
    }

    /** 전환 가능한 상태(PENDING·기한 미정)의 약속을 만든다. */
    private long confirmedPromise(Fixture f, String content) {
        long promiseId = detectPromise(f, content, 60);
        promiseCommandService.confirm(f.streamerId(), promiseId, null, null);
        return promiseId;
    }

    private Fixture createFixture() {
        StreamerInsert insert = new StreamerInsert(
                null, "약속 전환 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        long streamerId = insert.getId();

        Instant now = Instant.now();
        long platformAccountId = platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId, "CHZZK", "schedule-test-" + UUID.randomUUID(), "테스트 채널",
                "encrypted-access", "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), "user:read"
        )).orElseThrow();

        Long sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, title, status, started_at)
                VALUES (?, ?, '약속 전환 테스트 방송', 'LIVE', ?)
                RETURNING id
                """,
                Long.class,
                streamerId, platformAccountId, java.sql.Timestamp.from(STARTED_AT));

        return new Fixture(streamerId, platformAccountId, sessionId);
    }

    private record Fixture(long streamerId, long platformAccountId, long sessionId) {
    }
}
