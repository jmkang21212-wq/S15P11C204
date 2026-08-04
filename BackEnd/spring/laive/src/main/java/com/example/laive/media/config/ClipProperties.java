package com.example.laive.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 하이라이트 클립 생성 설정이다(CLIP_SPEC.md).
 *
 * @param platformDelay  CDN 송출 지연 보정. 시청자가 보는 화면과 OBS 로컬 녹화의
 *                       시차이며, 실방송 1회로 캘리브레이션해야 하는 유일한 값이다.
 * @param maxAttempts    클립 생성 실패 재시도 상한. 소진하면 FAILED로 확정하고
 *                       앱이 더 이상 같은 작업을 받지 않는다.
 * @param storageDir     클립·썸네일을 저장할 디렉터리. nginx가 이 경로를 서빙한다.
 * @param publicBaseUrl  조회 응답에서 상대 경로 앞에 붙일 접두사. DB에는 상대 경로만
 *                       저장하므로 도메인이 바뀌어도 기존 클립이 죽지 않는다.
 * @param maxUploadBytes 업로드 크기 상한. nginx의 client_max_body_size도 함께 올려야
 *                       한다 — 그쪽이 낮으면 요청이 Spring에 도달조차 하지 않는다.
 */
@ConfigurationProperties(prefix = "laive.clip")
public record ClipProperties(
        Duration platformDelay,
        int maxAttempts,
        String storageDir,
        String publicBaseUrl,
        long maxUploadBytes
) {
}
