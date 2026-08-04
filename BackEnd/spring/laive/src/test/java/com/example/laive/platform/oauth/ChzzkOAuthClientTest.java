package com.example.laive.platform.oauth;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.exception.PlatformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 치지직 Open API 응답 형태(공통 Envelope, 숫자 expiresIn, 한글 scope)를 기준으로
 * 클라이언트의 요청 형식과 오류 해석을 검증한다.
 */
class ChzzkOAuthClientTest {

    private static final String TOKEN_URI = "https://openapi.chzzk.naver.com/auth/v1/token";
    private static final String REVOKE_URI = "https://openapi.chzzk.naver.com/auth/v1/token/revoke";
    private static final String USER_ME_URI = "https://openapi.chzzk.naver.com/open/v1/users/me";
    private static final String LIVE_SETTING_URI = "https://openapi.chzzk.naver.com/open/v1/lives/setting";

    private static final String SCOPE = "채널 정보 조회 채널 관리자 조회 유저 조회";

    private static final String TOKEN_RESPONSE = """
            {
              "code": 200,
              "message": null,
              "content": {
                "refreshToken": "refresh-token-value",
                "accessToken": "access-token-value",
                "tokenType": "Bearer",
                "expiresIn": 86400,
                "scope": "%s"
              }
            }
            """.formatted(SCOPE);

    private static final String USER_RESPONSE = """
            {
              "code": 200,
              "message": null,
              "content": {
                "channelId": "a253006fe48c3ecbecd3723c8475c6f1",
                "channelName": "폭주하는 레전드 820",
                "nickname": "폭주하는 레전드 820"
              }
            }
            """;

    private static final String LIVE_SETTING_RESPONSE = """
            {
              "code": 200,
              "message": null,
              "content": {
                "defaultLiveTitle": "오늘의 방송 제목",
                "category": {
                  "categoryType": "GAME",
                  "categoryId": "League_of_Legends",
                  "categoryValue": "리그 오브 레전드",
                  "posterImageUrl": "https://example.com/poster.png"
                },
                "tags": ["태그1", "태그2"]
              }
            }
            """;

