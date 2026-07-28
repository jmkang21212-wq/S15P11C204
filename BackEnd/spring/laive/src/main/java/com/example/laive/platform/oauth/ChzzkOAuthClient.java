package com.example.laive.platform.oauth;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.exception.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 치지직 Open API와 통신해 인증 URL 생성, 토큰 발급·갱신·폐기, 채널 조회를 수행한다.
 *
 * 네이버 로그인과 달리 토큰 요청 바디는 JSON이어야 하며
 * (form-urlencoded로 보내면 400을 반환한다) 성공 응답도 content로 감싸여 온다.
 * Client Secret과 토큰 원문은 로그에 남기지 않는다.
 */
@Component
public class ChzzkOAuthClient {

    private static final Logger log = LoggerFactory.getLogger(ChzzkOAuthClient.class);

    private static final ParameterizedTypeReference<ChzzkApiResponse<ChzzkTokenResponse>> TOKEN_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ChzzkApiResponse<ChzzkUser>> USER_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<ChzzkApiResponse<Object>> VOID_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;
    private final PlatformProperties.Chzzk properties;

    public ChzzkOAuthClient(
            @Qualifier("chzzkRestClient") RestClient chzzkRestClient,
            PlatformProperties properties
    ) {
        this.restClient = chzzkRestClient;
        this.properties = properties.chzzk();
    }

    /**
     * CSRF 방지용 state와 등록된 리디렉션 URL을 포함한 치지직 인증 URL을 만든다.
     * scope는 애플리케이션 등록 시 승인된 목록이 그대로 부여되므로 파라미터로 넘기지 않는다.
     */
    public String buildAuthorizationUri(String state) {
        requireConfigured();
        return properties.authorizationUri()
                + "?clientId=" + encode(properties.clientId())
                + "&redirectUri=" + encode(properties.redirectUri())
                + "&state=" + encode(state);
    }

    /**
     * authorization code를 Access Token과 Refresh Token으로 교환한다.
     */
    public ChzzkTokenResponse issueToken(String code, String state) {
        requireConfigured();
        TokenIssueRequest request = new TokenIssueRequest(
                "authorization_code",
                properties.clientId(),
                properties.clientSecret(),
                code,
                state
        );
        // 발급 단계의 401은 INVALID_CLIENT(설정 오류)이거나 code 위조이므로 재연동 대상이 아니다.
        return postForToken(properties.tokenUri(), request, "토큰 발급", false);
    }

    /**
     * Refresh Token으로 새 토큰 쌍을 발급받는다.
     * 치지직의 Refresh Token은 1회용이라 호출에 성공하면 이전 토큰 쌍은 즉시 무효가 된다.
     * 응답을 저장하지 못하면 연동이 끊기므로 호출자는 저장까지 한 트랜잭션으로 묶어야 한다.
     */
    public ChzzkTokenResponse refreshToken(String refreshToken) {
        requireConfigured();
        TokenRefreshRequest request = new TokenRefreshRequest(
                "refresh_token",
                refreshToken,
                properties.clientId(),
                properties.clientSecret()
        );
        // 갱신 단계의 401은 Refresh Token이 이미 소진·만료된 경우다.
        return postForToken(properties.tokenUri(), request, "토큰 갱신", true);
    }

    /**
     * 연동 해제 시 플랫폼 쪽 승인도 함께 정리한다.
     */
    public void revokeToken(String token, String tokenTypeHint) {
        requireConfigured();
        TokenRevokeRequest request = new TokenRevokeRequest(
                properties.clientId(),
                properties.clientSecret(),
                token,
                tokenTypeHint
        );
        Result<Object> result = post(properties.revokeUri(), request, VOID_TYPE);
        // 폐기 응답에는 content가 없으므로 상태 코드만 확인한다.
        if (!result.status().is2xxSuccessful()) {
            log.warn("치지직 토큰 폐기 실패: status={} detail={}", result.status(), result.describe());
            if (result.isUnauthorized()) {
                throw new ChzzkTokenRejectedException(result.describe());
            }
            throw PlatformException.connectionFailed();
        }
    }

