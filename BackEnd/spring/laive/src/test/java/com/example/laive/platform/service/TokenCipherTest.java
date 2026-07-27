package com.example.laive.platform.service;

import com.example.laive.platform.config.PlatformProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCipherTest {

    private static final String KEY = "dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdCE=";
    private static final String OTHER_KEY = "b3RoZXIta2V5LTMyLWJ5dGVzLWZvci11bml0LXRlc3Q=";

    private static final String TOKEN = "chzzk-access-token-value-86-characters-long-enough-for-a-realistic-test-value-000000";

    @Test
    void restoresOriginalTokenAfterEncryption() {
        TokenCipher cipher = new TokenCipher(properties(KEY));

        String encrypted = cipher.encrypt(TOKEN);

        assertThat(encrypted).startsWith("v1:").doesNotContain(TOKEN);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(TOKEN);
    }

    @Test
    void producesDifferentCiphertextForSameTokenEachTime() {
        TokenCipher cipher = new TokenCipher(properties(KEY));

        // 매번 새 IV를 쓰므로 같은 토큰이라도 저장값이 달라야 한다.
        assertThat(cipher.encrypt(TOKEN)).isNotEqualTo(cipher.encrypt(TOKEN));
    }

    @Test
    void failsToDecryptWithDifferentKey() {
        String encrypted = new TokenCipher(properties(KEY)).encrypt(TOKEN);
        TokenCipher otherCipher = new TokenCipher(properties(OTHER_KEY));

        assertThatThrownBy(() -> otherCipher.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsToDecryptTamperedValue() {
        TokenCipher cipher = new TokenCipher(properties(KEY));
        String encrypted = cipher.encrypt(TOKEN);
        // GCM 인증 태그가 변조를 잡아낸다.
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "A=";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnknownStorageFormat() {
        TokenCipher cipher = new TokenCipher(properties(KEY));

        assertThatThrownBy(() -> cipher.decrypt("plain-text-token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToStartWithoutUsableKey() {
        // 약한 키로 토큰을 암호화하느니 기동을 실패시킨다.
        assertThatThrownBy(() -> new TokenCipher(properties("")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TokenCipher(properties("dG9vLXNob3J0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private PlatformProperties properties(String tokenEncryptionKey) {
        return new PlatformProperties(
                new PlatformProperties.Chzzk("id", "secret", "uri", "auth", "token", "revoke", "me"),
                tokenEncryptionKey,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        );
    }
}
