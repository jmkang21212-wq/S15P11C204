package com.example.laive.platform;

import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.platform.domain.PlatformAccount;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5 마이그레이션이 적용된 실제 스키마에서 UPSERT의 소유권 규칙과
 * record 컬럼 매핑을 확인한다.
 */
@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE="
})
@Transactional
class PlatformAccountIntegrationTest {

    @Autowired
    private PlatformAccountMapper platformAccountMapper;

    @Autowired
    private AuthMapper authMapper;

    @Test
    void createsThenUpdatesSameChannelForOwningStreamer() {
        long streamerId = createStreamer();
        String channelId = "channel-" + UUID.randomUUID();

        long created = platformAccountMapper.upsert(upsert(streamerId, channelId, "첫 채널명", "enc-access-1"))
                .orElseThrow();

        // 같은 스트리머의 재연동은 새 행을 만들지 않고 토큰과 채널명을 갱신한다.
        long reconnected = platformAccountMapper.upsert(upsert(streamerId, channelId, "바뀐 채널명", "enc-access-2"))
                .orElseThrow();
        assertThat(reconnected).isEqualTo(created);

        PlatformAccountSecret secret = platformAccountMapper.findSecretForUpdate(created).orElseThrow();
        assertThat(secret.platformAccountId()).isEqualTo(created);
        assertThat(secret.streamerId()).isEqualTo(streamerId);
        assertThat(secret.platform()).isEqualTo("CHZZK");
        assertThat(secret.channelId()).isEqualTo(channelId);
        assertThat(secret.accessToken()).isEqualTo("enc-access-2");
        assertThat(secret.refreshToken()).isEqualTo("enc-refresh");
        assertThat(secret.accessTokenExpiresAt()).isNotNull();
        assertThat(secret.refreshTokenExpiresAt()).isNotNull();
        assertThat(secret.connectionStatus()).isEqualTo("CONNECTED");
    }

    @Test
    void refusesChannelOwnedByAnotherStreamer() {
        long owner = createStreamer();
        long intruder = createStreamer();
        String channelId = "channel-" + UUID.randomUUID();
        platformAccountMapper.upsert(upsert(owner, channelId, "채널", "enc-access")).orElseThrow();

        Optional<Long> stolen = platformAccountMapper.upsert(upsert(intruder, channelId, "탈취", "enc-access"));

        // DO UPDATE의 WHERE가 거짓이라 아무 행도 반환되지 않아야 한다.
        assertThat(stolen).isEmpty();
        assertThat(platformAccountMapper.findByStreamerId(intruder)).isEmpty();
        assertThat(platformAccountMapper.findSecretForUpdate(
                platformAccountMapper.findByStreamerId(owner).getFirst().platformAccountId()
        ).orElseThrow().streamerId()).isEqualTo(owner);
    }

    @Test
    void mapsListColumnsInDeclaredOrder() {
        long streamerId = createStreamer();
        String channelId = "channel-" + UUID.randomUUID();
        long accountId = platformAccountMapper.upsert(upsert(streamerId, channelId, "한별TV", "enc-access"))
                .orElseThrow();

        PlatformAccount account = platformAccountMapper.findByStreamerId(streamerId).getFirst();

        assertThat(account.platformAccountId()).isEqualTo(accountId);
        assertThat(account.platform()).isEqualTo("CHZZK");
        assertThat(account.channelId()).isEqualTo(channelId);
        assertThat(account.channelName()).isEqualTo("한별TV");
        assertThat(account.connectionStatus()).isEqualTo("CONNECTED");
        assertThat(account.connectedAt()).isNotNull();
    }

    @Test
    void reportsReauthRequiredWhenRefreshTokenExpired() {
        long streamerId = createStreamer();
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        long accountId = platformAccountMapper.upsert(new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                "channel-" + UUID.randomUUID(),
                "만료된 연동",
                "enc-access",
                "enc-refresh",
                past,
                past,
                "유저 조회"
        )).orElseThrow();

        // 저장된 값은 아직 CONNECTED다. 토큰을 쓴 적이 없어 전환이 기록되지 않았기 때문이다.
        assertThat(platformAccountMapper.findSecretForUpdate(accountId).orElseThrow().connectionStatus())
                .isEqualTo("CONNECTED");
        // 목록은 사실대로 재연동 필요를 보여줘야 한다.
        assertThat(platformAccountMapper.findByStreamerId(streamerId).getFirst().connectionStatus())
                .isEqualTo("REAUTH_REQUIRED");
    }

    @Test
    void keepsConnectedStatusWhileRefreshTokenIsAlive() {
        long streamerId = createStreamer();
        platformAccountMapper.upsert(upsert(streamerId, "channel-" + UUID.randomUUID(), "정상 연동", "enc-access"))
                .orElseThrow();

        assertThat(platformAccountMapper.findByStreamerId(streamerId).getFirst().connectionStatus())
                .isEqualTo("CONNECTED");
    }

    @Test
    void updatesTokensAndConnectionStatus() {
        long streamerId = createStreamer();
        long accountId = platformAccountMapper
                .upsert(upsert(streamerId, "channel-" + UUID.randomUUID(), "채널", "enc-access"))
                .orElseThrow();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);

        assertThat(platformAccountMapper.updateConnectionStatus(accountId, "REAUTH_REQUIRED")).isEqualTo(1);
        assertThat(platformAccountMapper.findSecretForUpdate(accountId).orElseThrow().isReauthRequired())
                .isTrue();

        assertThat(platformAccountMapper.updateTokens(
                accountId,
                "enc-access-rotated",
                "enc-refresh-rotated",
                expiresAt,
                expiresAt.plus(29, ChronoUnit.DAYS),
                "유저 조회"
        )).isEqualTo(1);

        PlatformAccountSecret refreshed = platformAccountMapper.findSecretForUpdate(accountId).orElseThrow();
        assertThat(refreshed.accessToken()).isEqualTo("enc-access-rotated");
        assertThat(refreshed.refreshToken()).isEqualTo("enc-refresh-rotated");
        assertThat(refreshed.accessTokenExpiresAt()).isEqualTo(expiresAt);
        // 갱신에 성공하면 연동 상태가 정상으로 돌아와야 한다.
        assertThat(refreshed.connectionStatus()).isEqualTo("CONNECTED");
    }

    @Test
    void deletesOnlyOwnAccount() {
        long owner = createStreamer();
        long other = createStreamer();
        long accountId = platformAccountMapper
                .upsert(upsert(owner, "channel-" + UUID.randomUUID(), "채널", "enc-access"))
                .orElseThrow();

        assertThat(platformAccountMapper.delete(accountId, other)).isZero();
        assertThat(platformAccountMapper.findSecretByIdAndStreamerId(accountId, other)).isEmpty();
        assertThat(platformAccountMapper.delete(accountId, owner)).isEqualTo(1);
        assertThat(platformAccountMapper.findByStreamerId(owner)).isEmpty();
    }

    private long createStreamer() {
        StreamerInsert insert = new StreamerInsert(null, "연동 통합 테스트", null);
        authMapper.insertStreamer(insert);
        return insert.getId();
    }

    private PlatformAccountUpsert upsert(
            long streamerId,
            String channelId,
            String channelName,
            String encryptedAccessToken
    ) {
        Instant now = Instant.now();
        return new PlatformAccountUpsert(
                streamerId,
                "CHZZK",
                channelId,
                channelName,
                encryptedAccessToken,
                "enc-refresh",
                now.plus(1, ChronoUnit.DAYS),
                now.plus(30, ChronoUnit.DAYS),
                "유저 조회"
        );
    }
}
