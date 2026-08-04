package com.example.laive.platform.youtube;

import com.example.laive.platform.config.PlatformProperties;
import com.example.laive.platform.core.ExternalBroadcast;
import com.example.laive.platform.exception.PlatformException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
}
