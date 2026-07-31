package com.example.laive.media.repository.mybatis;

import com.example.laive.media.domain.ClipJobRow;
import com.example.laive.media.domain.ClipTarget;
import com.example.laive.media.repository.ClipRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisClipRepository implements ClipRepository {

    private final ClipMapper mapper;

    public MyBatisClipRepository(ClipMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ClipJobRow> findPendingJobs(long streamerId) {
        return mapper.findPendingJobs(streamerId);
    }

    @Override
    public Optional<ClipTarget> findTarget(long streamerId, long highlightId) {
        return mapper.findTarget(streamerId, highlightId);
    }

    @Override
    public int markUploaded(long highlightId, String clipPath, String thumbnailPath) {
        return mapper.markUploaded(highlightId, clipPath, thumbnailPath);
    }

    @Override
    public int recordFailure(long highlightId, String reason, int maxAttempts) {
        return mapper.recordFailure(highlightId, reason, maxAttempts);
    }

    @Override
    public int markSkipped(long highlightId, String reason) {
        return mapper.markSkipped(highlightId, reason);
    }
}
