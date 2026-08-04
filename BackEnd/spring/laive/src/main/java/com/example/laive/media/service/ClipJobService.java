package com.example.laive.media.service;

import com.example.laive.media.domain.ClipJob;
import com.example.laive.media.domain.ClipJobRow;
import com.example.laive.media.repository.ClipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 앱이 가져갈 클립 작업 목록을 만든다(CLIP_SPEC.md §6.4).
 *
 * <p>작업을 push하지 않고 앱이 물어보게 하는 이유는 스트리머가 방송 종료 직후 앱을
 * 끄는 것이 정상 행동이기 때문이다. push로 밀면 그 순간 작업이 증발하지만, 켜질 때마다
 * 물어보면 원본이 PC에 남아 있는 한 며칠 뒤에 켜도 그대로 이어진다.
 */
@Service
public class ClipJobService {

    private static final Logger log = LoggerFactory.getLogger(ClipJobService.class);

    private final ClipRepository repository;
    private final ClipOffsetCalculator calculator;

    public ClipJobService(ClipRepository repository, ClipOffsetCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    @Transactional
    public List<ClipJob> findPending(long streamerId) {
        List<ClipJob> jobs = new ArrayList<>();
        for (ClipJobRow row : repository.findPendingJobs(streamerId)) {
            toJob(row).ifPresent(jobs::add);
        }
        return jobs;
    }

    /**
     * 구간이 녹화본을 통째로 벗어나면 목록에서 빼고 그 자리에서 SKIPPED로 확정한다.
     *
     * <p>읽기 전용으로 두고 걸러내기만 하면 <b>막다른 길이 생긴다</b> — 앱이 목록에서
     * 못 보니 실패 보고도 못 하고, 결국 PENDING인 채로 영원히 남아 조회할 때마다
     * 같은 계산을 반복한다.
     *
     * <p>여기서 상태를 바꿔도 안전한 이유는 판정이 다시 뒤집히지 않기 때문이다.
     * 이 목록은 녹화가 COMPLETED인 것만 담고, 종료된 녹화에는 기준점 갱신이 거부되므로
     * (RecordingMapper.updateBase) 계산의 입력이 더 이상 변하지 않는다.
     */
    private Optional<ClipJob> toJob(ClipJobRow row) {
        var start = calculator.resolveStart(
                row.highlightId(), row.triggerStartedAt(), row.sessionStartedAt(), row.startMs()
        );
        var cut = calculator.calculate(start, row.durationMs(), row.timebase());
        if (cut.isEmpty()) {
            log.info("구간이 녹화본을 벗어나 SKIPPED로 확정합니다: highlightId={}", row.highlightId());
            repository.markSkipped(row.highlightId(), "구간이 녹화 범위를 벗어남");
            return Optional.empty();
        }
        return Optional.of(new ClipJob(
                row.highlightId(), row.sessionId(), row.sessionTitle(),
                row.recordingPath(), cut.get()
        ));
    }
}
