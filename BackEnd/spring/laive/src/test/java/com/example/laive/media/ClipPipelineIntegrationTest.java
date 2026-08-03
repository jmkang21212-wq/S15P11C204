package com.example.laive.media;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionCommand;
import com.example.laive.broadcast.session.domain.StartBroadcastSessionResult;
import com.example.laive.broadcast.session.service.BroadcastSessionTransactionService;
import com.example.laive.media.domain.RecordingStatus;
import com.example.laive.media.exception.MediaException;
import com.example.laive.media.service.ClipJobService;
import com.example.laive.media.service.ClipUploadService;
import com.example.laive.media.service.RecordingService;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 클립 파이프라인의 SQL이 실제 스키마에서 의도대로 도는지 검증한다.
 *
 * <p>단위 테스트({@code ClipOffsetCalculatorTest})가 계산을 고정한다면, 여기서는
 * 그 계산에 들어갈 값이 조인으로 제대로 모이는지와 상태 전이가 V12의 CHECK 제약을
 * 통과하는지를 본다. 특히 {@code clip_url}과 {@code clip_status}를 함께 써야 하는
 * 제약은 UPDATE 문을 실제로 실행해야만 드러난다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false",
        "laive.clip.storage-dir=build/test-clips",
        "laive.clip.public-base-url=https://laive.test/clips",
        "laive.clip.max-attempts=3"
})
@Transactional
class ClipPipelineIntegrationTest {

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private ClipJobService jobService;

    @Autowired
    private ClipUploadService uploadService;

