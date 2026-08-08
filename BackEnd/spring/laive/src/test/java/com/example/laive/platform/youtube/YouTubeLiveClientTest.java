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

    // ─────────────────────────── 방송 켜기 (BroadcastLaunchCapability) ───────────────────────────

    private static final String ACTIVE_URI = "https://www.googleapis.com/youtube/v3/liveBroadcasts"
            + "?part=id,contentDetails&broadcastStatus=active&broadcastType=all";
    private static final String UPCOMING_URI = "https://www.googleapis.com/youtube/v3/liveBroadcasts"
            + "?part=id,contentDetails&broadcastStatus=upcoming&broadcastType=all";
    private static final String STREAM_LIST_URI = "https://www.googleapis.com/youtube/v3/liveStreams"
            + "?part=id,cdn,status&mine=true";
    private static final String STREAM_STATUS_URI = "https://www.googleapis.com/youtube/v3/liveStreams"
            + "?part=id,status&id=stream-id";
    private static final String INSERT_BROADCAST_URI = "https://www.googleapis.com/youtube/v3/liveBroadcasts"
            + "?part=snippet,status,contentDetails";

    private static final String ONE_ITEM_RESPONSE = "{\"items\": [{\"id\": \"existing\"}]}";
    private static final String RTMP_STREAM_RESPONSE =
            "{\"items\": [{\"id\": \"stream-id\", \"cdn\": {\"ingestionType\": \"rtmp\"}}]}";
    /** 송출 전 상태. transition은 이때 시도하지 않아야 한다. */
    private static final String STREAM_INACTIVE_RESPONSE =
            "{\"items\": [{\"id\": \"stream-id\", \"status\": {\"streamStatus\": \"inactive\"}}]}";

    /** 자동 시작이 켜져 있고 우리 스트림에 묶인 방송 — 재사용해도 되는 유일한 형태다. */
    private static final String USABLE_UPCOMING_RESPONSE = """
            {"items": [{"id": "usable", "contentDetails":
                {"boundStreamId": "stream-id", "enableAutoStart": true}}]}
            """;

    /**
     * 실제로 겪은 장애를 고정한다. OBS 계정 연동 모드가 만들어 둔 방송은
     * enableAutoStart가 false인데, "대기 방송이 있으면 재사용"으로 판정하면 이것 하나
     * 때문에 방송이 영구히 켜지지 않는다 — 송출이 와도 그 방송은 ready에 머물고
     * 우리는 손을 놓기 때문이다. 그래서 <b>새로 만들어야</b> 한다.
     */
    @Test
    void createsNewBroadcastWhenExistingOneHasAutoStartOff() {
        server.expect(requestTo(ACTIVE_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_LIST_URI))
                .andRespond(withSuccess(RTMP_STREAM_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPCOMING_URI))
                .andRespond(withSuccess("""
                        {"items": [{"id": "stale", "contentDetails":
                            {"boundStreamId": "stream-id", "enableAutoStart": false}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(INSERT_BROADCAST_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\": \"new-broadcast\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.googleapis.com/youtube/v3/liveBroadcasts/bind"
                        + "?part=id,contentDetails&id=new-broadcast&streamId=stream-id"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_STATUS_URI))
                .andRespond(withSuccess(STREAM_INACTIVE_RESPONSE, MediaType.APPLICATION_JSON));

        client.ensureLaunched("access-token-value", "제목");

        server.verify();
    }

    /** 다른 스트림에 묶인 방송도 재사용해선 안 된다 — 우리 송출이 그 방송에 닿지 않는다. */
    @Test
    void createsNewBroadcastWhenExistingOneIsBoundToAnotherStream() {
        server.expect(requestTo(ACTIVE_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_LIST_URI))
                .andRespond(withSuccess(RTMP_STREAM_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPCOMING_URI))
                .andRespond(withSuccess("""
                        {"items": [{"id": "other", "contentDetails":
                            {"boundStreamId": "another-stream", "enableAutoStart": true}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(INSERT_BROADCAST_URI))
                .andRespond(withSuccess("{\"id\": \"new-broadcast\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.googleapis.com/youtube/v3/liveBroadcasts/bind"
                        + "?part=id,contentDetails&id=new-broadcast&streamId=stream-id"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_STATUS_URI))
                .andRespond(withSuccess(STREAM_INACTIVE_RESPONSE, MediaType.APPLICATION_JSON));

        client.ensureLaunched("access-token-value", "제목");

        server.verify();
    }

    /**
     * Outbox가 최대 8회 재시도한다. 이미 라이브면 아무 요청도 더 나가면 안 된다 —
     * 나가면 재시도 횟수만큼 빈 방송이 채널에 쌓인다.
     */
    @Test
    void doesNothingWhenBroadcastIsAlreadyLive() {
        server.expect(requestTo(ACTIVE_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ONE_ITEM_RESPONSE, MediaType.APPLICATION_JSON));

        client.ensureLaunched("access-token-value", "제목");

        // 스트림 조회도, insert도 나가지 않아야 한다. verify가 그것을 보장한다.
        server.verify();
    }

    private static final String BY_ID_URI = "https://www.googleapis.com/youtube/v3/liveBroadcasts"
            + "?part=id,snippet&id=usable";

    /**
     * 쓸 수 있는 대기 방송은 재사용한다 — 이것이 멱등성을 지키는 축이다.
     *
     * <p>그리고 <b>재사용할 때 제목을 세션 제목으로 맞춰야 한다.</b> 실제로 이걸 빠뜨려서
     * 유튜브에 이전 세션의 제목("테스트 방송")이 그대로 나갔다. insert 경로에는 title을
     * 실어 보내지만 재사용 경로에는 그럴 기회가 없다.
     */
    @Test
    void alignsTitleWhenReusingUpcomingBroadcast() {
        server.expect(requestTo(ACTIVE_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_LIST_URI))
                .andRespond(withSuccess(RTMP_STREAM_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPCOMING_URI))
                .andRespond(withSuccess(USABLE_UPCOMING_RESPONSE, MediaType.APPLICATION_JSON));
        // insert는 나가면 안 된다. 대신 제목을 맞추러 조회 → PUT이 나간다.
        server.expect(requestTo(BY_ID_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"items": [{"id": "usable", "snippet":
                            {"title": "이전 세션 제목", "description": "기존 설명",
                             "scheduledStartTime": "2026-08-07T09:00:00Z"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPDATE_URI))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.snippet.title").value("새 세션 제목"))
                // snippet 원본이 보존돼야 한다 — 빠진 mutable 필드는 유튜브가 삭제한다.
                .andExpect(jsonPath("$.snippet.description").value("기존 설명"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_STATUS_URI))
                .andRespond(withSuccess(STREAM_INACTIVE_RESPONSE, MediaType.APPLICATION_JSON));

        client.ensureLaunched("access-token-value", "새 세션 제목");

        server.verify();
    }

    /** 제목이 이미 같으면 불필요한 PUT을 보내지 않는다 — 재시도마다 update를 부르지 않기 위해서다. */
    @Test
    void skipsTitleAlignWhenAlreadyMatching() {
        server.expect(requestTo(ACTIVE_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_LIST_URI))
                .andRespond(withSuccess(RTMP_STREAM_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPCOMING_URI))
                .andRespond(withSuccess(USABLE_UPCOMING_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BY_ID_URI))
                .andRespond(withSuccess(
                        "{\"items\": [{\"id\": \"usable\", \"snippet\": {\"title\": \"같은 제목\"}}]}",
                        MediaType.APPLICATION_JSON));
        // UPDATE_URI가 나가면 안 된다.
        server.expect(requestTo(STREAM_STATUS_URI))
                .andRespond(withSuccess(STREAM_INACTIVE_RESPONSE, MediaType.APPLICATION_JSON));

        client.ensureLaunched("access-token-value", "같은 제목");

        server.verify();
    }

    /**
     * 송출이 들어오고 있으면 우리 방송을 지목해 라이브로 전환한다. 자동 시작만 믿으면
     * 같은 스트림에 방송이 여럿일 때 어느 것이 켜질지 정해지지 않는다.
     */
    @Test
    void transitionsToLiveWhenIngestIsAlreadyActive() {
        server.expect(requestTo(ACTIVE_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_LIST_URI))
                .andRespond(withSuccess(RTMP_STREAM_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPCOMING_URI))
                .andRespond(withSuccess(USABLE_UPCOMING_RESPONSE, MediaType.APPLICATION_JSON));
        // 재사용 경로라 제목 확인이 먼저 나간다 (이미 같아서 PUT은 없다).
        server.expect(requestTo(BY_ID_URI))
                .andRespond(withSuccess(
                        "{\"items\": [{\"id\": \"usable\", \"snippet\": {\"title\": \"제목\"}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_STATUS_URI))
                .andRespond(withSuccess(
                        "{\"items\": [{\"id\": \"stream-id\", \"status\": {\"streamStatus\": \"active\"}}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://www.googleapis.com/youtube/v3/liveBroadcasts/transition"
                        + "?part=id,status&broadcastStatus=live&id=usable"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.ensureLaunched("access-token-value", "제목");

        server.verify();
    }

    /**
     * 방송이 하나도 없을 때 만든다. 기존 스트림을 재사용하는 것이 중요하다 —
     * 새로 만들면 키가 달라져서 사용자가 OBS에 넣어 둔 키로는 송출이 도달하지 못한다.
     */
    @Test
    void createsBroadcastBoundToExistingStreamWithAutoStart() {
        server.expect(requestTo(ACTIVE_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_LIST_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(RTMP_STREAM_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(UPCOMING_URI))
                .andRespond(withSuccess(EMPTY_RESPONSE, MediaType.APPLICATION_JSON));
        server.expect(requestTo(INSERT_BROADCAST_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.snippet.title").value("오늘의 방송"))
                // 이 두 값이 없으면 송출이 들어와도 라이브로 전환되지 않는다.
                .andExpect(jsonPath("$.contentDetails.enableAutoStart").value(true))
                .andExpect(jsonPath("$.contentDetails.enableAutoStop").value(true))
                .andExpect(jsonPath("$.status.privacyStatus").value("unlisted"))
                .andRespond(withSuccess("{\"id\": \"new-broadcast\"}", MediaType.APPLICATION_JSON));
        // 바인딩을 빠뜨리면 송출이 들어와도 방송이 반응하지 않는다.
        server.expect(requestTo("https://www.googleapis.com/youtube/v3/liveBroadcasts/bind"
                        + "?part=id,contentDetails&id=new-broadcast&streamId=stream-id"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(STREAM_STATUS_URI))
                .andRespond(withSuccess(STREAM_INACTIVE_RESPONSE, MediaType.APPLICATION_JSON));

        client.ensureLaunched("access-token-value", "오늘의 방송");

        server.verify();
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
                        "https://www.googleapis.com",
                        "unlisted"
                ),
                "encryption-key",
                null,
                null
        );
    }
}
