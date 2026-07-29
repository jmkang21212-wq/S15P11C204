package com.example.laive.broadcast.realtime.integration;

import com.example.laive.broadcast.realtime.config.ChatAiProperties;
import com.example.laive.broadcast.realtime.domain.ChatAiIngestResult;
import com.example.laive.broadcast.realtime.domain.IngestChat;
import com.example.laive.broadcast.realtime.domain.SentimentLabel;
import com.example.laive.broadcast.realtime.domain.SentimentWindow;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * chat-ai(기본 포트 8001)를 HTTP로 호출하는 Adapter다. 계약은 {@code AI/CHAT_AI_API.md}.
 *
 * <p>한국어 라벨 → ASCII 코드 변환은 여기서만 한다(명세 13.6). 응답의 results와
 * window 둘 다 이 지점을 지나므로, 모델 라벨이 바뀌면 이 파일과 {@link SentimentLabel}만
 * 보면 된다.
 */
public class HttpChatAiGateway implements ChatAiGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpChatAiGateway.class);

    private final RestClient restClient;
    private final ChatAiProperties properties;

    public HttpChatAiGateway(RestClient restClient, ChatAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 채팅 배치를 분류한다.
     *
     * <p>어떤 실패도 던지지 않는다. 여기서 예외가 새어 나가면 호출자의 채팅 저장
     * 트랜잭션과 X-Request-Id 멱등 선점까지 되돌아가, AI 장애가 원본 채팅 유실로
     * 번진다. 계약도 같은 이유로 "실패 시 재시도 없이 배치 스킵"을 요구한다.
     */
    @Override
    public Optional<ChatAiIngestResult> ingest(long sessionId, Integer viewers, List<IngestChat> chats) {
        IngestRequestBody body = new IngestRequestBody(
                viewers,
                chats.stream()
                        .map(chat -> new ChatBody(chat.uid(), chat.msg(), chat.sentAt().toEpochMilli()))
                        .toList()
        );

        IngestResponseBody response;
        try {
            response = restClient.post()
                    .uri("/broadcasts/{sessionId}/ingest", sessionId)
                    .headers(this::authorize)
                    .body(body)
                    .retrieve()
                    .body(IngestResponseBody.class);
        } catch (RestClientException exception) {
            // 스택을 통째로 남기지 않는 이유: AI가 내려가 있으면 초당 배치마다
            // 같은 실패가 반복된다. 원인 클래스만으로 진단에 충분하다.
            log.warn("chat-ai ingest 실패, 배치를 스킵합니다(라벨 없이 저장): sessionId={} cause={}",
                    sessionId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
        if (response == null) {
            log.warn("chat-ai ingest 응답이 비어 있습니다: sessionId={}", sessionId);
            return Optional.empty();
        }

        return Optional.of(new ChatAiIngestResult(
                toLabels(sessionId, chats.size(), response.results()),
                toWindow(response.window())
        ));
    }

    @Override
    public void endBroadcast(long sessionId) {
        try {
            restClient.delete()
                    .uri("/broadcasts/{sessionId}", sessionId)
                    .headers(this::authorize)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            // 마지막 ingest 후 10분이 지나면 chat-ai가 스스로 정리한다.
            log.warn("chat-ai 방송 정리 요청 실패(10분 뒤 자동 정리됨): sessionId={} cause={}",
                    sessionId, exception.getClass().getSimpleName());
        }
    }

    /**
     * results를 요청 순서 그대로 코드 라벨로 바꾼다.
     *
     * <p>건수가 어긋나면 겹치는 앞부분만 쓴다. 계약상 results는 요청 chats와 같은
     * 순서·같은 건수지만, 어긋난 응답 때문에 배치 전체를 버리면 원본 저장까지
     * 라벨 없이 가야 할 이유가 없다.
     */
    private List<String> toLabels(long sessionId, int chatCount, List<ResultBody> results) {
        List<String> labels = new ArrayList<>(chatCount);
        int available = results == null ? 0 : results.size();
        if (available != chatCount) {
            log.warn("chat-ai results 건수가 요청과 다릅니다: sessionId={} 요청={} 응답={}",
                    sessionId, chatCount, available);
        }
        for (int i = 0; i < chatCount; i++) {
            if (i >= available || results.get(i) == null || results.get(i).label() == null) {
                labels.add(null);
                continue;
            }
            String korean = results.get(i).label();
            labels.add(SentimentLabel.fromKorean(korean)
                    .map(Enum::name)
                    .orElseGet(() -> {
                        log.warn("모르는 감정 라벨입니다(모델 버전 확인 필요): {}", korean);
                        return null;
                    }));
        }
        return labels;
    }

    /**
     * window를 코드 라벨 스키마로 바꾼다.
     *
     * <p>chatCount가 0이면 chat-ai는 전 라벨 0.0 분포에 dominant null을 주는데,
     * 명세 10.6은 이때 dominant와 distribution 둘 다 null을 요구한다.
     */
    private SentimentWindow toWindow(WindowBody window) {
        if (window == null) {
            return null;
        }
        long analyzedChat = window.chatCount() == null ? 0 : window.chatCount();
        if (analyzedChat == 0) {
            return new SentimentWindow(orDefaultWindowSec(window), 0, window.engagement(), null, null);
        }

        Map<String, Double> distribution = new LinkedHashMap<>();
        if (window.distribution() != null) {
            window.distribution().forEach((korean, ratio) ->
                    SentimentLabel.fromKorean(korean).ifPresentOrElse(
                            label -> distribution.put(label.name(), ratio),
                            () -> log.warn("모르는 감정 라벨입니다(모델 버전 확인 필요): {}", korean)
                    ));
        }
        String dominant = window.dominant() == null
                ? null
                : SentimentLabel.fromKorean(window.dominant()).map(Enum::name).orElse(null);
        return new SentimentWindow(
                orDefaultWindowSec(window),
                analyzedChat,
                window.engagement(),
                dominant,
                distribution.isEmpty() ? null : Map.copyOf(distribution)
        );
    }

    private int orDefaultWindowSec(WindowBody window) {
        return window.windowSec() == null ? 180 : window.windowSec();
    }

    private void authorize(HttpHeaders headers) {
        if (properties.token() != null && !properties.token().isBlank()) {
            headers.setBearerAuth(properties.token());
        }
    }

    record IngestRequestBody(Integer viewers, List<ChatBody> chats) {
    }

    record ChatBody(String uid, String msg, long ts) {
    }

    // 응답에 필드가 추가돼도(chat-ai 쪽 확장) 역직렬화가 깨지지 않게 한다.
    // 공용 ObjectMapper 빈이 순정 설정이라 미지 필드가 기본으로는 오류다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record IngestResponseBody(List<ResultBody> results, WindowBody window) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResultBody(String label, Double score) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WindowBody(
            Integer windowSec,
            Long chatCount,
            Long uniqueChatters,
            Integer viewers,
            Double engagement,
            Map<String, Double> distribution,
            String dominant
    ) {
    }
}
