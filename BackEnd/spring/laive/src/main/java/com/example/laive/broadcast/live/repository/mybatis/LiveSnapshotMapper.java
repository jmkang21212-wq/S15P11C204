package com.example.laive.broadcast.live.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL 전용 SQL은 resources/mapper/broadcast/LiveSnapshotMapper.xml에 둔다.
 */
@Mapper
public interface LiveSnapshotMapper {

    /**
     * DB에 저장된 마지막 시청자 수를 찾는다. Redis 스냅샷이 없을 때(서버 재시작
     * 직후 등)의 대체 경로다.
     */
    Optional<Integer> findLatestViewerCount(@Param("sessionId") long sessionId);

    /** 세션의 최근 브리핑(RISK·CHAT_SUMMARY·FAQ·PROMISE)을 최신순으로 찾는다. */
    List<LiveBriefingRow> findRecentBriefings(@Param("sessionId") long sessionId, @Param("limit") int limit);
}
