package com.example.laive.media.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 클립 슬라이스 설정을 켠다.
 *
 * <p>ffmpeg 관련 빈이 없는 것은 누락이 아니다 — 인코딩은 스트리머 PC의 Electron이
 * 수행하고 서버는 결과물만 받는다(CLIP_SPEC.md §0).
 */
@Configuration
@EnableConfigurationProperties(ClipProperties.class)
public class MediaConfig {
}
