package com.example.laive.platform.service;

import com.example.laive.platform.domain.PlatformAccount;
import com.example.laive.platform.domain.PlatformAccountSecret;
import com.example.laive.platform.domain.PlatformAccountUpsert;
import com.example.laive.platform.exception.PlatformCallbackException;
import com.example.laive.platform.exception.PlatformException;
import com.example.laive.platform.oauth.ChzzkOAuthClient;
import com.example.laive.platform.oauth.ChzzkTokenRejectedException;
import com.example.laive.platform.oauth.ChzzkTokenResponse;
import com.example.laive.platform.oauth.ChzzkUser;
import com.example.laive.platform.persistence.PlatformAccountMapper;
import com.example.laive.platform.service.PlatformLinkStateService.LinkState;
import com.example.laive.platform.service.PlatformTokenRefresher.TokenResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 방송 플랫폼 연동의 시작·완료·조회·해제 유스케이스를 처리한다.
 */
@Service
public class PlatformAccountService {

    public static final String CHZZK = "CHZZK";

    /** 문서상 Refresh Token 수명은 30일이며 응답에는 포함되지 않는다. */
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private static final Logger log = LoggerFactory.getLogger(PlatformAccountService.class);

    private final PlatformAccountMapper platformAccountMapper;
    private final PlatformLinkStateService linkStateService;
    private final PlatformTokenRefresher tokenRefresher;
    private final ChzzkOAuthClient chzzkOAuthClient;
    private final TokenCipher tokenCipher;
    private final Clock clock;

    public PlatformAccountService(
            PlatformAccountMapper platformAccountMapper,
            PlatformLinkStateService linkStateService,
            PlatformTokenRefresher tokenRefresher,
            ChzzkOAuthClient chzzkOAuthClient,
            TokenCipher tokenCipher,
            Clock clock
    ) {
        this.platformAccountMapper = platformAccountMapper;
        this.linkStateService = linkStateService;
        this.tokenRefresher = tokenRefresher;
        this.chzzkOAuthClient = chzzkOAuthClient;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
    }

    /**
     * 연동 주체와 복귀 주소를 state에 묶고 플랫폼 인증 URL을 만든다.
     */
    public String startLink(long streamerId, String platform, String returnUri) {
        requireSupported(platform);
        String state = linkStateService.create(streamerId, CHZZK, returnUri);
        return chzzkOAuthClient.buildAuthorizationUri(state);
    }

    /**
     * 콜백으로 받은 code를 토큰으로 바꾸고 채널 정보와 함께 저장한다.
     *
     * 외부 호출을 트랜잭션 밖에서 끝낸 뒤 단일 UPSERT로 저장한다.
     * 저장은 한 문장이라 원자적이고, 동일 채널 중복 연결은 유니크 제약이 막는다.
     */
    public CompletedLink completeLink(String state, String code, String error) {
        // state 소비 전 실패는 복귀 주소를 모르므로 일반 오류 응답으로 나간다.
        LinkState linkState = linkStateService.consume(state);
        try {
            return completeLink(linkState, state, code, error);
        } catch (PlatformException exception) {
            // 복귀 주소를 확보한 뒤의 실패는 클라이언트 화면으로 되돌려 보낸다.
            throw new PlatformCallbackException(linkState.returnUri(), exception);
        }
    }

    private CompletedLink completeLink(LinkState linkState, String state, String code, String error) {
        requireSupported(linkState.platform());
        if (error != null && !error.isBlank()) {
            log.info("플랫폼 연동 거부: platform={} error={}", linkState.platform(), error);
            throw PlatformException.invalidRequest("플랫폼 연동이 취소되었습니다.");
        }
        if (code == null || code.isBlank()) {
            throw PlatformException.invalidRequest("유효하지 않은 요청입니다.");
        }

        ChzzkTokenResponse token = chzzkOAuthClient.issueToken(code, state);
        ChzzkUser channel = fetchChannel(token.accessToken());

        Instant now = clock.instant();
        PlatformAccountUpsert upsert = new PlatformAccountUpsert(
                linkState.streamerId(),
                CHZZK,
                channel.channelId(),
                channel.channelName(),
                tokenCipher.encrypt(token.accessToken()),
                tokenCipher.encrypt(token.refreshToken()),
                now.plusSeconds(token.expiresIn()),
                now.plus(REFRESH_TOKEN_TTL),
                token.scope()
        );
        long platformAccountId = platformAccountMapper.upsert(upsert)
                // 다른 회원이 이미 연동한 채널이면 갱신 대상이 없어 빈 결과가 온다.
                .orElseThrow(PlatformException::alreadyLinked);

        return new CompletedLink(
                linkState.returnUri(),
                new PlatformAccount(
                        platformAccountId,
                        CHZZK,
                        channel.channelId(),
                        channel.channelName(),
                        "CONNECTED",
                        now
                )
        );
    }

    /** 내 연동 목록을 조회한다. 토큰은 조회하지 않는다. */
    public List<PlatformAccount> list(long streamerId) {
        return platformAccountMapper.findByStreamerId(streamerId);
    }

    /**
     * 연동을 삭제하고 플랫폼 쪽 승인도 정리한다.
     *
     * 삭제가 사용자 의도이므로 먼저 처리하고, 폐기 실패는 로그만 남긴다.
     * 이미 만료된 토큰을 폐기하려다 실패해도 연동 해제 자체는 성공해야 한다.
     */
    public void unlink(long streamerId, long platformAccountId) {
        PlatformAccountSecret secret = platformAccountMapper
                .findSecretByIdAndStreamerId(platformAccountId, streamerId)
                .orElseThrow(PlatformException::notFound);

        if (platformAccountMapper.delete(platformAccountId, streamerId) != 1) {
            throw PlatformException.notFound();
        }

        try {
            chzzkOAuthClient.revokeToken(tokenCipher.decrypt(secret.accessToken()), "access_token");
        } catch (ChzzkTokenRejectedException | PlatformException | IllegalStateException exception) {
            log.warn(
                    "플랫폼 토큰 폐기 실패(연동 해제는 완료): platformAccountId={} reason={}",
                    platformAccountId,
                    exception.toString()
            );
        }
    }

    /**
     * 플랫폼 API 호출에 쓸 유효한 Access Token을 돌려준다.
     * 만료가 임박했으면 갱신하고, 갱신이 불가능하면 재연동을 요구한다.
     */
    public String accessToken(long streamerId, long platformAccountId) {
        TokenResolution resolution = tokenRefresher.resolve(streamerId, platformAccountId);
        if (resolution.reauthRequired()) {
            throw PlatformException.reauthRequired();
        }
        return resolution.accessToken();
    }

    private ChzzkUser fetchChannel(String accessToken) {
        try {
            return chzzkOAuthClient.fetchMe(accessToken);
        } catch (ChzzkTokenRejectedException exception) {
            // 방금 발급받은 토큰이 거부되는 것은 사용자가 아니라 플랫폼 쪽 문제다.
            log.warn("발급 직후 Access Token이 거부되었습니다: {}", exception.getMessage());
            throw PlatformException.connectionFailed();
        }
    }

    private void requireSupported(String platform) {
        if (!CHZZK.equalsIgnoreCase(platform)) {
            throw PlatformException.invalidRequest("지원하지 않는 플랫폼입니다.");
        }
    }

    public record CompletedLink(String returnUri, PlatformAccount account) {
    }
}
