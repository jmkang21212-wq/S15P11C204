package com.example.laive.broadcast.session.repository.mybatis;

import com.example.laive.broadcast.session.domain.BroadcastPlatformSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 플랫폼 자식 세션과 외부 리소스의 MyBatis Mapper다. */
@Mapper
public interface BroadcastPlatformSessionMapper {

    @Select("""
            INSERT INTO broadcast_platform_sessions (
                broadcast_session_id, platform_account_id, status
            ) VALUES (#{broadcastSessionId}, #{platformAccountId}, 'PREPARING')
            ON CONFLICT (broadcast_session_id, platform_account_id) DO UPDATE
               SET updated_at = broadcast_platform_sessions.updated_at
            RETURNING id
            """)
    Long insert(
            @Param("broadcastSessionId") long broadcastSessionId,
            @Param("platformAccountId") long platformAccountId
    );

    @Select("""
            SELECT ps.id AS platform_session_id,
                   ps.broadcast_session_id,
                   ps.platform_account_id,
                   pa.platform AS platform_code,
                   pa.channel_name,
                   ps.status,
                   ps.started_at,
                   ps.ended_at,
                   ps.last_synced_at,
                   ps.error_code,
                   (SELECT r.url
                      FROM platform_session_resources r
                     WHERE r.platform_session_id = ps.id
                       AND r.resource_type IN ('BROADCAST', 'VIDEO')
                     ORDER BY CASE r.resource_type WHEN 'BROADCAST' THEN 0 ELSE 1 END, r.id
                     LIMIT 1) AS watch_url
              FROM broadcast_platform_sessions ps
              JOIN platform_accounts pa ON pa.id = ps.platform_account_id
             WHERE ps.broadcast_session_id = #{broadcastSessionId}
             ORDER BY ps.id
            """)
    List<BroadcastPlatformSession> findByBroadcastSessionId(
            @Param("broadcastSessionId") long broadcastSessionId
    );

    @Select("""
            SELECT ps.id AS platform_session_id,
                   ps.broadcast_session_id,
                   ps.platform_account_id,
                   pa.platform AS platform_code,
                   pa.channel_name,
                   ps.status,
                   ps.started_at,
                   ps.ended_at,
                   ps.last_synced_at,
                   ps.error_code,
                   NULL AS watch_url
              FROM broadcast_platform_sessions ps
              JOIN platform_accounts pa ON pa.id = ps.platform_account_id
             WHERE ps.id = #{platformSessionId}
            """)
    Optional<BroadcastPlatformSession> findById(@Param("platformSessionId") long platformSessionId);

    @Update("""
            UPDATE broadcast_platform_sessions
               SET status = 'LIVE',
                   started_at = COALESCE(started_at, #{startedAt}),
                   last_synced_at = #{startedAt},
                   error_code = NULL
             WHERE id = #{platformSessionId}
               AND status = 'PREPARING'
            """)
    int markLive(
            @Param("platformSessionId") long platformSessionId,
            @Param("startedAt") Instant startedAt
    );

    @Update("""
            UPDATE broadcast_platform_sessions
               SET status = 'ENDED',
                   ended_at = #{endedAt},
                   last_synced_at = #{endedAt}
             WHERE id = #{platformSessionId}
               AND status IN ('PREPARING', 'LIVE')
            """)
    int markEnded(
            @Param("platformSessionId") long platformSessionId,
            @Param("endedAt") Instant endedAt
    );

    @Update("""
            UPDATE broadcast_platform_sessions
               SET status = 'FAILED',
                   ended_at = #{failedAt},
                   last_synced_at = #{failedAt},
                   error_code = #{errorCode}
             WHERE id = #{platformSessionId}
               AND status IN ('PREPARING', 'LIVE')
            """)
    int markFailed(
            @Param("platformSessionId") long platformSessionId,
            @Param("failedAt") Instant failedAt,
            @Param("errorCode") String errorCode
    );

