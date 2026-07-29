package com.example.laive.broadcast.collect.service;

import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.collect.domain.ChatMessageItem;
import com.example.laive.broadcast.collect.domain.CollectionGap;
import com.example.laive.broadcast.collect.domain.ViewerMetricItem;
import com.example.laive.broadcast.collect.repository.mybatis.CollectMapper;
import com.example.laive.broadcast.collect.repository.mybatis.ResolvedChatMessage;
import com.example.laive.broadcast.collect.repository.mybatis.SessionCollectionRef;
import com.example.laive.broadcast.collect.repository.mybatis.ViewerProfileRef;
import com.example.laive.broadcast.collect.repository.mybatis.ViewerSeen;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 수집기가 보낸 원천 데이터를 영속화한다(명세 13.3).
 */
@Service
public class CollectionIngestService {

    private final CollectMapper collectMapper;

    public CollectionIngestService(CollectMapper collectMapper) {
        this.collectMapper = collectMapper;
    }

    /**
     * 채팅 배치를 저장한다. viewerHash로 시청자 프로필을 조회·생성한 뒤 chat_messages에 넣는다.
     *
     * <p>duplicateCount는 항상 0이다. chat_messages에는 자연키가 없어 개별 건의
     * 중복을 DB가 판별할 수 없고, 배치 단위 재전송은 호출 계층의 X-Request-Id
     * 멱등 가드가 먼저 막는다.
     */
    @Transactional
    public BatchIngestResult appendChatMessages(long sessionId, List<ChatMessageItem> items) {
        SessionCollectionRef session = collectMapper.findSessionRef(sessionId)
                .orElseThrow(BroadcastSessionException::notFound);

        Map<String, Long> viewerIds = resolveViewerIds(session.platformAccountId(), items);

        List<ResolvedChatMessage> resolved = items.stream()
                .map(item -> new ResolvedChatMessage(
                        viewerIds.get(item.viewerHash()),
                        item.message(),
                        item.sentAt(),
                        item.sentimentLabel()
                ))
                .toList();

        int inserted = collectMapper.insertChatMessages(sessionId, resolved);
        return new BatchIngestResult(inserted, 0);
    }

    /**
     * 시청자 수 배치를 저장한다.
     *
     * <p>(session_id, measured_at) Unique가 재시도 중복을 흡수하므로 재전송이
     * 안전하다. 같은 배치 안의 중복은 DB에 보내기 전에 접는다 — 그대로 보내도
     * ON CONFLICT가 걸러내지만, 그러면 삽입 건수 차이로 계산하는 duplicateCount가
     * "이미 저장돼 있었다"와 "요청 자체가 중복이었다"를 구분하지 못한다.
     */
    @Transactional
    public BatchIngestResult appendViewerMetrics(long sessionId, List<ViewerMetricItem> items) {
        if (collectMapper.findSessionRef(sessionId).isEmpty()) {
            throw BroadcastSessionException.notFound();
        }

        Map<Instant, ViewerMetricItem> byMeasuredAt = new LinkedHashMap<>();
        for (ViewerMetricItem item : items) {
            byMeasuredAt.put(item.measuredAt(), item);
        }

        List<ViewerMetricItem> deduplicated = List.copyOf(byMeasuredAt.values());
        int inserted = collectMapper.insertViewerMetrics(sessionId, deduplicated);
        return new BatchIngestResult(inserted, items.size() - inserted);
    }

    /** 수집 공백 구간을 기록하거나 이미 기록된 구간을 닫는다. */
    @Transactional
    public void recordCollectionGap(long sessionId, CollectionGap gap) {
        if (collectMapper.findSessionRef(sessionId).isEmpty()) {
            throw BroadcastSessionException.notFound();
        }
        collectMapper.upsertCollectionGap(sessionId, gap);
    }

    /** 세션 종료 시 아직 열려 있는 공백을 종료 시각으로 닫는다. */
    @Transactional
    public int closeOpenCollectionGaps(long sessionId, Instant endedAt) {
        return collectMapper.closeOpenCollectionGaps(sessionId, endedAt);
    }

    /**
     * 배치에 등장한 해시를 한 번씩만 Upsert하고 해시 → viewer_id 대응을 만든다.
     *
     * <p>중복 제거가 필수다. 같은 명령에서 같은 충돌 대상을 두 번 갱신하면
     * PostgreSQL이 거부한다.
     *
     * <p>해시마다 가장 이른 시각과 가장 늦은 시각을 함께 접는다. 이유는
     * {@link ViewerSeen} 주석에 있다.
     */
    private Map<String, Long> resolveViewerIds(long platformAccountId, List<ChatMessageItem> items) {
        Map<String, ViewerSeen> seenByHash = new LinkedHashMap<>();
        for (ChatMessageItem item : items) {
            seenByHash.merge(
                    item.viewerHash(),
                    new ViewerSeen(item.viewerHash(), item.sentAt(), item.sentAt()),
                    (existing, candidate) -> new ViewerSeen(
                            existing.viewerHash(),
                            earlier(existing.firstSeenAt(), candidate.firstSeenAt()),
                            later(existing.lastSeenAt(), candidate.lastSeenAt())
                    )
            );
        }

        List<ViewerSeen> seen = new ArrayList<>(seenByHash.values());

        Map<String, Long> viewerIds = new LinkedHashMap<>();
        for (ViewerProfileRef ref : collectMapper.upsertViewerProfiles(platformAccountId, seen)) {
            viewerIds.put(ref.viewerHash(), ref.viewerId());
        }
        return viewerIds;
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static Instant later(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }
}
