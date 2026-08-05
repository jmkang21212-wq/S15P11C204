package com.example.laive.platform.youtube;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.core.PlatformCredentialRejectedException;
import com.example.laive.platform.core.PlatformLiveSetting;
import com.example.laive.platform.core.PlatformScopeInsufficientException;
import com.example.laive.platform.exception.PlatformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * liveBroadcasts.update의 조회-후-echo 요청 형식과 오류 해석을 검증한다.
 * update는 요청 바디에 없는 mutable 필드를 삭제하므로 snippet 원본 보존이 핵심이다.
 */
class YouTubeLiveClientTest {

    private static final String LIST_URI = "https://www.googleapis.com/youtube/v3/liveBroadcasts"
            + "?part=id,snippet&broadcastStatus=active&broadcastType=all";
    private static final String UPDATE_URI = "https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet";

    private static final String ACTIVE_BROADCAST_RESPONSE = """
            {
              "items": [
                {
                  "id": "broadcast-id",
                  "snippet": {
                    "publishedAt": "2026-08-05T00:00:00Z",
                    "channelId": "channel-id",
                    "title": "이전 제목",
                    "description": "기존 설명",
                    "scheduledStartTime": "2026-08-05T09:00:00Z",
                    "isDefaultBroadcast": true
                  }
                }
              ]
            }
            """;

    private static final String EMPTY_RESPONSE = "{\"items\": []}";

    private MockRestServiceServer server;
    private YouTubeLiveClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YouTubeLiveClient(builder.build(), properties());
    }

    @Test
    void updatesTitleWithSnippetEchoWhenActiveBroadcastExists() {
        server.expect(requestTo(LIST_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token-value"))
                .andRespond(withSuccess(ACTIVE_BROADCAST_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPDATE_URI))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer access-token-value"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.id").value("broadcast-id"))
                .andExpect(jsonPath("$.snippet.title").value("새 방송 제목"))
                // 요청에 없는 mutable 필드는 삭제되므로 조회한 snippet이 그대로 돌아가야 한다.
                .andExpect(jsonPath("$.snippet.description").value("기존 설명"))
                .andExpect(jsonPath("$.snippet.scheduledStartTime").value("2026-08-05T09:00:00Z"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.updateLiveSetting("access-token-value", "새 방송 제목");

        server.verify();
    }

    @Test
    void skipsUpdateWhenNoActiveBroadcast() {
        // OBS 송출 전이면 활성 방송이 없다 — PUT 없이 조용히 반환해야 한다.
        server.expect(requestTo(LIST_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));

        client.updateLiveSetting("access-token-value", "새 방송 제목");

        server.verify();
    }

    @Test
    void mapsForbiddenPutToScopeInsufficient() {
        // 구 readonly 토큰은 조회는 통과하고 쓰기에서 403이 난다.
        server.expect(requestTo(LIST_URI))
                .andRespond(withSuccess(ACTIVE_BROADCAST_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPDATE_URI))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"code\": 403, \"errors\": [{\"reason\": \"insufficientPermissions\"}]}}"));

        assertThatThrownBy(() -> client.updateLiveSetting("old-scope-token", "새 방송 제목"))
                .isInstanceOf(PlatformScopeInsufficientException.class);
    }

    @Test
    void mapsUnauthorizedToCredentialRejected() {
        server.expect(requestTo(LIST_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"code\": 401}}"));

        assertThatThrownBy(() -> client.updateLiveSetting("revoked-token", "새 방송 제목"))
                .isInstanceOf(PlatformCredentialRejectedException.class);
    }

    @Test
    void mapsServerErrorToConnectionFailure() {
        server.expect(requestTo(LIST_URI))
                .andRespond(withSuccess(ACTIVE_BROADCAST_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPDATE_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.updateLiveSetting("access-token-value", "새 방송 제목"))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PLATFORM_CONNECTION_FAILED"));
    }

    @Test
    void fetchesTitleOfActiveBroadcast() {
        server.expect(requestTo(LIST_URI))
                .andExpect(header("Authorization", "Bearer access-token-value"))
                .andRespond(withSuccess(ACTIVE_BROADCAST_RESPONSE, MediaType.APPLICATION_JSON));

        PlatformLiveSetting setting = client.fetchLiveSetting("access-token-value");

        assertThat(setting.defaultLiveTitle()).isEqualTo("이전 제목");
        assertThat(setting.category()).isNull();
        assertThat(setting.tags()).isEmpty();
    }

    @Test
    void returnsEmptySettingWhenNoActiveBroadcast() {
        // 유튜브는 방송 단위 설정이라 진행 중 방송이 없으면 돌려줄 제목도 없다.
        server.expect(requestTo(LIST_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));

        PlatformLiveSetting setting = client.fetchLiveSetting("access-token-value");

        assertThat(setting.defaultLiveTitle()).isEmpty();
        assertThat(setting.category()).isNull();
        assertThat(setting.tags()).isEmpty();
    }

    private PlatformProperties properties() {
        return new PlatformProperties(
                null,
                new PlatformProperties.YouTube(
                        "client-id",
                        "client-secret",
                        "http://localhost:8080/api/v1/platform-accounts/youtube/callback",
                        "https://accounts.google.com/o/oauth2/v2/auth",
                        "https://oauth2.googleapis.com/token",
                        "https://oauth2.googleapis.com/revoke",
                        "https://www.googleapis.com"
                ),
                "encryption-key",
                null,
                null
        );
    }
}
