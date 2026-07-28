package com.example.laive.platform.domain;

import java.time.Instant;

/**
 * 연동 목록 응답에 사용하는 플랫폼 계정 요약 모델이다.
 * 토큰 필드를 의도적으로 포함하지 않아 외부 응답에 토큰이 새어 나가지 않게 한다.
 *
 * MyBatis가 생성자 파라미터 순서로 매핑하므로 SELECT 컬럼 순서와 이 순서를 함께 유지해야 한다.
 */
public record PlatformAccount(
        long platformAccountId,
        String platform,
        String channelId,
        String channelName,
        String connectionStatus,
        Instant connectedAt
) {
}
