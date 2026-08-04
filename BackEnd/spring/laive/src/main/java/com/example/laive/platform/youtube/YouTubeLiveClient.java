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

    public Optional<ExternalBroadcast> findActiveBroadcast(String accessToken) {
        try {
            BroadcastList broadcasts = restClient.get()
                    .uri(properties.apiBaseUrl()
                            + "/youtube/v3/liveBroadcasts?part=id,snippet,status&mine=true&broadcastStatus=active")
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
