package com.example.laive.broadcast.session.service;

import com.example.laive.broadcast.session.domain.BroadcastSessionDetail;
import com.example.laive.broadcast.session.domain.BroadcastSessionListItem;
import com.example.laive.broadcast.session.domain.BroadcastStatus;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import com.example.laive.broadcast.session.repository.BroadcastSessionRepository;
import com.example.laive.broadcast.session.repository.mybatis.BroadcastPlatformSessionMapper;
import com.example.laive.broadcast.session.domain.BroadcastPlatformSession;
import com.example.laive.common.api.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 소유권 조건이 포함된 방송 세션 조회를 담당한다.
 */
@Service
@Transactional(readOnly = true)
public class BroadcastSessionQueryService {

    private final BroadcastSessionRepository repository;
    private final BroadcastPlatformSessionMapper platformSessionMapper;

    public BroadcastSessionQueryService(
            BroadcastSessionRepository repository,
            BroadcastPlatformSessionMapper platformSessionMapper
    ) {
        this.repository = repository;
        this.platformSessionMapper = platformSessionMapper;
    }

    public Optional<BroadcastSessionDetail> getCurrent(long streamerId) {
        return repository.findCurrent(streamerId);
    }

    public BroadcastSessionDetail getSession(long streamerId, long sessionId) {
        return repository.findDetail(streamerId, sessionId)
                .orElseThrow(BroadcastSessionException::notFound);
    }

    public List<BroadcastPlatformSession> getPlatformSessions(long sessionId) {
        return platformSessionMapper.findByBroadcastSessionId(sessionId);
    }

    public PageResponse<BroadcastSessionListItem> getSessions(
            long streamerId,
            BroadcastStatus status,
            int page,
            int size
    ) {
        long offset = Math.multiplyExact((long) page, size);
        List<BroadcastSessionListItem> items = repository.findPage(
                streamerId,
                status,
                size,
                offset
        );
        long totalElements = repository.count(streamerId, status);
        return PageResponse.of(items, page, size, totalElements);
    }
}
