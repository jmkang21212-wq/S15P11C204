package com.example.laive.platform.youtube;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.core.ExternalBroadcast;
import com.example.laive.platform.core.PlatformCredentialRejectedException;
import com.example.laive.platform.core.PlatformLiveSetting;
import com.example.laive.platform.core.PlatformScopeInsufficientException;
import com.example.laive.platform.exception.PlatformException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
// HTTP 메시지 변환은 Jackson 3(tools.jackson)이 담당하므로 노드 타입도 그쪽 것이어야 한다.
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/** YouTube Data/Live Streaming API 응답을 플랫폼 공통 모델로 변환한다. */
@Component
public class YouTubeLiveClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeLiveClient.class);

    private final RestClient restClient;
    private final PlatformProperties.YouTube properties;

    public YouTubeLiveClient(
            @Qualifier("youtubeRestClient") RestClient restClient,
            PlatformProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties.youtube();
    }

    /**
     * 인증된 사용자의 진행 중인 방송을 찾는다.
     * <p>broadcastStatus·id·mine은 상호 배타적인 필터라 정확히 하나만 보낼 수 있다.
     * mine을 함께 보내면 incompatibleParameters로 항상 400이므로 붙이지 않는다 —
     * broadcastStatus 필터 자체가 인증된 사용자의 방송을 의미한다.
     * broadcastType은 생략하면 기본값이 event라서, OBS가 자동 생성하는 persistent
     * 방송이 조회에서 빠진다. 그래서 all로 지정한다.
     */
    public Optional<ExternalBroadcast> findActiveBroadcast(String accessToken) {
        try {
            BroadcastList broadcasts = restClient.get()
                    .uri(properties.apiBaseUrl()
                            + "/youtube/v3/liveBroadcasts?part=id,snippet,status"
                            + "&broadcastStatus=active&broadcastType=all")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(BroadcastList.class);
            if (broadcasts == null || broadcasts.items() == null || broadcasts.items().isEmpty()) {
                return Optional.empty();
            }
            Broadcast item = broadcasts.items().getFirst();
            VideoDetails details = videoDetails(accessToken, item.id()).orElse(null);
            String chatId = details == null || details.liveStreamingDetails() == null
                    ? null : details.liveStreamingDetails().activeLiveChatId();
            Instant startedAt = details == null || details.liveStreamingDetails() == null
                    ? null : details.liveStreamingDetails().actualStartTime();
            return Optional.of(new ExternalBroadcast(
                    item.id(),
                    item.snippet() == null ? null : item.snippet().title(),
                    "https://www.youtube.com/watch?v=" + item.id(),
                    chatId,
                    startedAt,
                    Map.of("providerStatus", item.status() == null ? "unknown" : item.status().lifeCycleStatus())
            ));
        } catch (RestClientResponseException exception) {
            // RestClientException의 하위 타입이라 반드시 먼저 잡아야 한다.
            // Outbox의 last_error에는 예외 클래스명만 남으므로 본문은 여기서만 볼 수 있다.
            log.warn("YouTube live discovery failed: status={} body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw PlatformException.connectionFailed();
        } catch (RestClientException exception) {
            log.warn("YouTube live discovery failed: {}", exception.getClass().getSimpleName());
            throw PlatformException.connectionFailed();
        }
    }

    public OptionalLong fetchViewerCount(String accessToken, String videoId) {
        return videoDetails(accessToken, videoId)
                .map(VideoDetails::liveStreamingDetails)
                .filter(details -> details != null && details.concurrentViewers() != null)
                .map(details -> {
                    try {
                        return OptionalLong.of(Long.parseLong(details.concurrentViewers()));
                    } catch (NumberFormatException exception) {
                        return OptionalLong.empty();
                    }
                })
                .orElseGet(OptionalLong::empty);
    }

    /**
     * 진행 중인 방송의 설정(제목)을 조회한다. 치지직과 달리 유튜브는 채널 단위
     * 기본 제목이 없어, 진행 중인 방송이 없으면 빈 제목을 돌려준다.
     */
    public PlatformLiveSetting fetchLiveSetting(String accessToken) {
        String title = activeBroadcastResource(accessToken)
                .map(resource -> resource.snippet().path("title").asString(""))
                .orElse("");
        return new PlatformLiveSetting(title, null, List.of());
    }

    /**
     * 진행 중인 방송의 제목을 변경한다. liveBroadcasts.update는 요청 바디에 없는
     * mutable 필드(description·scheduledStartTime 등)를 삭제하므로, 조회한 snippet을
     * 통째로 되돌려 보내되 title만 바꾼다. 진행 중인 방송이 없으면 조용히 건너뛴다 —
     * 제목 동기화는 best-effort고, OBS 송출 전에는 활성 방송이 존재하지 않는다.
     */
    public void updateLiveSetting(String accessToken, String title) {
        BroadcastResource broadcast = activeBroadcastResource(accessToken).orElse(null);
        if (broadcast == null) {
            log.info("YouTube title update skipped: no active broadcast");
            return;
        }
        broadcast.snippet().put("title", title);
        try {
            restClient.put()
                    .uri(properties.apiBaseUrl() + "/youtube/v3/liveBroadcasts?part=snippet")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(broadcast)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            log.warn("YouTube title update failed: status={} body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw translate(exception);
        } catch (RestClientException exception) {
            log.warn("YouTube title update failed: {}", exception.getClass().getSimpleName());
            throw PlatformException.connectionFailed();
        }
    }

    private Optional<VideoDetails> videoDetails(String accessToken, String videoId) {
        VideoList response = restClient.get()
                .uri(properties.apiBaseUrl() + "/youtube/v3/videos?part=liveStreamingDetails&id=" + videoId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(VideoList.class);
        if (response == null || response.items() == null || response.items().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.items().getFirst());
    }

    /** 설정 조회·변경 전용 활성 방송 조회다. discovery와 달리 videos.list를 부르지 않는다. */
    private Optional<BroadcastResource> activeBroadcastResource(String accessToken) {
        try {
            BroadcastResourceList list = restClient.get()
                    .uri(properties.apiBaseUrl()
                            + "/youtube/v3/liveBroadcasts?part=id,snippet"
                            + "&broadcastStatus=active&broadcastType=all")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(BroadcastResourceList.class);
            if (list == null || list.items() == null || list.items().isEmpty()
                    || list.items().getFirst().snippet() == null) {
                return Optional.empty();
            }
            return Optional.of(list.items().getFirst());
        } catch (RestClientResponseException exception) {
            log.warn("YouTube live setting lookup failed: status={} body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw translate(exception);
        } catch (RestClientException exception) {
            log.warn("YouTube live setting lookup failed: {}", exception.getClass().getSimpleName());
            throw PlatformException.connectionFailed();
        }
    }

    /**
     * 401은 토큰 자체가 거부된 것이고, 403은 토큰은 유효하나 연동 당시 승인받지
     * 않은 scope다 — 재연동으로만 해소된다.
     */
    private RuntimeException translate(RestClientResponseException exception) {
        if (exception.getStatusCode().value() == 401) {
            return new PlatformCredentialRejectedException("YouTube credential rejected", exception);
        }
        if (exception.getStatusCode().value() == 403) {
            return new PlatformScopeInsufficientException("YouTube scope insufficient", exception);
        }
        return PlatformException.connectionFailed();
    }

    record BroadcastList(List<Broadcast> items) {
    }

    record Broadcast(String id, BroadcastSnippet snippet, BroadcastStatus status) {
    }

    record BroadcastSnippet(String title) {
    }

    record BroadcastStatus(@JsonProperty("lifeCycleStatus") String lifeCycleStatus) {
    }

    record VideoList(List<VideoDetails> items) {
    }

    record VideoDetails(String id, LiveStreamingDetails liveStreamingDetails) {
    }

    record LiveStreamingDetails(
            Instant actualStartTime,
            Instant actualEndTime,
            String concurrentViewers,
            String activeLiveChatId
    ) {
    }

    /**
     * GET 응답 바인딩과 PUT 요청 바디를 겸한다. snippet을 ObjectNode로 받아 모든 필드를
     * 원본 그대로 보존한다 — liveBroadcasts.update는 요청에 없는 mutable 필드를 삭제한다.
     */
    record BroadcastResourceList(List<BroadcastResource> items) {
    }

    record BroadcastResource(String id, ObjectNode snippet) {
    }
}
