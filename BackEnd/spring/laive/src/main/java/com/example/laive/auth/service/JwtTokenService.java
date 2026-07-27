package com.example.laive.auth.service;

import com.example.laive.auth.config.AuthProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 인증된 스트리머를 식별하는 단기 HS256 Access Token을 발급한다.
 */
@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, AuthProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 스트리머 ID를 subject로 갖는 설정된 수명의 JWT를 생성한다.
     */
    public String issue(long streamerId) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(Long.toString(streamerId))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.jwt().accessTokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * API 응답의 expiresIn에 사용할 Access Token 수명을 초 단위로 반환한다.
     */
    public long expiresInSeconds() {
        return properties.jwt().accessTokenTtl().toSeconds();
    }
}
