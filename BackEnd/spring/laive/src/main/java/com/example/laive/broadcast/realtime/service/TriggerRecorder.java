package com.example.laive.broadcast.realtime.service;

import com.example.laive.broadcast.realtime.domain.highlight.HighlightCandidate;
import com.example.laive.broadcast.realtime.repository.mybatis.TriggerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * 확정된 CHAT_SURGE 구간을 {@code triggers}에 남긴다(명세 13.7 트리거 앵커).
 *
 * <p>트리거는 방송 중에만 만들 수 있는 리포트의 기준점이다. 여기서 실패하면
 * 그 구간의 하이라이트는 소급 복구할 수 없으므로, 저장 실패는 삼키되 반드시
 * 로그로 남긴다 — 판정 틱 루프가 죽는 것보다 구간 하나를 잃는 쪽이 낫다.
 */
@Service
public class TriggerRecorder {

    /** peak_z NUMERIC(6,3), peak_engagement NUMERIC(5,4)의 상한. 넘치면 INSERT가 거부된다. */
    private static final BigDecimal MAX_PEAK_Z = new BigDecimal("999.999");
    private static final BigDecimal MAX_PEAK_ENGAGEMENT = new BigDecimal("9.9999");

    private static final Logger log = LoggerFactory.getLogger(TriggerRecorder.class);

    private final TriggerMapper triggerMapper;

    public TriggerRecorder(TriggerMapper triggerMapper) {
        this.triggerMapper = triggerMapper;
    }

    public void record(long sessionId, Instant sessionStartedAt, HighlightCandidate candidate) {
        // 리드 소급이 방송 시작 전으로 나가면 시작점을 방송 시작으로 자른다.
        // start_ms >= 0 CHECK와 짝을 맞추기 위해 절대 시각도 함께 자른다.
        Instant startedAt = candidate.startedAt().isBefore(sessionStartedAt)
                ? sessionStartedAt
                : candidate.startedAt();
        Instant endedAt = candidate.endedAt();
        if (!endedAt.isAfter(startedAt)) {
            log.warn("구간 길이가 0 이하라 트리거를 버립니다: sessionId={} start={} end={}",
                    sessionId, startedAt, endedAt);
            return;
        }

        long startMs = Duration.between(sessionStartedAt, startedAt).toMillis();
        long endMs = Duration.between(sessionStartedAt, endedAt).toMillis();

        try {
            int inserted = triggerMapper.insertChatSurgeTrigger(
                    sessionId,
                    startMs,
                    endMs,
                    startedAt,
                    endedAt,
                    clamp(candidate.peakZ(), 3, MAX_PEAK_Z),
                    clamp(candidate.peakEngagement(), 4, MAX_PEAK_ENGAGEMENT)
            );
            if (inserted > 0) {
                log.info("CHAT_SURGE 트리거 확정: sessionId={} startMs={} endMs={} peakZ={}",
                        sessionId, startMs, endMs, String.format("%.2f", candidate.peakZ()));
            } else {
                log.debug("같은 시작점의 트리거가 이미 있어 무시합니다: sessionId={} startMs={}",
                        sessionId, startMs);
            }
        } catch (RuntimeException exception) {
            log.error("트리거 저장 실패(구간 유실): sessionId={} startMs={} endMs={}",
                    sessionId, startMs, endMs, exception);
        }
    }

    private static BigDecimal clamp(double value, int scale, BigDecimal max) {
        BigDecimal scaled = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
        if (scaled.compareTo(max) > 0) {
            return max;
        }
        return scaled.max(BigDecimal.ZERO);
    }
}
