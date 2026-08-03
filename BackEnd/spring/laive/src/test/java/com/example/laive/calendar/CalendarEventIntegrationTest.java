package com.example.laive.calendar;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.calendar.domain.CalendarEvent;
import com.example.laive.calendar.domain.CalendarEventStatus;
import com.example.laive.calendar.domain.CalendarEventType;
import com.example.laive.calendar.domain.CreateCalendarEventCommand;
import com.example.laive.calendar.domain.UpdateCalendarEventCommand;
import com.example.laive.calendar.exception.CalendarEventException;
import com.example.laive.calendar.repository.CalendarEventRepository;
import com.example.laive.calendar.service.CalendarEventCommandService;
import com.example.laive.calendar.service.CalendarEventQueryService;
import com.example.laive.common.api.PatchValue;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
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
 * 실제 PostgreSQL 스키마에서 record 매핑, 겹침 조회, 부분 수정과 CHECK 제약을 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class CalendarEventIntegrationTest {

    private static final Instant START = Instant.parse("2026-07-20T12:00:00Z");
    private static final Instant END = Instant.parse("2026-07-20T15:00:00Z");

    @Autowired
    private CalendarEventCommandService commandService;

    @Autowired
    private CalendarEventQueryService queryService;

    @Autowired
    private CalendarEventRepository repository;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BroadcastSessionTransactionService broadcastSessionTransactionService;

    private static UpdateCalendarEventCommand patch(
            PatchValue<Long> platformAccountId,
            PatchValue<CalendarEventType> eventType,
            PatchValue<String> title,
            PatchValue<String> memo,
            PatchValue<Instant> startAt,
            PatchValue<Instant> endAt,
            PatchValue<CalendarEventStatus> status
    ) {
        return new UpdateCalendarEventCommand(
                platformAccountId, eventType, title, memo, startAt, endAt, status
        );
    }

    @Test
    void persistsMultiplePlatformAccountsAndAllowsScheduledMultiPlatformSession() {
        long streamerId = createStreamer();
        long firstAccountId = createPlatformAccount(streamerId);
        long secondAccountId = createPlatformAccount(streamerId);

        CalendarEvent created = commandService.create(
                streamerId,
                new CreateCalendarEventCommand(
                        firstAccountId,
                        List.of(firstAccountId, secondAccountId),
                        CalendarEventType.BROADCAST,
                        "multi-platform schedule",
                        null,
                        START,
                        END
                )
        );

        assertThat(queryService.getPlatformAccountIds(created))
                .containsExactlyInAnyOrder(firstAccountId, secondAccountId);

        var started = broadcastSessionTransactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        List.of(firstAccountId, secondAccountId),
                        created.calendarEventId(),
                        "multi-platform broadcast",
                        "FPS",
                        "ko"
                )
        );

        assertThat(started.platformSessions()).hasSize(2);
    }

    /** 컬럼 순서가 어긋나면 record가 조용히 잘못 채워지므로 전 필드를 확인한다. */
    @Test
    void 생성한_일정을_모든_필드_그대로_다시_읽는다() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);

        CalendarEvent created = commandService.create(streamerId, new CreateCalendarEventCommand(
                platformAccountId,
                CalendarEventType.BROADCAST,
                "발로란트 랭크 정복 Day 12",
                "Day 11 이어서 불멸 티어 도전",
                START,
                END
        ));

        assertThat(created.calendarEventId()).isNotNull();

        CalendarEvent loaded = queryService.getEvent(streamerId, created.calendarEventId());

        assertThat(loaded.calendarEventId()).isEqualTo(created.calendarEventId());
        assertThat(loaded.streamerId()).isEqualTo(streamerId);
        assertThat(loaded.platformAccountId()).isEqualTo(platformAccountId);
        assertThat(loaded.eventType()).isEqualTo(CalendarEventType.BROADCAST);
        assertThat(loaded.title()).isEqualTo("발로란트 랭크 정복 Day 12");
        assertThat(loaded.memo()).isEqualTo("Day 11 이어서 불멸 티어 도전");
        assertThat(loaded.startAt()).isEqualTo(START);
        assertThat(loaded.endAt()).isEqualTo(END);
        assertThat(loaded.status()).isEqualTo(CalendarEventStatus.SCHEDULED);
    }

    @Test
    void 타인의_일정은_존재를_숨기고_404를_반환한다() {
        long owner = createStreamer();
        long other = createStreamer();
        long platformAccountId = createPlatformAccount(owner);

        CalendarEvent created = commandService.create(owner, new CreateCalendarEventCommand(
                platformAccountId, CalendarEventType.BROADCAST, "방송", null, START, END
        ));

        assertThatThrownBy(() -> queryService.getEvent(other, created.calendarEventId()))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_NOT_FOUND");
    }

    /** 경계를 걸치는 여러 날짜짜리 휴방이 조회에서 빠지면 캘린더에 구멍이 생긴다. */
    @Test
    void 기간_조회는_경계를_걸친_일정도_포함한다() {
        long streamerId = createStreamer();

        commandService.create(streamerId, new CreateCalendarEventCommand(
                null,
                CalendarEventType.DAY_OFF,
                "월말부터 다음달까지 휴방",
                null,
                Instant.parse("2026-07-30T00:00:00Z"),
                Instant.parse("2026-08-03T00:00:00Z")
        ));
        commandService.create(streamerId, new CreateCalendarEventCommand(
                null,
                CalendarEventType.DAY_OFF,
                "범위 밖 휴방",
                null,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z")
        ));

        List<CalendarEvent> events = queryService.getEvents(
                streamerId,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z")
        );

        assertThat(events)
                .extracting(CalendarEvent::title)
                .containsExactly("월말부터 다음달까지 휴방");
    }

    @Test
    void 조회_범위가_62일을_넘으면_거부한다() {
        long streamerId = createStreamer();

        assertThatThrownBy(() -> queryService.getEvents(
                streamerId,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-09-15T00:00:00Z")
        ))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_RANGE_TOO_WIDE");
    }

    @Test
    void 부분_수정은_보낸_필드만_바꾸고_명시적_null은_메모를_비운다() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);

        CalendarEvent created = commandService.create(streamerId, new CreateCalendarEventCommand(
                platformAccountId, CalendarEventType.BROADCAST, "원래 제목", "원래 메모", START, END
        ));

        commandService.update(streamerId, created.calendarEventId(), patch(
                null, null, PatchValue.of("바뀐 제목"), null, null, null, null
        ));

        CalendarEvent afterTitle = queryService.getEvent(streamerId, created.calendarEventId());
        assertThat(afterTitle.title()).isEqualTo("바뀐 제목");
        assertThat(afterTitle.memo()).isEqualTo("원래 메모");

        commandService.update(streamerId, created.calendarEventId(), patch(
                null, null, null, PatchValue.of(null), null, null, null
        ));

        CalendarEvent afterMemo = queryService.getEvent(streamerId, created.calendarEventId());
        assertThat(afterMemo.title()).isEqualTo("바뀐 제목");
        assertThat(afterMemo.memo()).isNull();
    }

    @Test
    void 취소는_상태_전이로_저장된다() {
        long streamerId = createStreamer();

        CalendarEvent created = commandService.create(streamerId, new CreateCalendarEventCommand(
                null, CalendarEventType.DAY_OFF, "휴방", null, START, END
        ));

        commandService.update(streamerId, created.calendarEventId(), patch(
                null, null, null, null, null, null, PatchValue.of(CalendarEventStatus.CANCELLED)
        ));

        assertThat(queryService.getEvent(streamerId, created.calendarEventId()).status())
                .isEqualTo(CalendarEventStatus.CANCELLED);
    }

    @Test
    void 방송_세션에_연결된_일정은_시간을_바꿀_수_없다() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);

        CalendarEvent created = commandService.create(streamerId, new CreateCalendarEventCommand(
                platformAccountId, CalendarEventType.BROADCAST, "방송", null, START, END
        ));
        linkBroadcastSession(streamerId, platformAccountId, created.calendarEventId());

        assertThat(repository.existsLinkedSession(created.calendarEventId())).isTrue();

        assertThatThrownBy(() -> commandService.update(streamerId, created.calendarEventId(), patch(
                null, null, null, null, PatchValue.of(START.plusSeconds(3600)), null, null
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("CALENDAR_EVENT_LINKED_TO_SESSION");

        commandService.update(streamerId, created.calendarEventId(), patch(
                null, null, PatchValue.of("제목만 변경"), null, null, null, null
        ));
        assertThat(queryService.getEvent(streamerId, created.calendarEventId()).title())
                .isEqualTo("제목만 변경");
    }

    @Test
    void 남의_플랫폼_계정으로는_방송_일정을_만들_수_없다() {
        long streamerId = createStreamer();
        long other = createStreamer();
        long othersAccount = createPlatformAccount(other);

        assertThatThrownBy(() -> commandService.create(streamerId, new CreateCalendarEventCommand(
                othersAccount, CalendarEventType.BROADCAST, "방송", null, START, END
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_ACCOUNT_NOT_FOUND");
    }

    @Test
    void 연결이_끊긴_플랫폼_계정은_거부한다() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        jdbcTemplate.update(
                "UPDATE platform_accounts SET connection_status = 'REAUTH_REQUIRED' WHERE id = ?",
                platformAccountId
        );

        assertThatThrownBy(() -> commandService.create(streamerId, new CreateCalendarEventCommand(
                platformAccountId, CalendarEventType.BROADCAST, "방송", null, START, END
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_ACCOUNT_NOT_CONNECTED");
    }

    @Test
    void 휴방을_방송으로_바꿀_때_플랫폼_계정_소유를_확인한다() {
        long streamerId = createStreamer();
        long other = createStreamer();
        long othersAccount = createPlatformAccount(other);

        CalendarEvent created = commandService.create(streamerId, new CreateCalendarEventCommand(
                null, CalendarEventType.DAY_OFF, "휴방", null, START, END
        ));

        assertThatThrownBy(() -> commandService.update(streamerId, created.calendarEventId(), patch(
                PatchValue.of(othersAccount), PatchValue.of(CalendarEventType.BROADCAST),
                null, null, null, null, null
        )))
                .isInstanceOf(CalendarEventException.class)
                .extracting("code")
                .isEqualTo("PLATFORM_ACCOUNT_NOT_FOUND");
    }

    /** 도메인 규칙을 우회해도 DB CHECK 제약이 마지막으로 막는지 확인한다. */
    @Test
    void DB_제약이_방송_일정의_플랫폼_계정_누락을_막는다() {
        long streamerId = createStreamer();

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO calendar_events
                    (streamer_id, platform_account_id, event_type, title, start_at, end_at, status)
                VALUES (?, NULL, 'BROADCAST', '규칙 우회 방송', ?, ?, 'SCHEDULED')
                """,
                streamerId,
                java.sql.Timestamp.from(START),
                java.sql.Timestamp.from(END)
        ))
                .hasMessageContaining("ck_calendar_events_broadcast_platform");
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(
                null,
                "캘린더 테스트 " + UUID.randomUUID(),
                null
        );
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "calendar-test-" + UUID.randomUUID(),
                "테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }

    /** ENDED·FAILED 세션은 ck_broadcast_sessions_terminal_ended_at 때문에 ended_at이 있어야 한다. */
    private void linkBroadcastSession(long streamerId, long platformAccountId, long calendarEventId) {
        jdbcTemplate.update(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, calendar_event_id, title, status, started_at, ended_at)
                VALUES (?, ?, ?, '연결된 방송', 'ENDED', now() - interval '3 hours', now())
                """,
                streamerId,
                platformAccountId,
                calendarEventId
        );
    }
}
