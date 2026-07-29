package com.example.laive.broadcast.realtime.config;

import com.example.laive.broadcast.realtime.domain.highlight.HighlightParams;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * CHAT_SURGE 판정 파라미터 설정이다. 의미는 {@link HighlightParams} 주석에 있다.
 * 초기값은 HIGHLIGHT_SPEC §5의 미검증 출발점이라 전부 밖으로 뺐다.
 *
 * @param tickInterval 판정 틱과 빈 배치 보충 전송의 주기
 */
@ConfigurationProperties(prefix = "laive.highlight")
public record HighlightProperties(
        Duration engageWindow,
        int baselineSize,
        double zEnter,
        int enterTicks,
        double zExit,
        int exitTicks,
        Duration lead,
        Duration maxActive,
        Duration cooldown,
        Duration warmup,
        int minWindowChats,
        double madEpsilon,
        Duration tickInterval
) {

    public HighlightParams toParams() {
        return new HighlightParams(
                engageWindow, baselineSize, zEnter, enterTicks, zExit, exitTicks,
                lead, maxActive, cooldown, warmup, minWindowChats, madEpsilon
        );
    }
}