    private MockRestServiceServer server;
    private ChzzkOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ChzzkOAuthClient(builder.build(), properties("client-id", "client-secret"));
    }

    @Test
    void encodesRedirectUriWhenBuildingAuthorizationUri() {
        String authorizationUri = client.buildAuthorizationUri("state-value");

        assertThat(authorizationUri)
                .startsWith("https://chzzk.naver.com/account-interlock?")
                .contains("clientId=client-id")
                .contains(
                        "redirectUri=http%3A%2F%2Flocalhost%3A8080"
                                + "%2Fapi%2Fv1%2Fplatform-accounts%2Fchzzk%2Fcallback"
                )
                .contains("state=state-value")
                // scope는 애플리케이션 등록 시 승인된 목록이 그대로 부여된다.
                .doesNotContain("scope=");
    }

    @Test
    void sendsJsonBodyWhenIssuingToken() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                // form-urlencoded로 보내면 치지직이 400을 반환하므로 JSON이어야 한다.
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.grantType").value("authorization_code"))
                .andExpect(jsonPath("$.clientId").value("client-id"))
                .andExpect(jsonPath("$.clientSecret").value("client-secret"))
                .andExpect(jsonPath("$.code").value("auth-code"))
                .andExpect(jsonPath("$.state").value("state-value"))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        ChzzkTokenResponse token = client.issueToken("auth-code", "state-value");

        server.verify();
        assertThat(token.accessToken()).isEqualTo("access-token-value");
        assertThat(token.refreshToken()).isEqualTo("refresh-token-value");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(86400L);
        assertThat(token.scope()).isEqualTo(SCOPE);
    }

    @Test
    void mapsInvalidClientToConnectionFailureWhenIssuingToken() {
        // 발급 단계의 401은 설정 오류이지 사용자 재연동 대상이 아니다.
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":401,\"message\":\"INVALID_CLIENT\"}"));

        assertThatThrownBy(() -> client.issueToken("auth-code", "state-value"))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getCode())
                .isEqualTo("PLATFORM_CONNECTION_FAILED");
    }

    @Test
    void sendsRefreshGrantWhenRefreshingToken() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(jsonPath("$.grantType").value("refresh_token"))
                .andExpect(jsonPath("$.refreshToken").value("old-refresh-token"))
                .andExpect(jsonPath("$.clientId").value("client-id"))
                .andRespond(withSuccess(TOKEN_RESPONSE, MediaType.APPLICATION_JSON));

        ChzzkTokenResponse token = client.refreshToken("old-refresh-token");

        server.verify();
        assertThat(token.refreshToken()).isEqualTo("refresh-token-value");
    }

    @Test
    void mapsInvalidTokenToRejectionWhenRefreshing() {
        // Refresh Token은 1회용이라 소진된 값으로 갱신하면 401 INVALID_TOKEN이 온다.
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":401,\"message\":\"INVALID_TOKEN\"}"));

        assertThatThrownBy(() -> client.refreshToken("spent-refresh-token"))
                .isInstanceOf(ChzzkTokenRejectedException.class);
    }

    @Test
    void sendsBearerTokenWhenFetchingChannel() {
        server.expect(requestTo(USER_ME_URI))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token-value"))
                .andRespond(withSuccess(USER_RESPONSE, MediaType.APPLICATION_JSON));

        ChzzkUser user = client.fetchMe("access-token-value");

        server.verify();
        assertThat(user.channelId()).isEqualTo("a253006fe48c3ecbecd3723c8475c6f1");
        assertThat(user.channelName()).isEqualTo("폭주하는 레전드 820");
        assertThat(user.nickname()).isEqualTo("폭주하는 레전드 820");
    }

    @Test
    void mapsInvalidTokenToRejectionWhenFetchingChannel() {
        server.expect(requestTo(USER_ME_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":401,\"message\":\"INVALID_TOKEN\"}"));

        assertThatThrownBy(() -> client.fetchMe("expired-access-token"))
                .isInstanceOf(ChzzkTokenRejectedException.class);
    }

    @Test
    void mapsQuotaExceededToConnectionFailure() {
        server.expect(requestTo(USER_ME_URI))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":429,\"message\":\"Quota 제한 초과\"}"));

        assertThatThrownBy(() -> client.fetchMe("access-token-value"))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getStatus().value())
                .isEqualTo(502);
    }

    @Test
    void sendsClientCredentialsWhenRevoking() {
        server.expect(requestTo(REVOKE_URI))
                .andExpect(jsonPath("$.clientId").value("client-id"))
                .andExpect(jsonPath("$.token").value("access-token-value"))
                .andExpect(jsonPath("$.tokenTypeHint").value("access_token"))
                .andRespond(withSuccess("{\"code\":200,\"message\":null}", MediaType.APPLICATION_JSON));

        client.revokeToken("access-token-value", "access_token");

        server.verify();
    }

    @Test
    void sendsBearerTokenWhenFetchingLiveSetting() {
        server.expect(requestTo(LIVE_SETTING_URI))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token-value"))
                .andRespond(withSuccess(LIVE_SETTING_RESPONSE, MediaType.APPLICATION_JSON));

        ChzzkLiveSetting setting = client.fetchLiveSetting("access-token-value");

        server.verify();
        assertThat(setting.defaultLiveTitle()).isEqualTo("오늘의 방송 제목");
        assertThat(setting.category().categoryType()).isEqualTo("GAME");
        assertThat(setting.category().categoryValue()).isEqualTo("리그 오브 레전드");
        assertThat(setting.tags()).containsExactly("태그1", "태그2");
    }

    @Test
    void mapsForbiddenToScopeInsufficientWhenFetchingLiveSetting() {
        // 방송 설정 권한이 앱에 추가되기 전에 발급된 토큰은 403을 받는다.
        server.expect(requestTo(LIVE_SETTING_URI))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":403,\"message\":\"FORBIDDEN\"}"));

        assertThatThrownBy(() -> client.fetchLiveSetting("old-scope-token"))
                .isInstanceOf(ChzzkScopeInsufficientException.class);
    }

    @Test
    void mapsInvalidTokenToRejectionWhenFetchingLiveSetting() {
        server.expect(requestTo(LIVE_SETTING_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":401,\"message\":\"INVALID_TOKEN\"}"));

        assertThatThrownBy(() -> client.fetchLiveSetting("expired-access-token"))
                .isInstanceOf(ChzzkTokenRejectedException.class);
    }

    @Test
    void sendsPatchJsonWhenUpdatingLiveSetting() {
        server.expect(requestTo(LIVE_SETTING_URI))
                .andExpect(method(org.springframework.http.HttpMethod.PATCH))
                .andExpect(header("Authorization", "Bearer access-token-value"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.defaultLiveTitle").value("새 방송 제목"))
                // 변경 성공 응답에는 content가 없다.
                .andRespond(withSuccess("{\"code\":200,\"message\":null}", MediaType.APPLICATION_JSON));

        client.updateLiveSetting("access-token-value", "새 방송 제목");

        server.verify();
    }

    @Test
    void mapsForbiddenToScopeInsufficientWhenUpdatingLiveSetting() {
        server.expect(requestTo(LIVE_SETTING_URI))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":403,\"message\":\"FORBIDDEN\"}"));

        assertThatThrownBy(() -> client.updateLiveSetting("old-scope-token", "새 방송 제목"))
                .isInstanceOf(ChzzkScopeInsufficientException.class);
    }

    @Test
    void mapsInvalidTokenToRejectionWhenUpdatingLiveSetting() {
        server.expect(requestTo(LIVE_SETTING_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":401,\"message\":\"INVALID_TOKEN\"}"));

        assertThatThrownBy(() -> client.updateLiveSetting("expired-access-token", "새 방송 제목"))
                .isInstanceOf(ChzzkTokenRejectedException.class);
    }

    @Test
    void mapsServerErrorToConnectionFailureWhenUpdatingLiveSetting() {
        server.expect(requestTo(LIVE_SETTING_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":500,\"message\":\"INTERNAL_SERVER_ERROR\"}"));

        assertThatThrownBy(() -> client.updateLiveSetting("access-token-value", "새 방송 제목"))
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).getStatus().value())
                .isEqualTo(502);
    }

    @Test
    void failsBeforeCallingPlatformWhenNotConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer unusedServer = MockRestServiceServer.bindTo(builder).build();
        ChzzkOAuthClient unconfigured = new ChzzkOAuthClient(builder.build(), properties("", ""));

        assertThatThrownBy(() -> unconfigured.buildAuthorizationUri("state-value"))
                .isInstanceOf(PlatformException.class);

        // 설정이 비어 있으면 외부 호출 자체가 일어나지 않아야 한다.
        unusedServer.verify();
    }

    private PlatformProperties properties(String clientId, String clientSecret) {
        return new PlatformProperties(
                new PlatformProperties.Chzzk(
                        clientId,
                        clientSecret,
                        "http://localhost:8080/api/v1/platform-accounts/chzzk/callback",
                        "https://chzzk.naver.com/account-interlock",
                        TOKEN_URI,
                        REVOKE_URI,
                        USER_ME_URI,
                        LIVE_SETTING_URI
                ),
                "dGVzdC1rZXktMzItYnl0ZXMtZm9yLXVuaXQtdGVzdHMh",
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        );
    }
}
