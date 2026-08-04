package com.example.laive.platform.persistence;

import com.example.laive.platform.domain.PlatformAccount;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 방송 플랫폼 연동 계정을 PostgreSQL에 저장·조회하는 MyBatis Mapper다.
 * SELECT 컬럼 순서는 대응하는 record의 생성자 파라미터 순서와 일치해야 한다.
 */
@Mapper
public interface PlatformAccountMapper {

    /**
     * 내 연동 목록. 토큰 컬럼은 조회하지 않는다.
     *
     * connection_status는 토큰을 실제로 쓸 때만 갱신되므로, 아무도 쓰지 않은 계정은
     * Refresh Token이 만료된 뒤에도 CONNECTED로 남는다. 목록이 사실과 다른 상태를
     * 보여주지 않도록 만료 여부를 조회 시점에 계산해서 내려보낸다.
     * 실제 상태 전환은 PlatformTokenRefresher가 갱신을 시도할 때 기록한다.
     */
    @Select("""
            SELECT id AS platform_account_id,
                   platform,
                   channel_id,
                   channel_name,
                   CASE WHEN connection_status = 'CONNECTED'
                             AND refresh_token_expires_at IS NOT NULL
                             AND refresh_token_expires_at <= now()
                        THEN 'REAUTH_REQUIRED'
                        ELSE connection_status
                   END AS connection_status,
                   connected_at
             FROM platform_accounts
             WHERE streamer_id = #{streamerId}
               AND connection_status <> 'DISCONNECTED'
             ORDER BY connected_at
            """)
    List<PlatformAccount> findByStreamerId(@Param("streamerId") long streamerId);

    /**
     * 토큰 갱신을 직렬화하기 위해 행을 배타 잠금으로 조회한다.
     * 치지직은 갱신 즉시 이전 Access Token을 무효화하므로 갱신·저장이 겹치면 안 된다.
     */
    @Select("""
            SELECT id AS platform_account_id,
                   streamer_id,
                   platform,
                   channel_id,
                   access_token,
                   refresh_token,
                   access_token_expires_at,
                   refresh_token_expires_at,
                   connection_status
              FROM platform_accounts
             WHERE id = #{platformAccountId}
             FOR UPDATE
            """)
    Optional<PlatformAccountSecret> findSecretForUpdate(
            @Param("platformAccountId") long platformAccountId
    );

    /** 연동 해제 시 폐기할 토큰을 읽는다. */
    @Select("""
            SELECT id AS platform_account_id,
                   streamer_id,
                   platform,
                   channel_id,
                   access_token,
                   refresh_token,
                   access_token_expires_at,
                   refresh_token_expires_at,
                   connection_status
              FROM platform_accounts
             WHERE id = #{platformAccountId}
               AND streamer_id = #{streamerId}
            """)
    Optional<PlatformAccountSecret> findSecretByIdAndStreamerId(
            @Param("platformAccountId") long platformAccountId,
            @Param("streamerId") long streamerId
    );

    /**
     * 연동을 생성하거나 같은 스트리머의 재연동이면 토큰을 갱신한다.
     *
     * uq_platform_accounts_platform_channel 덕분에 동일 채널은 한 행만 존재한다.
     * 충돌한 행이 다른 스트리머 소유면 DO UPDATE의 WHERE가 거짓이 되어 아무 행도
     * 반환하지 않으므로, 호출자는 빈 Optional을 "이미 다른 계정에 연동됨"으로 해석한다.
     */
    @Select("""
            INSERT INTO platform_accounts (
                streamer_id, platform, channel_id, channel_name,
                access_token, refresh_token,
                access_token_expires_at, refresh_token_expires_at,
                scope, connection_status
            ) VALUES (
                #{account.streamerId}, #{account.platform}, #{account.channelId}, #{account.channelName},
                #{account.accessToken}, #{account.refreshToken},
                #{account.accessTokenExpiresAt}, #{account.refreshTokenExpiresAt},
                #{account.scope}, 'CONNECTED'
            )
            ON CONFLICT (platform, channel_id) DO UPDATE
               SET streamer_id              = EXCLUDED.streamer_id,
                   channel_name             = EXCLUDED.channel_name,
                   access_token             = EXCLUDED.access_token,
                   refresh_token            = EXCLUDED.refresh_token,
                   access_token_expires_at  = EXCLUDED.access_token_expires_at,
                   refresh_token_expires_at = EXCLUDED.refresh_token_expires_at,
                   scope                    = EXCLUDED.scope,
                   connection_status        = 'CONNECTED'
             WHERE platform_accounts.streamer_id = EXCLUDED.streamer_id
            RETURNING id
            """)
    Optional<Long> upsert(@Param("account") PlatformAccountUpsert account);

