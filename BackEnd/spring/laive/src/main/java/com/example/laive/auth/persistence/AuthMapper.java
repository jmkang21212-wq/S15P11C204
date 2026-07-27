package com.example.laive.auth.persistence;

import com.example.laive.auth.domain.RefreshTokenRecord;
import com.example.laive.auth.domain.Streamer;
import com.example.laive.auth.domain.StreamerInsert;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.Optional;

/**
 * OAuth 회원, 스트리머와 Refresh Token을 PostgreSQL에 저장·조회하는 MyBatis Mapper다.
 */
@Mapper
public interface AuthMapper {

    /** provider와 provider 사용자 ID로 연결된 서비스 회원을 조회한다. */
    @Select("""
            SELECT s.id AS streamer_id,
                   s.email,
                   s.nickname,
                   s.profile_image_url
              FROM social_accounts sa
              JOIN streamers s ON s.id = sa.streamer_id
             WHERE sa.provider = #{provider}
               AND sa.provider_user_id = #{providerUserId}
            """)
    Optional<Streamer> findStreamerBySocialAccount(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId
    );

    /** Refresh Token 갱신 시 응답에 필요한 스트리머 요약 정보를 조회한다. */
    @Select("""
            SELECT id AS streamer_id, email, nickname, profile_image_url
              FROM streamers
             WHERE id = #{streamerId}
            """)
    Optional<Streamer> findStreamerById(@Param("streamerId") long streamerId);

    /** 소셜 이메일을 streamers.email에 저장해도 되는지 확인한다. */
    @Select("SELECT EXISTS (SELECT 1 FROM streamers WHERE email = #{email})")
    boolean existsStreamerByEmail(@Param("email") String email);

    /** 같은 식별자의 동시 회원 생성을 트랜잭션 범위에서 직렬화한다. */
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{identity}, 0))")
    String lockIdentity(@Param("identity") String identity);

    /** 비밀번호가 없는 소셜 전용 스트리머를 생성하고 생성된 ID를 객체에 주입한다. */
    @Insert("""
            INSERT INTO streamers (email, password_hash, nickname, profile_image_url)
            VALUES (#{email}, NULL, #{nickname}, #{profileImageUrl})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertStreamer(StreamerInsert streamer);

    /** 서비스 회원과 네이버 계정의 영구 연결 정보를 저장한다. */
    @Insert("""
            INSERT INTO social_accounts (
                streamer_id, provider, provider_user_id, provider_email
            ) VALUES (
                #{streamerId}, #{provider}, #{providerUserId}, #{providerEmail}
            )
            """)
    void insertSocialAccount(
            @Param("streamerId") long streamerId,
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId,
            @Param("providerEmail") String providerEmail
    );

    /** 네이버에서 새로 제공한 이메일 정보를 소셜 계정에 동기화한다. */
    @Update("""
            UPDATE social_accounts
               SET provider_email = #{providerEmail}
             WHERE provider = #{provider}
               AND provider_user_id = #{providerUserId}
            """)
    void updateProviderEmail(
            @Param("provider") String provider,
            @Param("providerUserId") String providerUserId,
            @Param("providerEmail") String providerEmail
    );

    /** 원문이 아닌 Refresh Token 해시와 만료 시각을 저장한다. */
    @Insert("""
            INSERT INTO auth_refresh_tokens (streamer_id, token_hash, expires_at)
            VALUES (#{streamerId}, #{tokenHash}, #{expiresAt})
            """)
    void insertRefreshToken(
            @Param("streamerId") long streamerId,
            @Param("tokenHash") String tokenHash,
            @Param("expiresAt") Instant expiresAt
    );

    /** 토큰 회전 경쟁을 막기 위해 Refresh Token 행을 배타 잠금으로 조회한다. */
    @Select("""
            SELECT id,
                   streamer_id,
                   expires_at,
                   revoked_at
              FROM auth_refresh_tokens
             WHERE token_hash = #{tokenHash}
             FOR UPDATE
            """)
    Optional<RefreshTokenRecord> findRefreshTokenForUpdate(@Param("tokenHash") String tokenHash);

    /** 지정한 Refresh Token을 한 번만 폐기한다. */
    @Update("""
            UPDATE auth_refresh_tokens
               SET revoked_at = #{revokedAt}
             WHERE id = #{id}
               AND revoked_at IS NULL
            """)
    int revokeRefreshToken(@Param("id") long id, @Param("revokedAt") Instant revokedAt);

    /** 로그아웃 시 토큰 해시로 활성 Refresh Token을 폐기한다. */
    @Update("""
            UPDATE auth_refresh_tokens
               SET revoked_at = #{revokedAt}
             WHERE token_hash = #{tokenHash}
               AND revoked_at IS NULL
            """)
    int revokeRefreshTokenByHash(
            @Param("tokenHash") String tokenHash,
            @Param("revokedAt") Instant revokedAt
    );
}
