package com.example.laive.broadcast.realtime.integration;

import com.example.laive.broadcast.realtime.config.ChatAiProperties;
import com.example.laive.broadcast.realtime.domain.ChatAiIngestResult;
import com.example.laive.broadcast.realtime.domain.ChatAiQuestionsResult;
import com.example.laive.broadcast.realtime.domain.ChatAiSummary;
import com.example.laive.broadcast.realtime.domain.ChatAiSummaryCall;
import com.example.laive.broadcast.realtime.domain.IngestChat;
import com.example.laive.broadcast.realtime.domain.SentimentLabel;
import com.example.laive.broadcast.realtime.domain.SentimentWindow;
import com.example.laive.broadcast.realtime.domain.SummaryMode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
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
    private final RestClient summaryRestClient;
    private final ChatAiProperties properties;

    /**
     * @param summaryRestClient 여론 요약 전용 클라이언트. LLM 생성이 수 초 걸려
     *                          ingest의 2초 Timeout과 분리한다
     */
    public HttpChatAiGateway(RestClient restClient, RestClient summaryRestClient, ChatAiProperties properties) {
        this.restClient = restClient;
        this.summaryRestClient = summaryRestClient;
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

    /**
     * 반복 질문 Top N을 폴링한다(계약 §3).
     *
     * <p>exclude 파라미터는 쓰지 않는다 — DB에 저장하는 구성에서 exclude로 걸러 받으면
     * 답변 완료한 질문이 응답에서 빠져 세션 리포트에서도 사라진다. 전량 받아
     * UPSERT하고 노출은 DB에서 거른다.
     */
    @Override
    public Optional<ChatAiQuestionsResult> getQuestions(long sessionId, int limit) {
        QuestionsResponseBody response;
        try {
            response = restClient.get()
                    .uri("/broadcasts/{sessionId}/questions?limit={limit}", sessionId, limit)
                    .headers(this::authorize)
                    .retrieve()
                    .body(QuestionsResponseBody.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            // 계약: 404 = ingest가 들어온 적 없는 방송. PREPARING 세션 폴링에서
            // 매 주기 반복되는 정상 경로라 warn으로 남기면 로그가 도배된다.
            log.debug("chat-ai questions 404(ingest 이력 없음): sessionId={}", sessionId);
            return Optional.empty();
        } catch (RestClientException exception) {
            log.warn("chat-ai questions 폴링 실패, 이번 주기를 건너뜁니다: sessionId={} cause={}",
                    sessionId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
        if (response == null) {
            log.warn("chat-ai questions 응답이 비어 있습니다: sessionId={}", sessionId);
            return Optional.empty();
        }

        if (response.pendingEmbedding() != null && response.pendingEmbedding() > 0) {
            log.debug("chat-ai 질문 집계에 미반영 대기 건이 있습니다(다음 폴링이 따라잡음): sessionId={} pending={}",
                    sessionId, response.pendingEmbedding());
        }
        return Optional.of(new ChatAiQuestionsResult(
                toQuestions(sessionId, response.questions()),
                response.totalQuestions() == null ? 0 : response.totalQuestions(),
                response.pendingEmbedding() == null ? 0 : response.pendingEmbedding(),
                response.updatedAt() == null ? null : Instant.ofEpochMilli(response.updatedAt())
        ));
    }

    /**
     * 여론 요약을 새로 생성한다(계약 §2).
     *
     * <p>retrieve() 대신 exchange()를 쓰는 이유: 409(COOLDOWN·IN_FLIGHT)와
     * 422(NOT_ENOUGH_CHATS)는 본문(retryAfterSec 등)을 가진 정상 거절이라 예외로
     * 흘리면 안 되고, 수동 버튼이 그 본문으로 카운트다운을 그린다. 계약이 "409·422는
     * 오류가 아니다 — 로그에 에러로 찍지 말 것"을 요구하므로 debug로만 남긴다.
     */
    @Override
    public ChatAiSummaryCall requestSummary(long sessionId, SummaryMode mode) {
        try {
            return summaryRestClient.post()
                    .uri("/broadcasts/{sessionId}/summary", sessionId)
                    .headers(this::authorize)
                    .body(new SummaryModeBody(mode.name()))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            SummaryResponseBody body = response.bodyTo(SummaryResponseBody.class);
                            if (body == null || body.summary() == null) {
                                log.warn("chat-ai summary 응답에 본문이 없습니다: sessionId={}", sessionId);
                                return new ChatAiSummaryCall.Failure();
                            }
                            return new ChatAiSummaryCall.Success(toSummary(body));
                        }
                        return toRejection(sessionId, response.bodyTo(SummaryRejectionBody.class),
                                response.getStatusCode().value());
                    });
        } catch (RestClientException exception) {
            // AUTO 주기마다 반복될 수 있는 실패라 원인 클래스만 남긴다(ingest와 동일).
            log.warn("chat-ai summary 생성 실패, 다음 주기에 다시 시도합니다: sessionId={} mode={} cause={}",
                    sessionId, mode, exception.getClass().getSimpleName());
            return new ChatAiSummaryCall.Failure();
        }
    }

    /** 거절 본문의 reason으로 분기한다. 모르는 reason·5xx(LLM 미설정/실패)는 Failure다. */
    private ChatAiSummaryCall toRejection(long sessionId, SummaryRejectionBody body, int status) {
        String reason = body == null ? null : body.reason();
        if (reason == null) {
            log.warn("chat-ai summary 비정상 응답: sessionId={} status={}", sessionId, status);
            return new ChatAiSummaryCall.Failure();
        }
        return switch (reason) {
            case "COOLDOWN" -> {
                log.debug("chat-ai summary 쿨다운(정상 거절): sessionId={} retryAfterSec={}",
                        sessionId, body.retryAfterSec());
                yield new ChatAiSummaryCall.Cooldown(
                        body.retryAfterSec() == null ? 0 : body.retryAfterSec(),
                        body.lastSummaryAt());
            }
            case "IN_FLIGHT" -> {
                log.debug("chat-ai summary 생성 진행 중(정상 거절): sessionId={}", sessionId);
                yield new ChatAiSummaryCall.InFlight();
            }
            case "NOT_ENOUGH_CHATS" -> {
                log.debug("chat-ai summary 채팅 부족(정상 거절): sessionId={} chatCount={} required={}",
                        sessionId, body.chatCount(), body.required());
                yield new ChatAiSummaryCall.NotEnoughChats(
                        body.chatCount() == null ? 0 : body.chatCount(),
                        body.required() == null ? 0 : body.required());
            }
            case "UNKNOWN_BROADCAST" -> {
                // 계약: ingest가 들어온 적 없는 방송. PREPARING 폴링의 정상 경로다.
                log.debug("chat-ai summary 404(ingest 이력 없음): sessionId={}", sessionId);
                yield new ChatAiSummaryCall.UnknownBroadcast();
            }
            default -> {
                log.warn("chat-ai summary 거절 사유를 모릅니다(계약 버전 확인 필요): sessionId={} reason={} status={}",
                        sessionId, reason, status);
                yield new ChatAiSummaryCall.Failure();
            }
        };
    }

    /** 마지막 요약을 캐시에서 다시 읽는다(계약 §2 — LLM 호출 없음). */
    @Override
    public Optional<ChatAiSummary> getSummary(long sessionId) {
        SummaryResponseBody response;
        try {
            response = summaryRestClient.get()
                    .uri("/broadcasts/{sessionId}/summary", sessionId)
                    .headers(this::authorize)
                    .retrieve()
                    .body(SummaryResponseBody.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            // 계약: 404 = 아직 생성된 요약이 없음(NO_SUMMARY). 정상 경로다.
            log.debug("chat-ai summary 404(생성된 요약 없음): sessionId={}", sessionId);
            return Optional.empty();
        } catch (RestClientException exception) {
            log.warn("chat-ai summary 조회 실패, 이번 회수를 건너뜁니다: sessionId={} cause={}",
                    sessionId, exception.getClass().getSimpleName());
            return Optional.empty();
        }
        if (response == null || response.summary() == null) {
            log.warn("chat-ai summary 조회 응답이 비어 있습니다: sessionId={}", sessionId);
            return Optional.empty();
        }
        return Optional.of(toSummary(response));
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

    /**
     * 요약 응답을 코드 라벨 스키마로 바꾼다. 라벨 변환 규칙은 {@link #toWindow}와
     * 같다 — 모르는 라벨은 warn 후 버린다(모델 버전 확인 신호).
     */
    private ChatAiSummary toSummary(SummaryResponseBody body) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        if (body.distribution() != null) {
            body.distribution().forEach((korean, ratio) ->
                    SentimentLabel.fromKorean(korean).ifPresentOrElse(
                            label -> distribution.put(label.name(), ratio),
                            () -> log.warn("모르는 감정 라벨입니다(모델 버전 확인 필요): {}", korean)
                    ));
        }
        String dominant = body.dominant() == null
                ? null
                : SentimentLabel.fromKorean(body.dominant()).map(Enum::name).orElse(null);
        return new ChatAiSummary(
                body.summary(),
                dominant,
                distribution.isEmpty() ? null : Map.copyOf(distribution),
                body.chatCount() == null ? 0 : body.chatCount(),
                body.windowSec() == null ? 180 : body.windowSec(),
                body.generatedAt(),
                body.nextAvailableAt()
        );
    }

    /**
     * 질문 목록을 도메인 타입으로 바꾼다. 필수 필드가 빠진 항목은 그 항목만 버린다 —
     * 목록 하나 때문에 나머지 49건의 반영을 막을 이유가 없다.
     */
    private List<ChatAiQuestionsResult.Question> toQuestions(long sessionId, List<QuestionBody> bodies) {
        if (bodies == null) {
            return List.of();
        }
        List<ChatAiQuestionsResult.Question> questions = new ArrayList<>(bodies.size());
        for (QuestionBody body : bodies) {
            if (body == null || body.questionId() == null || body.text() == null
                    || body.askers() == null || body.firstAskedAt() == null || body.lastAskedAt() == null) {
                log.warn("chat-ai 질문 항목에 필수 필드가 없어 버립니다: sessionId={} item={}", sessionId, body);
                continue;
            }
            questions.add(new ChatAiQuestionsResult.Question(
                    body.questionId(),
                    body.text(),
                    toQuestionType(body.type()),
                    body.askers(),
                    body.messages() == null ? body.askers() : body.messages(),
                    Instant.ofEpochMilli(body.firstAskedAt()),
                    Instant.ofEpochMilli(body.lastAskedAt())
            ));
        }
        return questions;
    }

    /** FAQ/CONTEXT만 통과시킨다. 모르는 값은 "판정 전"과 같은 null로 눌러쓴다. */
    private String toQuestionType(String type) {
        if (type == null || type.equals("FAQ") || type.equals("CONTEXT")) {
            return type;
        }
        log.warn("모르는 질문 타입입니다(계약 버전 확인 필요): {}", type);
        return null;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuestionsResponseBody(
            List<QuestionBody> questions,
            Long totalQuestions,
            Long pendingEmbedding,
            Long updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuestionBody(
            Long questionId,
            String text,
            String type,
            Integer askers,
            Integer messages,
            Long firstAskedAt,
            Long lastAskedAt
    ) {
    }

    record SummaryModeBody(String mode) {
    }

    // generatedAt·nextAvailableAt은 ISO-8601 문자열("...Z")이라 Instant로 바로 바인딩된다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummaryResponseBody(
            String summary,
            String dominant,
            Map<String, Double> distribution,
            Long chatCount,
            Integer windowSec,
            Instant generatedAt,
            Instant nextAvailableAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummaryRejectionBody(
            String reason,
            Integer retryAfterSec,
            Instant lastSummaryAt,
            Long chatCount,
            Integer required
    ) {
    }
}
