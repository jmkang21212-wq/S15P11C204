package com.example.laive.broadcast.fandom;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.broadcast.collect.domain.ChatMessageItem;
import com.example.laive.broadcast.collect.domain.ChatMessageType;
import com.example.laive.broadcast.collect.service.CollectionIngestService;
import com.example.laive.broadcast.fandom.domain.TopViewer;
import com.example.laive.broadcast.fandom.repository.mybatis.ViewerScoreMapper;
import com.example.laive.broadcast.live.dto.response.LiveSnapshotResponse;
import com.example.laive.broadcast.live.service.LiveSnapshotService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 열성 시청자 스코어링(FR-F-03)이 실제 PostgreSQL에서 도배를 걸러내는지 검증한다.
 *
 * <p>채팅은 실제 수집 경로({@link CollectionIngestService})로 넣는다 — 프로필 upsert와
 * viewer_id 해석까지 같이 검증되도록.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class FandomScoreIntegrationTest {

    /** 분(minute) 경계 계산이 흔들리지 않게 고정 시각을 쓴다. */
    private static final Instant BASE = Instant.parse("2026-08-01T12:00:00Z");

    @Autowired
    private CollectionIngestService ingestService;

    @Autowired
    private BroadcastSessionTransactionService transactionService;

    @Autowired
    private ViewerScoreMapper viewerScoreMapper;

    @Autowired
    private LiveSnapshotService liveSnapshotService;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ------------------------------------------------------------ 도배 방지

    @Test
    void 한_분에_백_개를_쳐도_분당_캡_때문에_점수는_삼점이다() {
        Fixture f = createFixture();
        String spammer = hash();

        List<ChatMessageItem> burst = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            burst.add(chat(spammer, "도배" + i, BASE.plusMillis(i * 500L % 59_000)));
        }
        ingestService.appendChatMessages(f.sessionId, burst);

        TopViewer viewer = topOf(f, spammer);
        assertThat(viewer.chatScore()).isEqualTo(3);   // 분당 3개까지만 인정
        assertThat(viewer.watchScore()).isEqualTo(1);  // 참여한 분은 1개
        assertThat(viewer.totalScore()).isEqualTo(5.0); // 3 + 1×2
    }

    @Test
    void 꾸준한_시청자가_도배러를_이긴다() {
        Fixture f = createFixture();
        String spammer = hash();
        String loyal = hash();

        List<ChatMessageItem> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {                       // 도배: 1분에 100개
            items.add(chat(spammer, "도배" + i, BASE.plusMillis(i * 500L % 59_000)));
        }
        for (int minute = 0; minute < 40; minute++) {         // 열성: 40분에 걸쳐 1개씩
            items.add(chat(loyal, "응원 " + minute, BASE.plus(minute, ChronoUnit.MINUTES)));
        }
        ingestService.appendChatMessages(f.sessionId, items);

        List<TopViewer> top = viewerScoreMapper.findTopViewers(f.sessionId, 5);
        assertThat(top.get(0).viewerId()).isEqualTo(viewerIdOf(loyal));
        assertThat(top.get(0).totalScore()).isEqualTo(120.0); // 40 + 40×2
        assertThat(top.get(1).totalScore()).isEqualTo(5.0);
    }

    @Test
    void 후원은_로그_스케일로_가산된다() {
        Fixture f = createFixture();
        String donor = hash();

        // 일반 수집 경로가 아니라 플랫폼 경로만 amount를 저장하므로 직접 넣는다
        ingestService.appendChatMessages(f.sessionId,
                List.of(chat(donor, "후원 인사", BASE)));
        jdbcTemplate.update(
                """
                UPDATE chat_messages
                   SET message_type = 'DONATION', amount = 9999
                 WHERE session_id = ? AND viewer_id = ?
                """,
                f.sessionId, viewerIdOf(donor));

        // log10(9999+1)=4 → 후원 40점 + 채팅 1 + 참여 1분×2 = 43
        assertThat(topOf(f, donor).totalScore()).isEqualTo(43.0);
    }

    // -------------------------------------------------------- 노출 규칙

    @Test
    void 스트리머_본인과_매니저는_노출에서_빠진다() {
        Fixture f = createFixture();
        String streamer = hash();
        String manager = hash();
        String fan = hash();

        ingestService.appendChatMessages(f.sessionId, List.of(
                chat(streamer, "방장 채팅", BASE, "한별TV", "streamer"),
                chat(manager, "매니저 채팅", BASE, "매니저", "manager"),
                chat(fan, "팬 채팅", BASE, "한별팬1호", "common_user")
        ));

        List<TopViewer> top = viewerScoreMapper.findTopViewers(f.sessionId, 5);
        assertThat(top).extracting(TopViewer::viewerId)
                .containsExactly(viewerIdOf(fan));
    }

    @Test
    void 스냅샷은_닉네임이_없으면_해시_별칭으로_폴백한다() {
        Fixture f = createFixture();
        // 닉네임 없이 채팅만 온 시청자 (구버전 수집기 시나리오)
        ingestService.appendChatMessages(f.sessionId,
                List.of(new ChatMessageItem("sha256:a91cffee0011", "익명 채팅", BASE)));

        LiveSnapshotResponse snapshot = liveSnapshotService.getSnapshot(f.streamerId, f.sessionId);

        assertThat(snapshot.topViewers()).singleElement().satisfies(viewer -> {
            assertThat(viewer.nickname()).isNull();
            assertThat(viewer.viewerAlias()).isEqualTo("viewer-a91c");
            assertThat(viewer.totalScore()).isEqualTo(3.0); // 1 + 1×2
        });
    }

    @Test
    void 상위_다섯_명만_총점_내림차순으로_나온다() {
        Fixture f = createFixture();
        List<ChatMessageItem> items = new ArrayList<>();
        for (int viewer = 0; viewer < 7; viewer++) {
            String hash = hash();
            for (int minute = 0; minute <= viewer; minute++) {  // 참여 분 수 = viewer+1
                items.add(chat(hash, "v" + viewer + "m" + minute,
                        BASE.plus(minute, ChronoUnit.MINUTES)));
            }
        }
        ingestService.appendChatMessages(f.sessionId, items);

        List<TopViewer> top = viewerScoreMapper.findTopViewers(f.sessionId, 5);
        assertThat(top).hasSize(5);
        assertThat(top).extracting(TopViewer::totalScore)
                .containsExactly(21.0, 18.0, 15.0, 12.0, 9.0); // (n + n×2), n=7..3
    }

    // -------------------------------------------------------- 종료 확정 저장

    @Test
    void 종료_확정_저장은_전_시청자를_남기고_재실행에_멱등하다() {
        Fixture f = createFixture();
        String fan = hash();
        String streamer = hash();
        ingestService.appendChatMessages(f.sessionId, List.of(
                chat(fan, "팬 채팅", BASE, "한별팬1호", "common_user"),
                chat(fan, "팬 채팅2", BASE.plus(1, ChronoUnit.MINUTES), "한별팬1호", "common_user"),
                chat(streamer, "방장 채팅", BASE, "한별TV", "streamer")
        ));

        viewerScoreMapper.persistViewerScores(f.sessionId);
        viewerScoreMapper.persistViewerScores(f.sessionId);  // 아웃박스 재시도 시나리오

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT viewer_id, streamer_id, chat_score, watch_score, total_score "
                        + "FROM viewer_scores WHERE session_id = ? ORDER BY total_score DESC",
                f.sessionId);

        // 노출과 달리 저장은 스트리머 본인도 남긴다 — 기록은 완전하게, 필터는 조회에서
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("chat_score", 2)
                .containsEntry("watch_score", 2)
                .containsEntry("streamer_id", f.streamerId);
        assertThat(((Number) rows.get(0).get("total_score")).doubleValue()).isEqualTo(6.0);
    }

    // -------------------------------------------------------------- 픽스처

    private record Fixture(long streamerId, long sessionId) {
    }

    private Fixture createFixture() {
        StreamerInsert insert = new StreamerInsert(null, "fandom " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        long streamerId = insert.getId();

        Instant now = Instant.now();
        long platformAccountId = platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId, "CHZZK", "fandom-test-" + UUID.randomUUID(), "테스트 채널",
                "encrypted-access", "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), "user:read"
        )).orElseThrow();

        long sessionId = transactionService.start(
                streamerId,
                new StartBroadcastSessionCommand(
                        platformAccountId, null, "팬덤 테스트 방송", null, "ko")
        ).sessionId();
        return new Fixture(streamerId, sessionId);
    }

    private static String hash() {
        return "sha256:" + UUID.randomUUID();
    }

    private static ChatMessageItem chat(String hash, String message, Instant sentAt) {
        return chat(hash, message, sentAt, "시청자닉", "common_user");
    }

    private static ChatMessageItem chat(
            String hash, String message, Instant sentAt, String nickname, String role) {
        return new ChatMessageItem(hash, message, sentAt, null, null,
                ChatMessageType.TEXT, null, null, null, nickname, role);
    }

    private long viewerIdOf(String hash) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM viewer_profiles WHERE viewer_hash = ?", Long.class, hash);
    }

    private TopViewer topOf(Fixture f, String hash) {
        long viewerId = viewerIdOf(hash);
        return viewerScoreMapper.findTopViewers(f.sessionId, 50).stream()
                .filter(viewer -> viewer.viewerId() == viewerId)
                .findFirst()
                .orElseThrow();
    }
}