    /** 회전된 토큰 쌍을 저장하고 연동 상태를 정상으로 되돌린다. */
    @Update("""
            UPDATE platform_accounts
               SET access_token             = #{accessToken},
                   refresh_token            = #{refreshToken},
                   access_token_expires_at  = #{accessTokenExpiresAt},
                   refresh_token_expires_at = #{refreshTokenExpiresAt},
                   scope                    = #{scope},
                   connection_status        = 'CONNECTED'
             WHERE id = #{platformAccountId}
            """)
    int updateTokens(
            @Param("platformAccountId") long platformAccountId,
            @Param("accessToken") String accessToken,
            @Param("refreshToken") String refreshToken,
            @Param("accessTokenExpiresAt") Instant accessTokenExpiresAt,
            @Param("refreshTokenExpiresAt") Instant refreshTokenExpiresAt,
            @Param("scope") String scope
    );

    /** 재연동이 필요해진 계정을 표시한다. */
    @Update("""
            UPDATE platform_accounts
               SET connection_status = #{connectionStatus}
             WHERE id = #{platformAccountId}
            """)
    int updateConnectionStatus(
            @Param("platformAccountId") long platformAccountId,
            @Param("connectionStatus") String connectionStatus
    );

    /** 본인 소유 연동만 삭제한다. */
    @Select("""
            WITH removed AS (
                DELETE FROM platform_accounts pa
                 WHERE pa.id = #{platformAccountId}
                   AND pa.streamer_id = #{streamerId}
                   AND NOT EXISTS (SELECT 1 FROM broadcast_sessions WHERE platform_account_id = pa.id)
                   AND NOT EXISTS (SELECT 1 FROM calendar_events WHERE platform_account_id = pa.id)
                   AND NOT EXISTS (SELECT 1 FROM viewer_profiles WHERE platform_account_id = pa.id)
                   AND NOT EXISTS (SELECT 1 FROM broadcast_platform_sessions WHERE platform_account_id = pa.id)
                   AND NOT EXISTS (SELECT 1 FROM calendar_event_platform_accounts WHERE platform_account_id = pa.id)
                RETURNING 1
            ), disconnected AS (
                UPDATE platform_accounts pa
                   SET connection_status = 'DISCONNECTED',
                       access_token = NULL,
                       refresh_token = NULL,
                       access_token_expires_at = NULL,
                       refresh_token_expires_at = NULL,
                       disconnected_at = now()
                 WHERE pa.id = #{platformAccountId}
                   AND pa.streamer_id = #{streamerId}
                   AND NOT EXISTS (SELECT 1 FROM removed)
                RETURNING 1
            )
            SELECT count(*)::int FROM (
                SELECT 1 FROM removed UNION ALL SELECT 1 FROM disconnected
            ) changed
            """)
    int delete(
            @Param("platformAccountId") long platformAccountId,
            @Param("streamerId") long streamerId
    );

    default int disconnect(long platformAccountId, long streamerId) {
        return delete(platformAccountId, streamerId);
    }
}
