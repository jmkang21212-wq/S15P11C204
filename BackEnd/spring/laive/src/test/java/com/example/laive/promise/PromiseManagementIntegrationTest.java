package com.example.laive.promise;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.analysis.domain.PromiseDetection;
import com.example.laive.broadcast.analysis.service.AnalysisIngestService;
import com.example.laive.common.api.PageResponse;
import com.example.laive.common.api.PatchValue;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.promise.domain.Promise;
import com.example.laive.promise.domain.PromiseCalendarItem;
import com.example.laive.promise.domain.PromiseStatus;
import com.example.laive.promise.domain.UpdatePromiseCommand;
import com.example.laive.promise.exception.PromiseException;
import com.example.laive.promise.service.PromiseCommandService;
import com.example.laive.promise.service.PromiseQueryService;
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
 * 약속 관리 API(명세 10.8)가 실제 PostgreSQL에서 동작하는지 검증한다.
 *
 * <p>약속은 AI 수신부가 만들어야 존재하므로 픽스처도 {@link AnalysisIngestService}를 통해
 * 만든다. 그래야 transcript_id·detectedAt 산식까지 실제 경로로 검증된다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.internal.service-token=test-internal-service-token-32bytes!",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class PromiseManagementIntegrationTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-20T13:00:00Z");

    @Autowired
    private AnalysisIngestService ingestService;

    @Autowired
    private PromiseQueryService queryService;

    @Autowired
    private PromiseCommandService commandService;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------------ 목록

    @Test
    void 목록은_상태로_거르고_detectedAt을_방송시작_더하기_startMs로_계산한다() {
        Fixture f = createFixture();
        long detected = detectPromise(f, "공포게임 방송", 60);
        long other = detectPromise(f, "스피드런 방송", 120);
        commandService.confirm(f.streamerId(), other, null, LocalDate.of(2026, 8, 1));

        PageResponse<Promise> all = queryService.getPromises(f.streamerId(), null, 0, 20);
        assertThat(all.totalElements()).isEqualTo(2);

        PageResponse<Promise> onlyDetected =
                queryService.getPromises(f.streamerId(), PromiseStatus.DETECTED, 0, 20);
        assertThat(onlyDetected.items())
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.promiseId()).isEqualTo(detected);
                    // 발화 60초 지점 → started_at + 60초
                    assertThat(p.detectedAt()).isEqualTo(STARTED_AT.plusSeconds(60));
                    assertThat(p.sessionId()).isEqualTo(f.sessionId());
                    assertThat(p.transcriptId()).isPositive();
                });
    }

    @Test
    void 목록은_0기반_페이지로_나눈다() {
        Fixture f = createFixture();
        for (int i = 0; i < 3; i++) {
            detectPromise(f, "약속 " + i, 10L * (i + 1));
        }

        PageResponse<Promise> first = queryService.getPromises(f.streamerId(), null, 0, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);

        PageResponse<Promise> second = queryService.getPromises(f.streamerId(), null, 1, 2);
        assertThat(second.items()).hasSize(1);
    }

    @Test
    void 다른_스트리머의_약속은_보이지_않는다() {
        Fixture mine = createFixture();
        Fixture others = createFixture();
        long othersPromise = detectPromise(others, "남의 약속", 30);

        assertThat(queryService.getPromises(mine.streamerId(), null, 0, 20).totalElements())
                .isZero();
        assertThatThrownBy(() -> commandService.confirm(
                mine.streamerId(), othersPromise, null, null))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------ 확정·무시

    @Test
    void 확정하면_내용과_기한을_반영하고_PENDING이_된다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);

        Promise confirmed = commandService.confirm(
                f.streamerId(), promiseId, "구독자 추첨 이벤트 진행", LocalDate.of(2026, 7, 27));

        assertThat(confirmed.status()).isEqualTo(PromiseStatus.PENDING);
        assertThat(confirmed.content()).isEqualTo("구독자 추첨 이벤트 진행");
        assertThat(confirmed.dueDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(statusOf(promiseId)).isEqualTo("PENDING");
    }

    /** 내용을 안 보내면 AI가 뽑은 문구를 그대로 쓴다. */
    @Test
    void 확정_요청에_내용이_없으면_기존_내용을_유지한다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "원래 문구", 60);

        Promise confirmed = commandService.confirm(f.streamerId(), promiseId, null, null);

        assertThat(confirmed.content()).isEqualTo("원래 문구");
        assertThat(confirmed.dueDate()).isNull();
    }

    @Test
    void 이미_확정된_약속을_다시_확정하면_409다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);
        commandService.confirm(f.streamerId(), promiseId, null, null);

        assertThatThrownBy(() -> commandService.confirm(f.streamerId(), promiseId, null, null))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 무시하면_DISMISSED가_되고_확정된_약속은_무시할_수_없다() {
        Fixture f = createFixture();
        long dismissed = detectPromise(f, "무시할 약속", 60);
        commandService.dismiss(f.streamerId(), dismissed);
        assertThat(statusOf(dismissed)).isEqualTo("DISMISSED");

        long confirmed = detectPromise(f, "확정할 약속", 70);
        commandService.confirm(f.streamerId(), confirmed, null, null);
        assertThatThrownBy(() -> commandService.dismiss(f.streamerId(), confirmed))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------------------------------------------------------------- PATCH

    @Test
    void 확정된_약속의_상태를_바꾼다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);
        commandService.confirm(f.streamerId(), promiseId, null, LocalDate.of(2026, 8, 1));

        Promise updated = commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.COMPLETED), null, null));

        assertThat(updated.status()).isEqualTo(PromiseStatus.COMPLETED);
        // 기한은 건드리지 않았으므로 유지되어야 한다
        assertThat(updated.dueDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(statusOf(promiseId)).isEqualTo("COMPLETED");
    }

    /** 캘린더에서 약속을 끌어 옮기는 시나리오다. dueDate만 오고 상태는 그대로여야 한다. */
    @Test
    void 기한만_보내면_날짜만_바뀌고_상태는_유지된다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);
        commandService.confirm(f.streamerId(), promiseId, null, LocalDate.of(2026, 8, 1));

        Promise moved = commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(null, null, PatchValue.of(LocalDate.of(2026, 8, 20))));

        assertThat(moved.dueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(moved.status()).isEqualTo(PromiseStatus.PENDING);
    }

    /** 명시적 null을 보내면 기한을 비운다. 미전송과 구분되어야 한다. */
    @Test
    void 기한에_null을_보내면_기한이_지워진다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);
        commandService.confirm(f.streamerId(), promiseId, null, LocalDate.of(2026, 8, 1));

        Promise cleared = commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(null, null, PatchValue.of(null)));

        assertThat(cleared.dueDate()).isNull();
    }

    @Test
    void 확정_전_약속은_PATCH로_바꿀_수_없다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);

        assertThatThrownBy(() -> commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.COMPLETED), null, null)))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    /**
     * 확정한 약속을 지우는 유일한 경로다.
     *
     * <p>전용 dismiss API는 DETECTED만 받으므로, 이게 없으면 한 번 확정한 약속은 영구히 남는다.
     * 일정은 CANCELLED로 취소되는데 약속만 지울 수 없는 비대칭이 여기서 해소된다.
     */
    @Test
    void 확정된_약속을_PATCH로_DISMISSED로_지운다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);
        commandService.confirm(f.streamerId(), promiseId, null, null);

        Promise dismissed = commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.DISMISSED), null, null));

        assertThat(dismissed.status()).isEqualTo(PromiseStatus.DISMISSED);
        assertThat(statusOf(promiseId)).isEqualTo("DISMISSED");
    }

    /** 한 번 버린 약속은 PATCH로 되살리지 않는다. */
    @Test
    void DISMISSED_약속은_PATCH로_되살릴_수_없다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "무시할 약속", 60);
        commandService.dismiss(f.streamerId(), promiseId);

        assertThatThrownBy(() -> commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.PENDING), null, null)))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void PATCH로_DETECTED나_SCHEDULED로는_갈_수_없다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);
        commandService.confirm(f.streamerId(), promiseId, null, null);

        // DETECTED는 확정 전 상태라 되돌아갈 수 없다.
        assertThatThrownBy(() -> commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.DETECTED), null, null)))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        // SCHEDULED는 일정이 함께 생겨야 하므로 전환 API만 만들 수 있다.
        assertThatThrownBy(() -> commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.SCHEDULED), null, null)))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 빈_PATCH_요청은_400이다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "공포게임 방송", 60);
        commandService.confirm(f.streamerId(), promiseId, null, null);

        assertThatThrownBy(() -> commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(null, null, null)))
                .isInstanceOf(PromiseException.class)
                .satisfies(e -> assertThat(((PromiseException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // -------------------------------------------------------------- 캘린더

    @Test
    void 기한이_잡힌_진행중_약속만_캘린더에_오른다() {
        Fixture f = createFixture();

        long onCalendar = detectPromise(f, "캘린더에 오를 약속", 10);
        commandService.confirm(f.streamerId(), onCalendar, null, LocalDate.of(2026, 8, 14));

        long noDate = detectPromise(f, "기한 없는 약속", 20);
        commandService.confirm(f.streamerId(), noDate, null, null);

        long stillDetected = detectPromise(f, "확정 안 한 약속", 30);

        List<PromiseCalendarItem> items = queryService.getCalendarPromises(
                f.streamerId(),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.promiseId()).isEqualTo(onCalendar);
            assertThat(item.dueDate()).isEqualTo(LocalDate.of(2026, 8, 14));
            assertThat(item.status()).isEqualTo(PromiseStatus.PENDING);
        });
        assertThat(items).extracting(PromiseCalendarItem::promiseId)
                .doesNotContain(noDate, stillDetected);
    }

    @Test
    void 완료된_약속은_캘린더에서_빠진다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "완료할 약속", 10);
        commandService.confirm(f.streamerId(), promiseId, null, LocalDate.of(2026, 8, 14));

        commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(PatchValue.of(PromiseStatus.COMPLETED), null, null));

        assertThat(calendarPromisesInAugust(f)).isEmpty();
    }

    /** 드래그로 다른 달에 놓으면 그 달 조회에서 나와야 한다. */
    @Test
    void 기한을_옮기면_해당_월_조회에_따라_움직인다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "옮길 약속", 10);
        commandService.confirm(f.streamerId(), promiseId, null, LocalDate.of(2026, 8, 14));
        assertThat(calendarPromisesInAugust(f)).hasSize(1);

        commandService.update(f.streamerId(), promiseId,
                new UpdatePromiseCommand(null, null, PatchValue.of(LocalDate.of(2026, 9, 3))));

        assertThat(calendarPromisesInAugust(f)).isEmpty();
        assertThat(queryService.getCalendarPromises(
                f.streamerId(),
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-30T00:00:00Z"))).hasSize(1);
    }

    /**
     * 조회 범위를 한국 시각 기준으로 잘라야 경계 하루가 어긋나지 않는다.
     *
     * <p>2026-08-31T15:00:00Z는 한국 시각으로 9월 1일 0시다. 9월 1일 약속이 포함되어야 한다.
     */
    @Test
    void 캘린더_범위는_한국_시각_기준으로_자른다() {
        Fixture f = createFixture();
        long promiseId = detectPromise(f, "9월 1일 약속", 10);
        commandService.confirm(f.streamerId(), promiseId, null, LocalDate.of(2026, 9, 1));

        List<PromiseCalendarItem> items = queryService.getCalendarPromises(
                f.streamerId(),
                Instant.parse("2026-08-31T15:00:00Z"),
                Instant.parse("2026-09-30T15:00:00Z"));

        assertThat(items).extracting(PromiseCalendarItem::promiseId).contains(promiseId);
    }

    // -------------------------------------------------------------- 픽스처

    private List<PromiseCalendarItem> calendarPromisesInAugust(Fixture f) {
        return queryService.getCalendarPromises(
                f.streamerId(),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-31T00:00:00Z"));
    }

    private String statusOf(long promiseId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM promises WHERE id = ?", String.class, promiseId);
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

    private Fixture createFixture() {
        StreamerInsert insert = new StreamerInsert(
                null, "약속 관리 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        long streamerId = insert.getId();

        Instant now = Instant.now();
        long platformAccountId = platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId, "CHZZK", "promise-test-" + UUID.randomUUID(), "테스트 채널",
                "encrypted-access", "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), "user:read"
        )).orElseThrow();

        Long sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, title, status, started_at)
                VALUES (?, ?, '약속 관리 테스트 방송', 'LIVE', ?)
                RETURNING id
                """,
                Long.class,
                streamerId, platformAccountId, java.sql.Timestamp.from(STARTED_AT));

        return new Fixture(streamerId, sessionId);
    }

    private record Fixture(long streamerId, long sessionId) {
    }
}
