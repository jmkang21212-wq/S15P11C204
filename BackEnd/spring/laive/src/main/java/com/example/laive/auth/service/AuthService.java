package com.example.laive.auth.service;

import com.example.laive.auth.api.AuthTokenResponse;
import com.example.laive.auth.domain.Streamer;
import com.example.laive.auth.domain.StreamerInsert;
import com.example.laive.auth.oauth.NaverProfile;
import com.example.laive.auth.persistence.AuthMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 계정 조회·회원 생성과 토큰 서비스들을 묶어 로그인 유스케이스를 처리한다.
 */
@Service
public class AuthService {

    private static final String NAVER = "NAVER";

    private final AuthMapper authMapper;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AuthMapper authMapper,
            RefreshTokenService refreshTokenService,
            JwtTokenService jwtTokenService
    ) {
        this.authMapper = authMapper;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 네이버 회원 식별자로 기존 계정을 찾거나 소셜 전용 스트리머를 새로 만든다.
     * 동일 네이버 계정의 동시 콜백은 DB advisory lock으로 직렬화한다.
     */
    @Transactional
    public LoginCompletion completeNaverLogin(NaverProfile profile) {
        authMapper.lockIdentity("social:NAVER:" + profile.id());

        Streamer streamer = authMapper.findStreamerBySocialAccount(NAVER, profile.id())
                .orElseGet(() -> createNaverStreamer(profile));
        authMapper.updateProviderEmail(NAVER, profile.id(), profile.email());

        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.issue(streamer.streamerId());
        return new LoginCompletion(refreshToken, streamer);
    }

    /**
     * Refresh Token을 회전하고 해당 스트리머의 새 Access Token을 발급한다.
     */
    @Transactional
    public RefreshedLogin refresh(String rawRefreshToken) {
        RefreshTokenService.RotatedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken);
        Streamer streamer = rotated.streamer();
        AuthTokenResponse response = new AuthTokenResponse(
                jwtTokenService.issue(streamer.streamerId()),
                jwtTokenService.expiresInSeconds(),
                streamer
        );
        return new RefreshedLogin(rotated.token(), response);
    }

    /**
     * 전달된 Refresh Token이 있으면 서버에서 폐기한다. 토큰이 없어도 로그아웃은 멱등 처리한다.
     */
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private Streamer createNaverStreamer(NaverProfile profile) {
        String streamerEmail = profile.email();
        if (streamerEmail != null) {
            // 이메일이 같은 일반 계정을 자동 연결하지 않아 계정 탈취 가능성을 차단한다.
            authMapper.lockIdentity("streamer-email:" + streamerEmail);
            if (authMapper.existsStreamerByEmail(streamerEmail)) {
                streamerEmail = null;
            }
        }

        StreamerInsert insert = new StreamerInsert(
                streamerEmail,
                profile.resolvedNickname(),
                profile.profileImage()
        );
        authMapper.insertStreamer(insert);
        authMapper.insertSocialAccount(
                insert.getId(),
                NAVER,
                profile.id(),
                profile.email()
        );
        return new Streamer(
                insert.getId(),
                insert.getEmail(),
                insert.getNickname(),
                insert.getProfileImageUrl()
        );
    }

    public record LoginCompletion(
            RefreshTokenService.IssuedRefreshToken refreshToken,
            Streamer streamer
    ) {
    }

    public record RefreshedLogin(
            RefreshTokenService.IssuedRefreshToken refreshToken,
            AuthTokenResponse response
    ) {
    }
}
