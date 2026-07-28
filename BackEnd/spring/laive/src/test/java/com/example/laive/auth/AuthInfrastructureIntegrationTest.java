package com.example.laive.auth;

import com.example.laive.auth.domain.RefreshTokenRecord;
import com.example.laive.auth.domain.Streamer;
import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.persistence.AuthMapper;
import com.example.laive.auth.service.OAuthStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "laive.auth.jwt.secret=test-only-secret-key-with-at-least-32-bytes",
        "laive.platform.token-encryption-key=dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE="
})
@Transactional
class AuthInfrastructureIntegrationTest {

    @Autowired
    private AuthMapper authMapper;

    @Autowired
    private OAuthStateService stateService;

    @Test
    void persistsSocialAccountAndLocksRefreshTokenUsingExistingSchema() {
        String providerUserId = "integration-" + UUID.randomUUID();
        authMapper.lockIdentity("social:NAVER:" + providerUserId);
        StreamerInsert insert = new StreamerInsert(null, "통합 테스트 사용자", null);
        authMapper.insertStreamer(insert);
        authMapper.insertSocialAccount(
                insert.getId(),
                "NAVER",
                providerUserId,
                "integration@example.com"
        );

        Streamer found = authMapper.findStreamerBySocialAccount("NAVER", providerUserId)
                .orElseThrow();
        assertThat(found.streamerId()).isEqualTo(insert.getId());
        assertThat(found.email()).isNull();
        assertThat(found.nickname()).isEqualTo("통합 테스트 사용자");

        Instant expiresAt = Instant.now().plusSeconds(60);
        String tokenHash = UUID.randomUUID().toString().replace("-", "");
        authMapper.insertRefreshToken(insert.getId(), tokenHash, expiresAt);
        RefreshTokenRecord token = authMapper.findRefreshTokenForUpdate(tokenHash).orElseThrow();

        assertThat(token.streamerId()).isEqualTo(insert.getId());
        assertThat(token.revokedAt()).isNull();
        assertThat(authMapper.revokeRefreshToken(token.id(), Instant.now())).isEqualTo(1);
    }

    @Test
    void storesAndConsumesOAuthStateInRedis() {
        String returnUri = "http://localhost:3000/oauth/callback";
        String state = stateService.create(returnUri);

        assertThat(stateService.consume(state)).isEqualTo(returnUri);
        assertThatThrownBy(() -> stateService.consume(state))
                .hasMessage("인증 정보가 유효하지 않습니다.");
    }
}
