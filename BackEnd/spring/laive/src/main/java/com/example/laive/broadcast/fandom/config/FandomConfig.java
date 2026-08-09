package com.example.laive.broadcast.fandom.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 열성 시청자 슬라이스 설정을 켠다. 점수 산식 자체는 SQL(ViewerScoreMapper.xml)이
 * 단일 원천이고 여기에는 노출 규칙만 둔다.
 */
@Configuration
@EnableConfigurationProperties(FandomProperties.class)
public class FandomConfig {
}
