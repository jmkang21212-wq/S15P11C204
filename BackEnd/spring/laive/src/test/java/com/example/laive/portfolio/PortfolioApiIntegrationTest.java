package com.example.laive.portfolio;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.common.api.PageResponse;
import com.example.laive.common.api.PatchValue;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.portfolio.domain.CreatePortfolioCommand;
import com.example.laive.portfolio.domain.FeaturedHighlightSelection;
import com.example.laive.portfolio.domain.PortfolioDetail;
import com.example.laive.portfolio.domain.PortfolioSearchCondition;
import com.example.laive.portfolio.domain.PortfolioSearchItem;
import com.example.laive.portfolio.domain.PortfolioSort;
import com.example.laive.portfolio.domain.UpdatePortfolioCommand;
import com.example.laive.portfolio.exception.PortfolioException;
import com.example.laive.portfolio.service.PortfolioCommandService;
import com.example.laive.portfolio.service.PortfolioPerformanceService;
import com.example.laive.portfolio.service.PortfolioQueryService;
import com.example.laive.streamer.domain.StreamerTag;
import com.example.laive.streamer.domain.UpdateStreamerProfileCommand;
import com.example.laive.streamer.service.StreamerProfileCommandService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 포트폴리오 API(명세 12)가 실제 PostgreSQL에서 동작하는지 검증한다.
 *
 * <p>핵심 규칙 셋을 모두 실제 경로로 밟는다 — 콜드스타트 게이트(종료 방송 1회),
 * 사용자 직접 설정 생성(자동 구성 없음), 프로필 연동(태그·프로필은 조인).
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.internal.service-token=test-internal-service-token-32bytes!",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class PortfolioApiIntegrationTest {

    /** 21:00 KST 시작. 최근 28일·90일 창에 들어오도록 상대 시각을 쓴다. */
    private static final Instant RECENT_START =
            Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.DAYS)
                    .plus(Duration.ofHours(12));                    // 12:00Z = 21:00 KST
    private static final Instant RECENT_END = RECENT_START.plus(Duration.ofHours(4)); // 01:00 KST

    @Autowired
    private PortfolioQueryService queryService;

    @Autowired
    private PortfolioCommandService commandService;

    @Autowired
    private PortfolioPerformanceService performanceService;

    @Autowired
    private StreamerProfileCommandService profileCommandService;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // -------------------------------------------------------- 콜드스타트 게이트

    @Test
    void 방송_이력이_없으면_조회도_생성도_NOT_ELIGIBLE이다() {
        Fixture f = createFixture();

        assertCode(() -> queryService.getMyPortfolio(f.streamerId), "PORTFOLIO_NOT_ELIGIBLE");
        assertCode(() -> commandService.create(f.streamerId, createCommand(f, List.of())),
                "PORTFOLIO_NOT_ELIGIBLE");
        assertCode(() -> commandService.update(f.streamerId,
                        patch(b -> b.title = PatchValue.of("새 제목"))),
                "PORTFOLIO_NOT_ELIGIBLE");
    }

    /** 진행 중(LIVE) 방송은 "진행한" 방송이 아니다 — 종료돼야 자격이 생긴다. */
    @Test
    void LIVE_세션만_있어도_아직_NOT_ELIGIBLE이다() {
        Fixture f = createFixture();
        createSession(f, "LIVE", RECENT_START, null);

        assertCode(() -> queryService.getMyPortfolio(f.streamerId), "PORTFOLIO_NOT_ELIGIBLE");
    }

    @Test
    void 방송을_마치면_NOT_CREATED가_되고_생성하면_조회된다() {
        Fixture f = createFixture();
        createEndedSession(f);

        assertCode(() -> queryService.getMyPortfolio(f.streamerId), "PORTFOLIO_NOT_CREATED");

        PortfolioDetail created = commandService.create(f.streamerId, createCommand(f, List.of()));

        // 슬러그는 내부 id를 노출하지 않는 무작위 10자다
        assertThat(created.portfolio().publicSlug()).matches("^[a-z0-9]{10}$");
        assertThat(created.portfolio().title()).isEqualTo("한별 TV");
        assertThat(created.portfolio().isPublic()).isFalse();
        assertThat(created.portfolio().publishedAt()).isNull();
        assertThat(queryService.getMyPortfolio(f.streamerId).portfolio().portfolioId())
                .isEqualTo(created.portfolio().portfolioId());
    }

    @Test
    void 이미_있으면_생성은_409다() {
        Fixture f = createFixture();
        createEndedSession(f);
        commandService.create(f.streamerId, createCommand(f, List.of()));

        assertCode(() -> commandService.create(f.streamerId, createCommand(f, List.of())),
                "PORTFOLIO_ALREADY_EXISTS");
    }

    // ------------------------------------------------- 생성 = 사용자 직접 설정

    @Test
    void 생성_필수값이_빠지면_400이다() {
        List<Long> tags = tagIds("FPS");
        assertCode(() -> new CreatePortfolioCommand(
                null, null, null, null, false, tags, List.of()), "PORTFOLIO_INVALID_FIELD");
        assertCode(() -> new CreatePortfolioCommand(
                "제목", null, null, null, null, tags, List.of()), "PORTFOLIO_INVALID_FIELD");
        assertCode(() -> new CreatePortfolioCommand(
                "제목", null, null, null, false, List.of(), List.of()), "PORTFOLIO_INVALID_FIELD");
        assertCode(() -> new CreatePortfolioCommand(
                "제목", null, null, null, false, tags, null), "PORTFOLIO_INVALID_FIELD");
    }

    @Test
    void 공개로_생성하면_publishedAt이_즉시_기록된다() {
        Fixture f = createFixture();
        createEndedSession(f);

        PortfolioDetail created = commandService.create(f.streamerId, new CreatePortfolioCommand(
                "한별 TV", "소개", null, null, true, tagIds("FPS"), List.of()));

        assertThat(created.portfolio().isPublic()).isTrue();
        assertThat(created.portfolio().publishedAt()).isNotNull();
    }

    @Test
    void 생성은_고른_태그와_하이라이트를_그대로_반영한다() {
        Fixture f = createFixture();
        long sessionId = createEndedSession(f);
        long reportId = createReport(sessionId, 100, 300, 1_000);
        long h1 = createHighlight(reportId, "https://cdn.example.com/clips/1.mp4");
        long h2 = createHighlight(reportId, null);

        PortfolioDetail created = commandService.create(f.streamerId, new CreatePortfolioCommand(
                "한별 TV", "신작 위주로 방송합니다", "biz@example.com", "https://example.com",
                false, tagIds("FPS", "저챗"),
                List.of(new FeaturedHighlightSelection(h1, 1), new FeaturedHighlightSelection(h2, 2))));

        assertThat(created.tags()).extracting(StreamerTag::name)
                .containsExactly("FPS", "저챗");                      // 고른 순서 유지
        assertThat(created.featuredHighlights()).hasSize(2);
        // 요청은 id·순서만 보냈지만 응답은 클립 URL을 채워 돌려줘야 한다
        assertThat(created.featuredHighlights().get(0).clipUrl())
                .isEqualTo("https://cdn.example.com/clips/1.mp4");
        assertThat(created.featuredHighlights().get(1).clipUrl()).isNull();
    }

    @Test
    void 남의_하이라이트로_생성하면_400이다() {
        Fixture mine = createFixture();
        createEndedSession(mine);

        Fixture other = createFixture();
        long otherReport = createReport(createEndedSession(other), 10, 20, 30);
        long othersHighlight = createHighlight(otherReport, null);

        assertCode(() -> commandService.create(mine.streamerId, createCommand(mine,
                        List.of(new FeaturedHighlightSelection(othersHighlight, 1)))),
                "PORTFOLIO_HIGHLIGHT_INVALID");
    }

    // ------------------------------------------------------------ 실적 집계

    @Test
    void 실적은_종료_세션과_리포트에서_집계된다() {
        Fixture f = createFixture();
        createReport(createEndedSession(f), 100, 250, 1_000);
        createReport(createEndedSession(f), 200, 400, 2_500);
        createSession(f, "LIVE", Instant.now(), null);            // 진행 중은 집계 제외

        commandService.create(f.streamerId, createCommand(f, List.of()));
        performanceService.refresh(f.streamerId);
        PortfolioDetail detail = queryService.getMyPortfolio(f.streamerId);

        assertThat(detail.performance().avgViewers()).isEqualTo(150);
        assertThat(detail.performance().peakViewers()).isEqualTo(400);
        assertThat(detail.performance().totalChat()).isEqualTo(3_500);
        assertThat(detail.performance().totalBroadcastCount()).isEqualTo(2);
        // 최근 28일 종료 2회 ÷ 4주 = 0.5 → 반올림 1
        assertThat(detail.performance().broadcastsPerWeek()).isEqualTo(1);
    }

    @Test
    void 주_방송_시간대는_최근_방송의_최빈_시각이다() {
        Fixture f = createFixture();
        createEndedSession(f);                                     // 21:00~01:00 KST
        createEndedSession(f);
        commandService.create(f.streamerId, createCommand(f, List.of()));

        assertThat(queryService.getMyPortfolio(f.streamerId)
                .performance().primaryTimeRange()).isEqualTo("21:00-01:00");
    }

    /** 90일보다 오래된 방송뿐이면 표시할 시간대가 없다 — 자격은 유지되지만 "-"다. */
    @Test
    void 최근_90일_방송이_없으면_시간대는_대시다() {
        Fixture f = createFixture();
        Instant old = Instant.now().minus(Duration.ofDays(120));
        createSession(f, "ENDED", old, old.plus(Duration.ofHours(2)));
        commandService.create(f.streamerId, createCommand(f, List.of()));

        assertThat(queryService.getMyPortfolio(f.streamerId)
                .performance().primaryTimeRange()).isEqualTo("-");
    }

    // ---------------------------------------------------------------- 수정

    @Test
    void 수정은_보낸_필드만_바꾼다() {
        Fixture f = createFixture();
        createEndedSession(f);
        commandService.create(f.streamerId, new CreatePortfolioCommand(
                "원래 제목", "원래 소개", "old@example.com", null, false, tagIds("FPS"), List.of()));

        PortfolioDetail updated = commandService.update(f.streamerId, patch(b -> {
            b.title = PatchValue.of("새 제목");
            b.contactEmail = PatchValue.of(null);                  // 명시적 null = 비우기
        }));

        assertThat(updated.portfolio().title()).isEqualTo("새 제목");
        assertThat(updated.portfolio().contactEmail()).isNull();
        assertThat(updated.portfolio().introduction()).isEqualTo("원래 소개"); // 미전송 유지
    }

    @Test
    void 공개_전환은_publishedAt을_채우고_비공개_전환해도_보존한다() {
        Fixture f = createFixture();
        createEndedSession(f);
        commandService.create(f.streamerId, createCommand(f, List.of()));

        PortfolioDetail published = commandService.update(f.streamerId,
                patch(b -> b.isPublic = PatchValue.of(true)));
        Instant publishedAt = published.portfolio().publishedAt();
        assertThat(publishedAt).isNotNull();

        PortfolioDetail unpublished = commandService.update(f.streamerId,
                patch(b -> b.isPublic = PatchValue.of(false)));
        assertThat(unpublished.portfolio().isPublic()).isFalse();
        assertThat(unpublished.portfolio().publishedAt()).isEqualTo(publishedAt);
    }

    /** 순서 맞바꿈이 한 트랜잭션에서 성공해야 한다 — DEFERRABLE UNIQUE 검증. */
    @Test
    void 하이라이트_순서_맞바꿈이_한_번의_수정으로_된다() {
        Fixture f = createFixture();
        long reportId = createReport(createEndedSession(f), 100, 200, 500);
        long h1 = createHighlight(reportId, null);
        long h2 = createHighlight(reportId, null);
        commandService.create(f.streamerId, createCommand(f, List.of(
                new FeaturedHighlightSelection(h1, 1), new FeaturedHighlightSelection(h2, 2))));

        PortfolioDetail swapped = commandService.update(f.streamerId,
                patch(b -> b.featuredHighlights = PatchValue.of(List.of(
                        new FeaturedHighlightSelection(h1, 2),
                        new FeaturedHighlightSelection(h2, 1)))));

        assertThat(swapped.featuredHighlights())
                .extracting(h -> h.highlightId())
                .containsExactly(h2, h1);                          // display_order 순
    }

    @Test
    void 하이라이트_중복_지정은_400이다() {
        Fixture f = createFixture();
        long reportId = createReport(createEndedSession(f), 100, 200, 500);
        long h1 = createHighlight(reportId, null);
        commandService.create(f.streamerId, createCommand(f, List.of()));

        assertCode(() -> commandService.update(f.streamerId,
                        patch(b -> b.featuredHighlights = PatchValue.of(List.of(
                                new FeaturedHighlightSelection(h1, 1),
                                new FeaturedHighlightSelection(h1, 2))))),
                "PORTFOLIO_HIGHLIGHT_INVALID");
    }

    @Test
    void 빈_수정_요청은_400이다() {
        Fixture f = createFixture();
        createEndedSession(f);
        commandService.create(f.streamerId, createCommand(f, List.of()));

        assertCode(() -> commandService.update(f.streamerId, patch(b -> { })),
                "PORTFOLIO_EMPTY_PATCH");
    }

    // -------------------------------------------------------- 검색·공개 조회

    @Test
    void 검색은_공개만_노출하고_로그인_시_본인을_뺀다() {
        Fixture me = publicPortfolio(300);
        Fixture visible = publicPortfolio(100);
        Fixture hidden = createFixture();                          // 비공개
        createEndedSession(hidden);
        commandService.create(hidden.streamerId, createCommand(hidden, List.of()));

        PageResponse<PortfolioSearchItem> mine = queryService.search(
                condition(null, null, null), me.streamerId, 0, 20);
        assertThat(mine.items()).extracting(PortfolioSearchItem::portfolioId)
                .doesNotContain(portfolioIdOf(me), portfolioIdOf(hidden))
                .contains(portfolioIdOf(visible));

        // 익명(공개 디렉터리)은 본인 제외 없이 전체 공개 행이다
        PageResponse<PortfolioSearchItem> anonymous = queryService.search(
                condition(null, null, null), null, 0, 20);
        assertThat(anonymous.items()).extracting(PortfolioSearchItem::portfolioId)
                .contains(portfolioIdOf(me), portfolioIdOf(visible));
    }

    @Test
    void 검색은_채널명과_태그명_키워드로_거른다() {
        Fixture f = publicPortfolio(100);                          // 채널명 "한별채널-{uuid}"

        PageResponse<PortfolioSearchItem> byChannel = queryService.search(
                condition("한별채널", null, null), null, 0, 20);
        assertThat(byChannel.items()).extracting(PortfolioSearchItem::portfolioId)
                .contains(portfolioIdOf(f));

        PageResponse<PortfolioSearchItem> byTagKeyword = queryService.search(
                condition("FPS", null, null), null, 0, 20);
        assertThat(byTagKeyword.items()).extracting(PortfolioSearchItem::portfolioId)
                .contains(portfolioIdOf(f));

        assertThat(queryService.search(condition("존재하지않는키워드", null, null), null, 0, 20)
                .totalElements()).isZero();
    }

    @Test
    void 검색은_태그_정확일치와_시청자_범위로_거른다() {
        Fixture small = publicPortfolio(50);
        Fixture big = publicPortfolio(500);

        PageResponse<PortfolioSearchItem> byTag = queryService.search(
                condition(null, null, "FPS"), null, 0, 20);
        assertThat(byTag.items()).extracting(PortfolioSearchItem::portfolioId)
                .contains(portfolioIdOf(small), portfolioIdOf(big));

        PageResponse<PortfolioSearchItem> byRange = queryService.search(
                new PortfolioSearchCondition(null, null, null, 100, 1000, PortfolioSort.RECENT),
                null, 0, 20);
        assertThat(byRange.items()).extracting(PortfolioSearchItem::portfolioId)
                .contains(portfolioIdOf(big))
                .doesNotContain(portfolioIdOf(small));
    }

    @Test
    void 검색은_평균_시청자순으로_정렬하고_페이지를_나눈다() {
        Fixture small = publicPortfolio(10);
        Fixture mid = publicPortfolio(20);
        Fixture big = publicPortfolio(30);
        List<Long> ours = List.of(portfolioIdOf(small), portfolioIdOf(mid), portfolioIdOf(big));

        PageResponse<PortfolioSearchItem> page = queryService.search(
                new PortfolioSearchCondition(null, null, null, 10, 30, PortfolioSort.AVG_VIEWERS_DESC),
                null, 0, 2);

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).extracting(PortfolioSearchItem::avgViewers)
                .containsExactly(30, 20);
        assertThat(ours).contains(page.items().get(0).portfolioId());
    }

    @Test
    void 검색_결과에는_플랫폼_계정과_태그명이_실린다() {
        Fixture f = publicPortfolio(100);

        PortfolioSearchItem item = queryService.search(condition("한별채널", null, null), null, 0, 20)
                .items().stream()
                .filter(i -> i.portfolioId() == portfolioIdOf(f))
                .findFirst().orElseThrow();

        assertThat(item.platformAccounts()).singleElement()
                .satisfies(a -> assertThat(a.platform()).isEqualTo("CHZZK"));
        assertThat(item.categoryTags()).containsExactly("FPS");    // 문자열 배열 (12.3)
        assertThat(item.publicSlug()).isEqualTo(slugOf(f));
    }

    @Test
    void 슬러그_공개_조회는_비공개와_미존재를_같은_404로_답한다() {
        Fixture open = publicPortfolio(100);
        Fixture closed = createFixture();
        createEndedSession(closed);
        commandService.create(closed.streamerId, createCommand(closed, List.of()));

        assertThat(queryService.getPublicPortfolio(slugOf(open))
                .portfolio().streamerId()).isEqualTo(open.streamerId);

        assertCode(() -> queryService.getPublicPortfolio(slugOf(closed)),
                "PORTFOLIO_NOT_FOUND");
        assertCode(() -> queryService.getPublicPortfolio("no-such-slug"),
                "PORTFOLIO_NOT_FOUND");
    }

    // ------------------------------------------------------- 프로필과의 연동

    /** 프로필(6.6)에서 태그를 바꾸면 포트폴리오 조회·태그 검색에 그대로 반영돼야 한다. */
    @Test
    void 프로필_태그_수정이_포트폴리오와_검색에_이어진다() {
        Fixture f = publicPortfolio(100);                          // 태그 FPS로 시작

        profileCommandService.update(f.streamerId, new UpdateStreamerProfileCommand(
                null, null, null, PatchValue.of(tagIds("공포게임", "하이텐션"))));

        assertThat(queryService.getMyPortfolio(f.streamerId).tags())
                .extracting(StreamerTag::name)
                .containsExactly("공포게임", "하이텐션");

        assertThat(queryService.search(condition(null, null, "공포게임"), null, 0, 20).items())
                .extracting(PortfolioSearchItem::portfolioId)
                .contains(portfolioIdOf(f));
        assertThat(queryService.search(condition(null, null, "FPS"), null, 0, 20).items())
                .extracting(PortfolioSearchItem::portfolioId)
                .doesNotContain(portfolioIdOf(f));
    }

    // -------------------------------------------------------------- 픽스처

    private record Fixture(long streamerId, long platformAccountId) {
    }

    /** 수정 명령을 필드 몇 개만 채워 만들기 위한 가변 빌더다. */
    private static final class PatchBuilder {
        PatchValue<String> title;
        PatchValue<String> introduction;
        PatchValue<String> contactEmail;
        PatchValue<String> contactUrl;
        PatchValue<Boolean> isPublic;
        PatchValue<List<FeaturedHighlightSelection>> featuredHighlights;
    }

    private static UpdatePortfolioCommand patch(java.util.function.Consumer<PatchBuilder> customizer) {
        PatchBuilder b = new PatchBuilder();
        customizer.accept(b);
        return new UpdatePortfolioCommand(
                b.title, b.introduction, b.contactEmail, b.contactUrl, b.isPublic, b.featuredHighlights);
    }

    private static PortfolioSearchCondition condition(String keyword, String platform, String tag) {
        return new PortfolioSearchCondition(keyword, platform, tag, null, null, PortfolioSort.RECENT);
    }

    private CreatePortfolioCommand createCommand(Fixture f, List<FeaturedHighlightSelection> highlights) {
        return new CreatePortfolioCommand(
                "한별 TV", "소개글", null, null, false, tagIds("FPS"), highlights);
    }

    /** 공개 포트폴리오 + 지정한 평균 시청자 실적을 가진 스트리머를 만든다. */
    private Fixture publicPortfolio(int avgViewers) {
        Fixture f = createFixture();
        createReport(createEndedSession(f), avgViewers, avgViewers * 2, 1_000);
        commandService.create(f.streamerId, new CreatePortfolioCommand(
                "한별 TV", "소개", null, null, true, tagIds("FPS"), List.of()));
        performanceService.refresh(f.streamerId);
        return f;
    }

    private long portfolioIdOf(Fixture f) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM portfolios WHERE streamer_id = ?", Long.class, f.streamerId);
    }

    private String slugOf(Fixture f) {
        return jdbcTemplate.queryForObject(
                "SELECT public_slug FROM portfolios WHERE streamer_id = ?", String.class, f.streamerId);
    }

    private List<Long> tagIds(String... names) {
        return List.of(names).stream()
                .map(name -> jdbcTemplate.queryForObject(
                        "SELECT id FROM tags WHERE name = ?", Long.class, name))
                .toList();
    }

    private long createEndedSession(Fixture f) {
        return createSession(f, "ENDED", RECENT_START, RECENT_END);
    }

    private long createSession(Fixture f, String status, Instant startedAt, Instant endedAt) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO broadcast_sessions
                    (streamer_id, platform_account_id, title, status, started_at, ended_at)
                VALUES (?, ?, '포트폴리오 테스트 방송', ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                f.streamerId, f.platformAccountId, status,
                java.sql.Timestamp.from(startedAt),
                endedAt == null ? null : java.sql.Timestamp.from(endedAt));
    }

    private long createReport(long sessionId, int avgViewers, int peakViewers, long totalChat) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO reports
                    (session_id, status, avg_viewers, peak_viewers, total_chat, generated_at)
                VALUES (?, 'COMPLETED', ?, ?, ?, now())
                RETURNING id
                """,
                Long.class, sessionId, avgViewers, peakViewers, totalChat);
    }

    /**
     * clip_url이 있으면 clip_status도 UPLOADED여야 한다(V16 ck_highlights_clip_url_status).
     * URL이 있다는 것은 업로드가 끝났다는 뜻이라 컬럼 DEFAULT인 PENDING과 공존할 수 없다.
     */
    private long createHighlight(long reportId, String clipUrl) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO highlights (report_id, start_ms, end_ms, clip_url, thumbnail_url, clip_status)
                VALUES (?, 1000, 11000, ?, NULL, ?)
                RETURNING id
                """,
                Long.class, reportId, clipUrl, clipUrl == null ? "PENDING" : "UPLOADED");
    }

    private Fixture createFixture() {
        StreamerInsert insert = new StreamerInsert(null, "pf " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        long streamerId = insert.getId();

        Instant now = Instant.now();
        long platformAccountId = platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId, "CHZZK", "pf-test-" + UUID.randomUUID(),
                "한별채널-" + UUID.randomUUID(),
                "encrypted-access", "encrypted-refresh",
                now.plus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), "user:read"
        )).orElseThrow();

        return new Fixture(streamerId, platformAccountId);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(PortfolioException.class,
                        e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
