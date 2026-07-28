package com.example.laive.platform.service;

import com.example.laive.platform.config.PlatformProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 플랫폼 토큰을 AES-256-GCM으로 암·복호화한다 (NFR-S-03: 평문 저장 금지).
 *
 * 저장 형식은 "v1:{base64(iv)}:{base64(ciphertext+tag)}"이다.
 * 버전 접두사를 두어 나중에 키나 알고리즘을 교체할 때 기존 값을 구분할 수 있게 한다.
 */
@Component
public class TokenCipher {

    private static final String VERSION = "v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenCipher(PlatformProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.tokenEncryptionKey()), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("암호화할 값이 null입니다.");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            Base64.Encoder encoder = Base64.getEncoder();
            return VERSION + ":" + encoder.encodeToString(iv) + ":" + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("플랫폼 토큰 암호화에 실패했습니다.", exception);
        }
    }

    public String decrypt(String stored) {
        // 복호화 실패는 키 교체나 데이터 손상 같은 서버 측 문제이므로 사용자 메시지로 노출하지 않는다.
        if (stored == null) {
            throw new IllegalArgumentException("복호화할 값이 null입니다.");
        }
        String[] parts = stored.split(":", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("알 수 없는 토큰 저장 형식입니다.");
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] iv = decoder.decode(parts[1]);
            byte[] ciphertext = decoder.decode(parts[2]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("플랫폼 토큰 복호화에 실패했습니다.", exception);
        }
    }

    private static byte[] decodeKey(String encodedKey) {
        // 키가 없거나 길이가 모자라면 토큰을 약하게 암호화하느니 기동을 실패시킨다.
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("PLATFORM_TOKEN_KEY must be configured");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("PLATFORM_TOKEN_KEY must be Base64 encoded", exception);
        }
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalStateException("PLATFORM_TOKEN_KEY must decode to exactly 32 bytes");
        }
        return keyBytes;
    }
}