    /**
     * Access Token으로 연동된 채널 정보를 조회한다.
     */
    public ChzzkUser fetchMe(String accessToken) {
        requireConfigured();
        Result<ChzzkUser> result = execute(
                restClient.get()
                        .uri(properties.userMeUri())
                        .header("Authorization", "Bearer " + accessToken)
                        .accept(MediaType.APPLICATION_JSON),
                USER_TYPE
        );
        // 조회 단계의 401은 Access Token 만료다.
        ChzzkUser user = requireContent(result, "유저 조회", true);
        if (isBlank(user.channelId())) {
            log.warn("치지직 유저 조회 응답에 channelId가 없습니다: detail={}", result.describe());
            throw PlatformException.connectionFailed();
        }
        return user;
    }

    private ChzzkTokenResponse postForToken(
            String uri,
            Object request,
            String operation,
            boolean rejectedOnUnauthorized
    ) {
        Result<ChzzkTokenResponse> result = post(uri, request, TOKEN_TYPE);
        ChzzkTokenResponse token = requireContent(result, operation, rejectedOnUnauthorized);
        if (isBlank(token.accessToken()) || isBlank(token.refreshToken())) {
            log.warn("치지직 {} 응답에 토큰이 없습니다: detail={}", operation, result.describe());
            throw PlatformException.connectionFailed();
        }
        return token;
    }

    private <T> Result<T> post(String uri, Object request, ParameterizedTypeReference<ChzzkApiResponse<T>> type) {
        return execute(
                restClient.post()
                        .uri(uri)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(request),
                type
        );
    }

    /**
     * 4xx도 예외로 만들지 않고 응답 바디를 읽어 치지직의 오류 코드를 확인한다.
     */
    private <T> Result<T> execute(
            RestClient.RequestHeadersSpec<?> spec,
            ParameterizedTypeReference<ChzzkApiResponse<T>> type
    ) {
        try {
            return spec.exchange((request, response) ->
                    new Result<>(response.getStatusCode(), response.bodyTo(type)));
        } catch (RestClientException | IllegalArgumentException exception) {
            // 타임아웃, 커넥션 실패, 역직렬화 실패는 원인만 남기고 502로 수렴시킨다.
            log.warn("치지직 API 호출 실패: {}", exception.toString());
            throw PlatformException.connectionFailed();
        }
    }

    private <T> T requireContent(Result<T> result, String operation, boolean rejectedOnUnauthorized) {
        if (rejectedOnUnauthorized && result.isUnauthorized()) {
            // 토큰이 죽었을 때의 사용자 응답(연동 실패 vs 재연동 필요)은 서비스가 판단한다.
            log.info("치지직 {} 중 토큰 거부: detail={}", operation, result.describe());
            throw new ChzzkTokenRejectedException(result.describe());
        }
        if (!result.status().is2xxSuccessful() || result.body() == null || !result.body().hasContent()) {
            log.warn("치지직 {} 실패: status={} detail={}", operation, result.status(), result.describe());
            throw PlatformException.connectionFailed();
        }
        return result.body().content();
    }

    private void requireConfigured() {
        // 설정 누락을 외부 호출 전에 감지해 일관된 외부 플랫폼 오류로 응답한다.
        if (isBlank(properties.clientId())
                || isBlank(properties.clientSecret())
                || isBlank(properties.redirectUri())) {
            log.warn("치지직 연동 설정(clientId/clientSecret/redirectUri)이 비어 있습니다.");
            throw PlatformException.connectionFailed();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 상태 코드와 파싱된 Envelope를 함께 들고 다닌다. */
    private record Result<T>(HttpStatusCode status, ChzzkApiResponse<T> body) {

        boolean isUnauthorized() {
            return status.value() == 401;
        }

        /** 로그용 요약. 치지직이 준 코드와 메시지만 담고 토큰은 포함하지 않는다. */
        String describe() {
            return body == null ? "empty body" : body.code() + " " + body.message();
        }
    }

    private record TokenIssueRequest(
            String grantType,
            String clientId,
            String clientSecret,
            String code,
            String state
    ) {
    }

    private record TokenRefreshRequest(
            String grantType,
            String refreshToken,
            String clientId,
            String clientSecret
    ) {
    }

    private record TokenRevokeRequest(
            String clientId,
            String clientSecret,
            String token,
            String tokenTypeHint
    ) {
    }
}
