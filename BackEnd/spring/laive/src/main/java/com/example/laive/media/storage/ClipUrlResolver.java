package com.example.laive.media.storage;

import com.example.laive.media.config.ClipProperties;
import org.springframework.stereotype.Component;

/**
 * DB에 저장된 클립 키를 조회 응답용 절대 URL로 만든다.
 *
 * <p>DB에는 키만 저장한다. 절대 URL을 넣으면 도메인이 바뀌는 순간 그전에 만든 클립이
 * 전부 죽은 링크가 되고(EC2 IP → 발표용 도메인), 로컬 개발 DB에 {@code localhost:8080}이
 * 박히는 문제도 같이 생긴다. 조립을 여기 한 곳에 모아 두면 S3로 옮길 때도
 * 설정값 하나만 바꾸면 된다.
 */
@Component
public class ClipUrlResolver {

    private final String baseUrl;

    public ClipUrlResolver(ClipProperties properties) {
        String configured = properties.publicBaseUrl();
        this.baseUrl = configured.endsWith("/")
                ? configured.substring(0, configured.length() - 1)
                : configured;
    }

    /** 키가 없으면(클립 생성 전·실패) null을 그대로 돌려준다 — API 1.5의 계약이다. */
    public String toUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return baseUrl + "/" + key;
    }
}
