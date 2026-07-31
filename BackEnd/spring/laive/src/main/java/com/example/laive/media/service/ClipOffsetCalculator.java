package com.example.laive.media.service;

import com.example.laive.media.config.ClipProperties;
import com.example.laive.media.domain.ClipCut;
import com.example.laive.media.domain.RecordingTimebase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 하이라이트 구간을 녹화본의 몇 초 지점인지로 환산한다(CLIP_SPEC.md §오프셋 계산).
 *
 * <p><b>이 계산이 이 기능에서 가장 틀리기 쉽고 가장 늦게 발각되는 지점이다.</b>
 * 틀려도 클립은 정상적으로 만들어지고 업로드도 성공한다. 엉뚱한 장면이 담겼다는
 * 것은 사람이 열어봐야 알고, 확인 한 번에 실방송 한 번이 든다. 그래서 계산을
 * 클라이언트에 맡기지 않고 여기 한 곳에 모아 테스트로 고정한다.
 *
 * <h2>왜 송출 지연을 빼는가</h2>
 * 시청자는 CDN을 거친 화면을 보고, 채팅은 <b>그 지연된 화면</b>에 대한 반응이다.
 * OBS 로컬 녹화는 CDN을 거치지 않아 4~5초 빠르므로, 채팅 시각을 그대로 녹화
 * 타임코드로 옮기면 원인 장면이 이미 지나간 뒤가 된다.
 *
 * <h2>왜 반응 지연은 빼지 않는가</h2>
 * 사람이 장면을 보고 채팅을 치기까지의 지연은 {@code laive.highlight.lead}(30초)로
 * 트리거 확정 시점에 이미 소급되어 있다({@code TriggerRecorder}). 여기서 또 빼면
 * 30초 앞이 통째로 들어온다.
 */
@Component
public class ClipOffsetCalculator {

    private static final Logger log = LoggerFactory.getLogger(ClipOffsetCalculator.class);

    private final Duration platformDelay;

    public ClipOffsetCalculator(ClipProperties properties) {
        this.platformDelay = properties.platformDelay();
    }

    /**
     * 구간이 녹화본 안에 조금이라도 걸치면 잘라낼 위치를, 통째로 벗어나면 빈 값을 준다.
     *
     * @param absoluteStart 구간 시작의 실제 시각. {@link #resolveStart}로 구한다
     * @param durationMs    구간 길이. 하이라이트의 {@code end_ms - start_ms}
     * @param timebase      녹화 기준점
     */
    public Optional<ClipCut> calculate(Instant absoluteStart, long durationMs, RecordingTimebase timebase) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException("구간 길이가 0 이하입니다: " + durationMs);
        }
        long start = timebase.toTimecodeMs(absoluteStart) - platformDelay.toMillis();
        long end = start + durationMs;

        // 녹화가 방송보다 늦게 시작되면 앞부분이 통째로 없다.
        if (end <= 0) {
            log.info("구간이 녹화 시작 이전이라 클립을 만들 수 없습니다: start={}ms end={}ms", start, end);
            return Optional.empty();
        }

        // 앞이 잘리는 경우 끝점을 고정하고 시작만 당긴다 — 없는 앞부분을 요구하면
        // ffmpeg가 0초부터 읽어서 구간 전체가 뒤로 밀린다.
        if (start < 0) {
            log.info("구간 앞부분이 녹화 이전이라 {}ms 잘라냅니다.", -start);
            return Optional.of(new ClipCut(0, end));
        }
        return Optional.of(new ClipCut(start, durationMs));
    }

    /**
     * 구간 시작의 실제 시각을 고른다.
     *
     * <p>{@code triggers.started_at}을 그대로 쓰는 것이 정답이다. 세션 시작 시각에
     * 오프셋을 더해 재계산하는 경로는 {@code highlights.trigger_id}가
     * {@code ON DELETE SET NULL}이라 NULL일 때만 쓰는 폴백이며, 정상 흐름에서는
     * 발생하지 않으므로 로그를 남긴다.
     *
     * <p>V8이 트리거에 오프셋과 절대 시각을 둘 다 저장한 이유가 이것이다 —
     * "조회할 때마다 세션 시작 시각과 조인해 변환하면 오프셋과 절대 시각을 섞는
     * 실수가 조인 지점마다 되살아난다."
     */
    public Instant resolveStart(
            long highlightId,
            Instant triggerStartedAt,
            Instant sessionStartedAt,
            long highlightStartMs
    ) {
        if (triggerStartedAt != null) {
            return triggerStartedAt;
        }
        log.warn("트리거 앵커가 없어 세션 오프셋으로 폴백합니다: highlightId={} startMs={}",
                highlightId, highlightStartMs);
        return sessionStartedAt.plusMillis(highlightStartMs);
    }
}