    @Update("""
            UPDATE broadcast_platform_sessions
               SET status = 'ENDED',
                   ended_at = #{endedAt},
                   last_synced_at = #{endedAt}
             WHERE broadcast_session_id = #{broadcastSessionId}
               AND status IN ('PREPARING', 'LIVE')
            """)
    int markAllEnded(
            @Param("broadcastSessionId") long broadcastSessionId,
            @Param("endedAt") Instant endedAt
    );

    @Select("""
            SELECT count(*)
              FROM broadcast_platform_sessions
             WHERE broadcast_session_id = #{broadcastSessionId}
               AND status IN ('PREPARING', 'LIVE')
            """)
    long countActive(@Param("broadcastSessionId") long broadcastSessionId);

    @Select("""
            SELECT count(*)
              FROM broadcast_platform_sessions
             WHERE broadcast_session_id = #{broadcastSessionId}
               AND status IN ('LIVE', 'ENDED')
            """)
    long countEverLive(@Param("broadcastSessionId") long broadcastSessionId);

    @Insert("""
            INSERT INTO platform_session_resources (
                platform_session_id, resource_type, external_id, url, metadata
            ) VALUES (
                #{platformSessionId}, #{resourceType}, #{externalId}, #{url},
                CAST(#{metadataJson} AS jsonb)
            )
            ON CONFLICT (platform_session_id, resource_type, external_id) DO UPDATE
               SET url = EXCLUDED.url,
                   metadata = EXCLUDED.metadata
            """)
    int upsertResource(
            @Param("platformSessionId") long platformSessionId,
            @Param("resourceType") String resourceType,
            @Param("externalId") String externalId,
            @Param("url") String url,
            @Param("metadataJson") String metadataJson
    );

    @Insert("""
            INSERT INTO platform_session_capability_runs (
                platform_session_id, capability_code, support_status, run_status
            ) VALUES (
                #{platformSessionId}, #{capabilityCode}, #{supportStatus},
                CASE WHEN #{supportStatus} = 'SUPPORTED' THEN 'PENDING' ELSE 'COMPLETED' END
            )
            ON CONFLICT (platform_session_id, capability_code) DO NOTHING
            """)
    int insertCapabilityRun(
            @Param("platformSessionId") long platformSessionId,
            @Param("capabilityCode") String capabilityCode,
            @Param("supportStatus") String supportStatus
    );

    @Update("""
            UPDATE platform_session_capability_runs
               SET run_status = 'RUNNING', error_code = NULL, updated_at = now()
             WHERE platform_session_id = #{platformSessionId}
               AND capability_code = #{capabilityCode}
               AND support_status = 'SUPPORTED'
               AND run_status = 'PENDING'
            """)
    int markCapabilityRunning(
            @Param("platformSessionId") long platformSessionId,
            @Param("capabilityCode") String capabilityCode
    );

    @Update("""
            UPDATE platform_session_capability_runs
               SET run_status = 'COMPLETED', last_succeeded_at = #{completedAt},
                   error_code = NULL, updated_at = #{completedAt}
             WHERE platform_session_id = #{platformSessionId}
               AND support_status = 'SUPPORTED'
               AND run_status IN ('PENDING', 'RUNNING')
            """)
    int markCapabilitiesCompleted(
            @Param("platformSessionId") long platformSessionId,
            @Param("completedAt") Instant completedAt
    );

    @Update("""
            UPDATE platform_session_capability_runs
               SET run_status = 'FAILED', error_code = #{errorCode}, updated_at = #{failedAt}
             WHERE platform_session_id = #{platformSessionId}
               AND support_status = 'SUPPORTED'
               AND run_status IN ('PENDING', 'RUNNING')
            """)
    int markCapabilitiesFailed(
            @Param("platformSessionId") long platformSessionId,
            @Param("failedAt") Instant failedAt,
            @Param("errorCode") String errorCode
    );
}
