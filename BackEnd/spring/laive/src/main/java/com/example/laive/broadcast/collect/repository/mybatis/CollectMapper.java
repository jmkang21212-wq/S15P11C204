package com.example.laive.broadcast.collect.repository.mybatis;

import com.example.laive.broadcast.collect.domain.CollectionGap;
import com.example.laive.broadcast.collect.domain.ViewerMetricItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL 전용 SQL은 resources/mapper/broadcast/CollectMapper.xml에 둔다.
 */
@Mapper
public interface CollectMapper {

    /** 세션의 소유 스트리머와 플랫폼 계정을 찾는다. 없으면 알 수 없는 세션이다. */
    Optional<SessionCollectionRef> findSessionRef(@Param("sessionId") long sessionId);

    Optional<PlatformSessionCollectionRef> findPlatformSessionRef(
            @Param("platformSessionId") long platformSessionId
    );

    Optional<PlatformCollectorSpecRow> findPlatformCollectorSpec(
            @Param("platformSessionId") long platformSessionId
    );

    List<ViewerProfileRef> upsertViewerProfiles(
            @Param("platformAccountId") long platformAccountId,
            @Param("seen") List<ViewerSeen> seen
    );

    int insertChatMessages(
            @Param("sessionId") long sessionId,
            @Param("items") List<ResolvedChatMessage> items
    );

    int insertPlatformChatMessages(
            @Param("sessionId") long sessionId,
            @Param("platformSessionId") long platformSessionId,
            @Param("items") List<ResolvedChatMessage> items
    );

    int insertViewerMetrics(
            @Param("sessionId") long sessionId,
            @Param("items") List<ViewerMetricItem> items
    );

    int insertPlatformViewerMetrics(
            @Param("sessionId") long sessionId,
            @Param("platformSessionId") long platformSessionId,
            @Param("items") List<ViewerMetricItem> items
    );

    int upsertCollectionGap(
            @Param("sessionId") long sessionId,
            @Param("gap") CollectionGap gap
    );

    int upsertPlatformCollectionGap(
            @Param("sessionId") long sessionId,
            @Param("platformSessionId") long platformSessionId,
            @Param("gap") CollectionGap gap
    );

    int closeOpenPlatformCollectionGaps(
            @Param("platformSessionId") long platformSessionId,
            @Param("endedAt") Instant endedAt
    );

    /** 세션 종료 시 아직 열려 있는 공백을 종료 시각으로 닫는다. */
    int closeOpenCollectionGaps(
            @Param("sessionId") long sessionId,
            @Param("endedAt") Instant endedAt
    );

    Integer sumLatestPlatformViewerCounts(
            @Param("sessionId") long sessionId,
            @Param("measuredAt") Instant measuredAt
    );
}
