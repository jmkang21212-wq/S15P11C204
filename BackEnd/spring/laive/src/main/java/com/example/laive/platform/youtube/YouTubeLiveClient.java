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
        putTitle(accessToken, broadcast, title);
    }

    /**
     * 조회한 snippet을 통째로 되돌려 보내되 title만 바꾼다.
     *
     * <p>liveBroadcasts.update는 요청 바디에 없는 mutable 필드(description·
     * scheduledStartTime 등)를 <b>삭제한다.</b> 그래서 title만 담아 보내면 설명과 예약
     * 시각이 날아간다 — snippet 원본 보존이 이 메서드의 핵심이다.
     */
    private void putTitle(String accessToken, BroadcastResource broadcast, String title) {
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

    /**
     * 재사용하는 방송의 제목을 세션 제목으로 맞춘다.
     *
     * <p>필요한 이유 — 새로 만들 때는 insert에 title을 실어 보내지만, <b>재사용 경로에는
     * 그럴 기회가 없다.</b> 이걸 빠뜨려서 실제로 방송 제목이 이전 세션의 것으로 남았다.
     * discovery 이후의 {@code syncBroadcastTitle}이 한 번 더 시도하기는 하지만, 그쪽은
     * "활성 방송"만 대상이라 아직 대기 중인 방송에는 닿지 않는다.
     *
     * <p>실패해도 방송 시작을 막지 않는다. 제목이 틀린 채로라도 방송은 켜지는 편이 낫다.
     */
    private void alignTitle(String accessToken, String broadcastId, String title) {
        try {
            BroadcastResource broadcast = broadcastResourceById(accessToken, broadcastId).orElse(null);
            if (broadcast == null || broadcast.snippet() == null) {
                return;
            }
            if (title.equals(broadcast.snippet().path("title").asString(""))) {
                return;
            }
            putTitle(accessToken, broadcast, title);
            log.info("YouTube reused broadcast title aligned: broadcastId={}", broadcastId);
        } catch (RuntimeException exception) {
            log.warn("YouTube reused broadcast title align failed: broadcastId={} cause={}",
                    broadcastId, exception.getClass().getSimpleName());
        }
    }

    private Optional<BroadcastResource> broadcastResourceById(String accessToken, String broadcastId) {
        BroadcastResourceList list = restClient.get()
                .uri(properties.apiBaseUrl()
                        + "/youtube/v3/liveBroadcasts?part=id,snippet&id=" + broadcastId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(BroadcastResourceList.class);
        if (list == null || list.items() == null || list.items().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.items().getFirst());
    }

    /**
     * 송출이 들어오면 라이브가 되도록 방송을 준비한다 (BroadcastLaunchCapability).
     *
     * <p>유튜브는 ingest만으로 방송이 생기지 않는다. liveBroadcast 리소스가 있고 그것이
     * liveStream(= 사용자가 OBS에 넣은 스트림 키)에 묶여 있어야 하며, enableAutoStart가
     * 켜져 있어야 송출 순간 자동으로 라이브가 된다. 스튜디오 UI에서는 이 옵션이 예약
     * 방송에만 붙어 있지만, API로 만들 때는 예약 절차 없이 직접 지정할 수 있다.
     *
     * <p><b>멱등성</b>이 이 메서드의 첫 제약이다. Outbox가 최대 8회 재시도하는데, 준비
     * 직후에는 아직 송출이 없어 discovery가 실패하므로 이 메서드는 거의 확실히 여러 번
     * 불린다. 호출마다 방송을 만들면 채널에 빈 방송이 쌓인다.
     *
     * <p>다만 "방송이 존재하는가"로 건너뛰면 안 된다 — 그렇게 했더니 <b>자동 시작이 꺼진
     * 옛 방송</b>(OBS 계정 연동 모드가 만들어 둔 것) 하나 때문에 영구히 막혔다. 송출이
     * 들어와도 그 방송은 ready에 머물고, 우리는 "이미 있다"며 손을 놓아 아무도 라이브가
     * 되지 않는다. 그래서 <b>쓸 수 있는 방송</b>만 인정한다 — 자동 시작이 켜져 있고 우리
     * 스트림에 묶인 것.
     *
     * <p>마지막으로 명시적 transition을 시도한다. 같은 스트림에 방송이 여럿 묶여 있으면
     * 자동 시작만으로는 어느 것이 라이브가 될지 정해지지 않기 때문이다. 송출이 아직
     * 없으면 실패하는데, 그건 정상이라 삼키고 다음 재시도에 맡긴다.
     */
    public void ensureLaunched(String accessToken, String title) {
        if (activeBroadcastId(accessToken).isPresent()) {
            log.info("YouTube broadcast launch skipped: already live");
            return;
        }
        try {
            // 스트림은 반드시 기존 것을 재사용한다. 새로 만들면 키가 달라지는데, 사용자가
            // OBS에 넣어 둔 키는 예전 것이라 송출이 이 방송에 도달하지 못한다.
            Optional<String> existingStream = findReusableStreamId(accessToken);
            // 스트림이 없으면 묶인 방송도 있을 수 없으므로 재사용 검사를 건너뛴다.
            String broadcastId = existingStream
                    .flatMap(streamId -> usableBroadcastId(accessToken, streamId))
                    .orElse(null);
            String streamId = existingStream.orElseGet(() -> insertStream(accessToken));
            if (broadcastId == null) {
                broadcastId = insertBroadcast(accessToken, title);
                bind(accessToken, broadcastId, streamId);
                log.info("YouTube broadcast created: broadcastId={} streamId={}", broadcastId, streamId);
            } else {
                log.info("YouTube broadcast reused: broadcastId={}", broadcastId);
                // 재사용한 방송은 이전 세션의 제목을 들고 있다. 여기서 맞추지 않으면
                // 유튜브에 옛 제목이 그대로 나간다.
                alignTitle(accessToken, broadcastId, title);
            }
            transitionToLive(accessToken, broadcastId, streamId);
        } catch (RestClientResponseException exception) {
            log.warn("YouTube broadcast launch failed: status={} body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw translate(exception);
        } catch (RestClientException exception) {
            log.warn("YouTube broadcast launch failed: {}", exception.getClass().getSimpleName());
            throw PlatformException.connectionFailed();
        }
    }

    /**
     * 송출이 이미 들어오고 있으면 방송을 라이브로 직접 전환한다.
     *
     * <p>자동 시작에만 기대지 않는 이유는, 같은 스트림에 방송이 여럿 묶여 있을 때 어느
     * 것이 켜질지 유튜브가 정하기 때문이다. 우리가 쓰려는 방송을 지목해 전환하면 그
     * 모호함이 사라진다.
     *
     * <p><b>실패를 삼킨다.</b> 송출 전이면 errorStreamInactive, 자동 시작이 먼저 전환했으면
     * redundantTransition이 온다 — 둘 다 정상적인 경과이고, 전자는 다음 재시도에서 해소된다.
     */
    private void transitionToLive(String accessToken, String broadcastId, String streamId) {
        if (!isStreamActive(accessToken, streamId)) {
            log.info("YouTube transition deferred: ingest not active yet (broadcastId={})", broadcastId);
            return;
        }
        try {
            restClient.post()
                    .uri(properties.apiBaseUrl()
                            + "/youtube/v3/liveBroadcasts/transition"
                            + "?part=id,status&broadcastStatus=live&id=" + broadcastId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("YouTube broadcast transitioned to live: broadcastId={}", broadcastId);
        } catch (RestClientResponseException exception) {
            log.info("YouTube transition skipped: status={} body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
        }
    }

    /** 송출 신호가 실제로 들어오는지 본다. transition은 이게 active여야 성공한다. */
    private boolean isStreamActive(String accessToken, String streamId) {
        StreamList list = restClient.get()
                .uri(properties.apiBaseUrl() + "/youtube/v3/liveStreams?part=id,status&id=" + streamId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(StreamList.class);
        if (list == null || list.items() == null || list.items().isEmpty()) {
            return false;
        }
        Stream stream = list.items().getFirst();
        return stream.status() != null && "active".equalsIgnoreCase(stream.status().streamStatus());
    }

    /** 이미 라이브인 방송 id. 있으면 준비 단계는 할 일이 없다. */
    private Optional<String> activeBroadcastId(String accessToken) {
        return broadcastDetails(accessToken, "active").stream()
                .map(BroadcastDetail::id)
                .findFirst();
    }

    /**
     * 재사용할 수 있는 대기 방송 id.
     *
     * <p>두 조건을 모두 만족해야 한다 — <b>자동 시작이 켜져 있고</b>, <b>우리가 쓰는
     * 스트림에 묶여</b> 있어야 한다. 이 검사가 이 클래스에서 가장 중요한 부분이다.
     * 조건 없이 "대기 방송이 있으면 재사용"으로 두면, 자동 시작이 꺼진 방송 하나가
     * 남아 있는 것만으로 방송이 영구히 켜지지 않는다.
     */
    private Optional<String> usableBroadcastId(String accessToken, String streamId) {
        return broadcastDetails(accessToken, "upcoming").stream()
                .filter(item -> item.contentDetails() != null)
                .filter(item -> Boolean.TRUE.equals(item.contentDetails().enableAutoStart()))
                .filter(item -> streamId.equals(item.contentDetails().boundStreamId()))
                .map(BroadcastDetail::id)
                .findFirst();
    }

    private List<BroadcastDetail> broadcastDetails(String accessToken, String broadcastStatus) {
        try {
            BroadcastDetailList list = restClient.get()
                    .uri(properties.apiBaseUrl()
                            + "/youtube/v3/liveBroadcasts?part=id,contentDetails"
                            + "&broadcastStatus=" + broadcastStatus + "&broadcastType=all")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(BroadcastDetailList.class);
            return list == null || list.items() == null ? List.of() : list.items();
        } catch (RestClientResponseException exception) {
            log.warn("YouTube broadcast lookup failed: status={} body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString());
            throw translate(exception);
        } catch (RestClientException exception) {
            throw PlatformException.connectionFailed();
        }
    }

    /**
     * 채널에 이미 있는 재사용 가능한 ingest 스트림을 찾는다.
     *
     * <p>보통 스트림 키는 하나뿐이라 첫 번째를 쓴다. 여러 개라면 어느 것이 OBS에 들어
     * 있는지 알 방법이 없어 첫 번째를 고르는데, 그 경우 사용자가 OBS의 키를 확인해야
     * 한다 — LAIVE가 OBS에 키를 직접 넣어 주게 되면 사라질 제약이다.
     */
    private Optional<String> findReusableStreamId(String accessToken) {
        StreamList list = restClient.get()
                .uri(properties.apiBaseUrl() + "/youtube/v3/liveStreams?part=id,cdn,status&mine=true")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(StreamList.class);
        if (list == null || list.items() == null || list.items().isEmpty()) {
            return Optional.empty();
        }
        return list.items().stream()
                .filter(stream -> stream.cdn() != null && "rtmp".equalsIgnoreCase(stream.cdn().ingestionType()))
                .map(Stream::id)
                .findFirst();
    }

    /** 재사용할 스트림이 없을 때만 만든다. 이 경우 새 키를 OBS에 넣어야 송출이 닿는다. */
    private String insertStream(String accessToken) {
        log.warn("YouTube reusable ingest stream not found — creating one. "
                + "OBS의 스트림 키를 새로 발급된 값으로 바꿔야 송출이 도달한다.");
        Stream created = restClient.post()
                .uri(properties.apiBaseUrl() + "/youtube/v3/liveStreams?part=snippet,cdn")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "snippet", Map.of("title", "LAIVE"),
                        "cdn", Map.of(
                                "frameRate", "variable",
                                "ingestionType", "rtmp",
                                "resolution", "variable")))
                .retrieve()
                .body(Stream.class);
        if (created == null || created.id() == null) {
            throw PlatformException.connectionFailed();
        }
        return created.id();
    }

    /**
     * 방송을 만든다. scheduledStartTime은 필수 필드라 현재 시각을 넣는다 — 예약이
     * 목적이 아니라 즉시 켜는 것이므로 의미상 "지금"이다.
     */
    private String insertBroadcast(String accessToken, String title) {
        Broadcast created = restClient.post()
                .uri(properties.apiBaseUrl()
                        + "/youtube/v3/liveBroadcasts?part=snippet,status,contentDetails")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "snippet", Map.of(
                                "title", title,
                                "scheduledStartTime", Instant.now().toString()),
                        "status", Map.of(
                                "privacyStatus", properties.broadcastPrivacyStatus(),
                                // 시청자 수·채팅을 수집하려면 아동용이 아니어야 한다.
                                "selfDeclaredMadeForKids", false),
                        "contentDetails", Map.of(
                                // 이 두 값이 핵심이다. 송출이 시작되면 자동으로 라이브가 되고,
                                // 송출이 끊기면 자동으로 종료된다 — 사용자가 유튜브를 열 필요가 없다.
                                "enableAutoStart", true,
                                "enableAutoStop", true)))
                .retrieve()
                .body(Broadcast.class);
        if (created == null || created.id() == null) {
            throw PlatformException.connectionFailed();
        }
        return created.id();
    }

    /** 방송과 ingest 스트림을 묶는다. 이걸 빠뜨리면 송출이 들어와도 방송이 반응하지 않는다. */
    private void bind(String accessToken, String broadcastId, String streamId) {
        restClient.post()
                .uri(properties.apiBaseUrl()
                        + "/youtube/v3/liveBroadcasts/bind?part=id,contentDetails"
                        + "&id=" + broadcastId + "&streamId=" + streamId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
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

    record StreamList(List<Stream> items) {
    }

    record Stream(String id, StreamCdn cdn, StreamStatus status) {
    }

    record StreamCdn(String ingestionType) {
    }

    record StreamStatus(String streamStatus) {
    }

    record BroadcastDetailList(List<BroadcastDetail> items) {
    }

    record BroadcastDetail(String id, BroadcastContentDetails contentDetails) {
    }

    /** 재사용 가능 여부 판정에 쓰는 두 필드. 이 값들 없이는 쓸 수 있는 방송인지 알 수 없다. */
    record BroadcastContentDetails(String boundStreamId, Boolean enableAutoStart) {
    }

    record BroadcastResource(String id, ObjectNode snippet) {
    }
}