    @Autowired
    private BroadcastSessionTransactionService transactionService;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 녹화_보고가_기준점과_파일_경로를_남긴다() {
        var session = createSession();
        long streamerId = streamerOf(session.sessionId());
        Instant base = session.startedAt();

        recordingService.start(streamerId, session.sessionId(), base, base, 0);
        recordingService.updateBase(streamerId, session.sessionId(), base.plusSeconds(60), 60_000);
        recordingService.finish(streamerId, session.sessionId(), base.plusSeconds(7200),
                "C:\\Videos\\rec.mkv", RecordingStatus.COMPLETED);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM recordings WHERE session_id = ?", session.sessionId());
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("output_path")).isEqualTo("C:\\Videos\\rec.mkv");
        assertThat(((Number) row.get("base_timecode_ms")).longValue()).isEqualTo(60_000L);
    }

    @Test
    void 녹화_시작을_두_번_보고해도_충돌하지_않는다() {
        // 앱이 응답을 못 받고 재시도하는 것이 정상 흐름이다.
        var session = createSession();
        long streamerId = streamerOf(session.sessionId());
        Instant base = session.startedAt();

        recordingService.start(streamerId, session.sessionId(), base, base, 0);
        recordingService.start(streamerId, session.sessionId(), base, base.plusSeconds(30), 30_000);

        Long timecode = jdbcTemplate.queryForObject(
                "SELECT base_timecode_ms FROM recordings WHERE session_id = ?",
                Long.class, session.sessionId());
        assertThat(timecode).isEqualTo(30_000L);
    }

    @Test
    void 남의_세션에는_보고할_수_없다() {
        var session = createSession();
        long stranger = createStreamer();

        assertThatThrownBy(() -> recordingService.start(
                stranger, session.sessionId(), Instant.now(), Instant.now(), 0))
                .isInstanceOf(MediaException.class)
                .hasMessageContaining("방송 세션을 찾을 수 없습니다");
    }

    @Test
    void 작업_목록이_송출_지연을_뺀_자를_위치를_준다() {
        var fixture = pendingHighlight();

        var jobs = jobService.findPending(fixture.streamerId());

        assertThat(jobs).hasSize(1);
        var job = jobs.getFirst();
        assertThat(job.highlightId()).isEqualTo(fixture.highlightId());
        assertThat(job.recordingPath()).isEqualTo("C:\\Videos\\rec.mkv");
        // 트리거는 방송 시작 +600초, 녹화 기준점은 방송 시작과 동일하므로
        // 600,000ms에서 송출 지연 4,500ms를 뺀 595.5초다.
        assertThat(job.cut().startSec()).isEqualTo(595.5);
        assertThat(job.cut().durationSec()).isEqualTo(60.0);
    }

    @Test
    void 녹화가_끝나지_않았으면_작업이_생기지_않는다() {
        var fixture = pendingHighlight();
        jdbcTemplate.update(
                "UPDATE recordings SET status = 'RECORDING' WHERE session_id = ?",
                fixture.sessionId());

        assertThat(jobService.findPending(fixture.streamerId())).isEmpty();
    }

    @Test
    void 업로드가_클립_키를_저장하고_절대_URL로_답한다() {
        var fixture = pendingHighlight();

        var uploaded = uploadService.upload(
                fixture.streamerId(), fixture.highlightId(), clipFile(), thumbnailFile());

        assertThat(uploaded.clipUrl())
                .isEqualTo("https://laive.test/clips/" + fixture.sessionId() + "/" + fixture.highlightId() + ".mp4");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT clip_url, thumbnail_url, clip_status FROM highlights WHERE id = ?",
                fixture.highlightId());
        // DB에는 키만 들어간다 — 도메인이 바뀌어도 기존 클립이 죽지 않게 하기 위해서다.
        assertThat(row.get("clip_url")).isEqualTo(fixture.sessionId() + "/" + fixture.highlightId() + ".mp4");
        assertThat(row.get("clip_status")).isEqualTo("UPLOADED");
    }

    @Test
    void 이미_올린_클립에_재업로드가_오면_기존_URL로_답한다() {
        // 저장은 됐는데 응답을 못 받고 앱이 꺼진 경우다. 409를 내면 앱이 영원히 재시도한다.
        var fixture = pendingHighlight();
        var first = uploadService.upload(
                fixture.streamerId(), fixture.highlightId(), clipFile(), thumbnailFile());

        var second = uploadService.upload(
                fixture.streamerId(), fixture.highlightId(), clipFile(), thumbnailFile());

        assertThat(second.clipUrl()).isEqualTo(first.clipUrl());
    }

    @Test
    void 업로드된_하이라이트는_작업_목록에서_빠진다() {
        var fixture = pendingHighlight();
        uploadService.upload(fixture.streamerId(), fixture.highlightId(), clipFile(), thumbnailFile());

        assertThat(jobService.findPending(fixture.streamerId())).isEmpty();
    }

    @Test
    void 실패가_상한에_닿으면_FAILED로_확정된다() {
        var fixture = pendingHighlight();

        uploadService.fail(fixture.streamerId(), fixture.highlightId(), "ffmpeg exit 1");
        assertThat(clipStatusOf(fixture.highlightId())).isEqualTo("PENDING");
        uploadService.fail(fixture.streamerId(), fixture.highlightId(), "ffmpeg exit 1");
        uploadService.fail(fixture.streamerId(), fixture.highlightId(), "ffmpeg exit 1");

        assertThat(clipStatusOf(fixture.highlightId())).isEqualTo("FAILED");
        assertThat(jobService.findPending(fixture.streamerId())).isEmpty();
    }

    @Test
    void 되돌릴_수_없는_실패는_횟수를_쓰지_않고_SKIPPED가_된다() {
        var fixture = pendingHighlight();

        uploadService.skip(fixture.streamerId(), fixture.highlightId(), "recording file not found");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT clip_status, clip_attempts FROM highlights WHERE id = ?", fixture.highlightId());
        assertThat(row.get("clip_status")).isEqualTo("SKIPPED");
        assertThat(((Number) row.get("clip_attempts")).intValue()).isZero();
    }

    @Test
    void 남의_하이라이트에는_업로드할_수_없다() {
        var fixture = pendingHighlight();
        long stranger = createStreamer();

        assertThatThrownBy(() -> uploadService.upload(
                stranger, fixture.highlightId(), clipFile(), thumbnailFile()))
                .isInstanceOf(MediaException.class)
                .hasMessageContaining("하이라이트를 찾을 수 없습니다");
    }

    @Test
    void mp4가_아닌_파일은_거부한다() {
        var fixture = pendingHighlight();
        var notVideo = new MockMultipartFile("clip", "clip.mp4", "text/plain", "nope".getBytes());

        assertThatThrownBy(() -> uploadService.upload(
                fixture.streamerId(), fixture.highlightId(), notVideo, thumbnailFile()))
                .isInstanceOf(MediaException.class);
    }

    // --- Fixture ---

    private record Fixture(long streamerId, long sessionId, long highlightId) {
    }

    /** 녹화까지 끝나 클립만 기다리는 하이라이트 하나를 만든다. */
    private Fixture pendingHighlight() {
        var session = createSession();
        long streamerId = streamerOf(session.sessionId());
        Instant base = session.startedAt();

        recordingService.start(streamerId, session.sessionId(), base, base, 0);
        recordingService.finish(streamerId, session.sessionId(), base.plusSeconds(7200),
                "C:\\Videos\\rec.mkv", RecordingStatus.COMPLETED);

        long triggerId = insertTrigger(session.sessionId(), base);
        long reportId = insertReport(session.sessionId());
        long highlightId = insertHighlight(reportId, triggerId);
        return new Fixture(streamerId, session.sessionId(), highlightId);
    }

    /** 방송 시작 +600초부터 60초짜리 구간. */
    private long insertTrigger(long sessionId, Instant base) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO triggers (session_id, trigger_type, start_ms, end_ms, started_at, ended_at,
                                      peak_z, peak_engagement, avg_viewers, chat_count, unique_chatters)
                VALUES (?, 'CHAT_SURGE', 600000, 660000, ?, ?, 5.2, 0.31, 100, 40, 20)
                RETURNING id
                """, Long.class, sessionId, at(base.plusSeconds(600)), at(base.plusSeconds(660)));
    }

    /** JdbcTemplate은 Instant를 그대로 바인딩하지 못한다. */
    private OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private long insertReport(long sessionId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO reports (session_id, status, generated_at)
                VALUES (?, 'COMPLETED', now())
                RETURNING id
                """, Long.class, sessionId);
    }

    private long insertHighlight(long reportId, long triggerId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO highlights (report_id, trigger_id, start_ms, end_ms)
                VALUES (?, ?, 600000, 660000)
                RETURNING id
                """, Long.class, reportId, triggerId);
    }

    private String clipStatusOf(long highlightId) {
        return jdbcTemplate.queryForObject(
                "SELECT clip_status FROM highlights WHERE id = ?", String.class, highlightId);
    }

    private MockMultipartFile clipFile() {
        return new MockMultipartFile("clip", "clip.mp4", "video/mp4", "fake-video".getBytes());
    }

    private MockMultipartFile thumbnailFile() {
        return new MockMultipartFile("thumbnail", "thumb.jpg", "image/jpeg", "fake-image".getBytes());
    }

    private long streamerOf(long sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT streamer_id FROM broadcast_sessions WHERE id = ?", Long.class, sessionId);
    }

    private StartBroadcastSessionResult createSession() {
        long streamerId = createStreamer();
        long platformAccountId = createPlatformAccount(streamerId);
        return transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(platformAccountId, null, "클립 테스트 방송", null, "ko")
        );
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(null, "클립 테스트 " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private long createPlatformAccount(long streamerId) {
        Instant now = Instant.now();
        return platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "clip-test-" + UUID.randomUUID(),
                "테스트 채널",
                "encrypted-access",
                "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "user:read"
        )).orElseThrow();
    }
}
