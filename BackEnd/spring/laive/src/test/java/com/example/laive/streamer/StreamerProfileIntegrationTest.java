package com.example.laive.streamer;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.common.api.PatchValue;
import com.example.laive.streamer.domain.StreamerProfile;
import com.example.laive.streamer.domain.StreamerTag;
import com.example.laive.streamer.domain.UpdateStreamerProfileCommand;
import com.example.laive.streamer.exception.StreamerProfileException;
import com.example.laive.streamer.service.StreamerProfileCommandService;
import com.example.laive.streamer.service.StreamerProfileQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로필 API(명세 6.6)와 태그 카탈로그가 실제 PostgreSQL에서 동작하는지 검증한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=",
        "laive.internal.service-token=test-internal-service-token-32bytes!",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class StreamerProfileIntegrationTest {

    @Autowired
    private StreamerProfileQueryService queryService;

    @Autowired
    private StreamerProfileCommandService commandService;

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------- 조회

    @Test
    void 프로필은_로그인수단과_태그를_함께_담는다() {
        long streamerId = createStreamer();
        jdbcTemplate.update(
                "UPDATE streamers SET email = ?, password_hash = 'hash', bio = 'FPS 위주 방송' WHERE id = ?",
                "profile-" + streamerId + "@example.com", streamerId);
        jdbcTemplate.update(
                "INSERT INTO social_accounts (streamer_id, provider, provider_user_id) VALUES (?, 'NAVER', ?)",
                streamerId, "naver-" + UUID.randomUUID());
        commandService.update(streamerId, new UpdateStreamerProfileCommand(
                null, null, null, PatchValue.of(tagIds("FPS", "저챗"))));

        StreamerProfile profile = queryService.getProfile(streamerId);

        assertThat(profile.hasPassword()).isTrue();
        assertThat(profile.loginMethods()).containsExactly("EMAIL", "NAVER");
        assertThat(profile.bio()).isEqualTo("FPS 위주 방송");
        assertThat(profile.tags()).extracting(StreamerTag::name)
                .containsExactly("FPS", "저챗");                    // 고른 순서 = 노출 순서
    }

    @Test
    void 소셜_전용_계정은_EMAIL이_로그인수단에_없다() {
        long streamerId = createStreamer();

        StreamerProfile profile = queryService.getProfile(streamerId);

        assertThat(profile.hasPassword()).isFalse();
        assertThat(profile.loginMethods()).isEmpty();
        assertThat(profile.tags()).isEmpty();
    }

    @Test
    void 태그_카탈로그는_시드가_채워져_있다() {
        List<StreamerTag> catalog = queryService.getTagCatalog();

        assertThat(catalog).extracting(StreamerTag::name)
                .contains("FPS", "저챗", "공포게임", "하이텐션");
        assertThat(catalog).extracting(StreamerTag::tagType)
                .contains("CONTENT", "GAME", "GENRE", "STYLE");
    }

    // ---------------------------------------------------------------- 수정

    @Test
    void 보낸_필드만_바뀌고_명시적_null은_비운다() {
        long streamerId = createStreamer();
        jdbcTemplate.update("UPDATE streamers SET bio = '지울 소개' WHERE id = ?", streamerId);

        StreamerProfile updated = commandService.update(streamerId, new UpdateStreamerProfileCommand(
                PatchValue.of("새 닉네임"),
                null,                                              // 미전송 → 유지
                PatchValue.of(null),                               // 명시적 null → 비움
                null));

        assertThat(updated.nickname()).isEqualTo("새 닉네임");
        assertThat(updated.bio()).isNull();
    }

    @Test
    void 태그는_전체_교체이고_순서가_보존된다() {
        long streamerId = createStreamer();
        commandService.update(streamerId, new UpdateStreamerProfileCommand(
                null, null, null, PatchValue.of(tagIds("FPS", "저챗"))));

        StreamerProfile replaced = commandService.update(streamerId, new UpdateStreamerProfileCommand(
                null, null, null, PatchValue.of(tagIds("하이텐션", "공포게임"))));

        assertThat(replaced.tags()).extracting(StreamerTag::name)
                .containsExactly("하이텐션", "공포게임");

        StreamerProfile cleared = commandService.update(streamerId, new UpdateStreamerProfileCommand(
                null, null, null, PatchValue.of(null)));           // null = 전부 비움
        assertThat(cleared.tags()).isEmpty();
    }

    @Test
    void 카탈로그에_없는_태그나_중복은_400이다() {
        long streamerId = createStreamer();
        Long fps = tagIds("FPS").get(0);

        assertCode(() -> commandService.update(streamerId, new UpdateStreamerProfileCommand(
                null, null, null, PatchValue.of(List.of(999_999L)))), "STREAMER_INVALID_TAG");
        assertCode(() -> commandService.update(streamerId, new UpdateStreamerProfileCommand(
                null, null, null, PatchValue.of(List.of(fps, fps)))), "STREAMER_INVALID_TAG");
    }

    @Test
    void 닉네임을_비우면_400이고_빈_요청도_400이다() {
        long streamerId = createStreamer();

        assertCode(() -> commandService.update(streamerId, new UpdateStreamerProfileCommand(
                PatchValue.of("   "), null, null, null)), "STREAMER_INVALID_FIELD");
        assertCode(() -> commandService.update(streamerId, new UpdateStreamerProfileCommand(
                null, null, null, null)), "STREAMER_EMPTY_PATCH");
    }

    // -------------------------------------------------------------- 픽스처

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(null, "profile " + UUID.randomUUID(), null);
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private List<Long> tagIds(String... names) {
        return List.of(names).stream()
                .map(name -> jdbcTemplate.queryForObject(
                        "SELECT id FROM tags WHERE name = ?", Long.class, name))
                .toList();
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(StreamerProfileException.class,
                        e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
