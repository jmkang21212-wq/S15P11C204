package com.example.laive.broadcast.live.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
