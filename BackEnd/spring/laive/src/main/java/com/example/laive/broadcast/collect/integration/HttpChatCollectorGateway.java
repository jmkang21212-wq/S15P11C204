package com.example.laive.broadcast.collect.integration;

import com.example.laive.broadcast.collect.config.CollectorProperties;
import com.example.laive.broadcast.collect.domain.CollectorState;
import com.example.laive.broadcast.collect.service.CollectionIngestService;
import com.example.laive.broadcast.session.integration.ChatCollectorGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;

/**
 * 세션별 치지직 채팅 수집기를 제어 서비스에 시작·종료 요청한다.
 *
 * <p>수집기가 Spring 밖의 별도 프로세스인 이유는 <b>socket.io 클라이언트</b>다.
 * 치지직은 raw WebSocket이 아니라 socket.io 프로토콜을 쓰는데(engine.io 프레이밍과
 * 자체 핸드셰이크), Java 쪽 클라이언트인 socket.io-client-java는 관리가 멈춘
 * 구버전 프로토콜 기준이다. Python의 python-socketio는 유지되고 있고 2026-07-28
 * 스파이크가 그걸로 수집 성공을 검증했다.
 *
 * <p>성능은 이유가 아니다. 방송 하나의 채팅은 초당 수십 건이고 전부 I/O 대기라
 * 어느 런타임도 부담이 없다. Spring이 세션당 전용 스레드를 못 드는 것도 아니다 —
 * {@code @Scheduled} 같은 주기 실행 모델로는 sessionKey와 재핸드셰이크 상태를
 * 이어갈 수 없지만, 그건 전용 스레드가 필요하다는 뜻이지 Spring이 안 된다는 뜻이
 * 아니다.
 *
 * <p>분리해서 얻는 것은 장애 격리다. 재핸드셰이크 루프가 물리거나 수집기가 죽어도
 * Tomcat 스레드와 Outbox 워커를 잡아먹지 않아, 한 세션의 수집 문제가 다른 방송의
 * 준비까지 멈추게 하지 않는다.
 */
public class HttpChatCollectorGateway implements ChatCollectorGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpChatCollectorGateway.class);

    private final RestClient restClient;
    private final CollectorProperties properties;
    private final CollectionIngestService ingestService;
    private final CollectorStatusStore statusStore;

    public HttpChatCollectorGateway(
            RestClient restClient,
            CollectorProperties properties,
            CollectionIngestService ingestService,
            CollectorStatusStore statusStore
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.ingestService = ingestService;
        this.statusStore = statusStore;
    }

    /**
     * 수집을 시작한다. 실패하면 예외를 던져 준비 Outbox 이벤트가 재시도하게 한다.
     *
     * <p>조용히 넘어가지 않는 이유: 여기서 삼키면 세션이 LIVE로 전환되지만 채팅이
     * 한 건도 들어오지 않는다. 트리거 판정과 리포트가 전부 이 데이터에 걸려 있어
     * 방송이 끝난 뒤에야 빈 리포트로 드러난다. 준비 단계 실패로 다루면 재시도
     * 백오프를 거치고, 끝내 안 되면 세션이 FAILED로 확정돼 즉시 보인다.
     */
    @Override
    public void start(long sessionId, long streamerId, long platformAccountId) {
        try {
            restClient.post()
                    .uri("/collectors/{sessionId}", sessionId)
                    .headers(this::authorize)
                    .retrieve()
                    .toBodilessEntity();
            log.info("채팅 수집기 시작 요청 완료: sessionId={}", sessionId);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "채팅 수집기를 시작할 수 없습니다: sessionId=" + sessionId, exception);
        }
    }

    /**
     * 수집을 종료하고 남은 배치를 Flush하게 한다.
     *
     * <p>start와 달리 실패해도 예외를 던지지 않는다. 방송은 이미 끝났고 통계 확정과
     * 리포트 생성이 뒤에 이어지는데, 수집기 정리 실패로 그 경로를 막으면 이미 모인
     * 데이터까지 리포트가 되지 못한다. 수집기는 하트비트가 끊기면 STOPPED로
     * 보이므로 정리 실패가 조용히 묻히지도 않는다.
     */
    @Override
    public void stopAndFlush(long sessionId, Instant endedAt, Duration gracePeriod) {
        try {
            restClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/collectors/{sessionId}")
                            .queryParam("gracePeriodMs", gracePeriod.toMillis())
                            .build(sessionId))
                    .headers(this::authorize)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("채팅 수집기 종료 요청 실패: sessionId={}", sessionId, exception);
        }

        // 수집기가 공백 도중에 죽었으면 그 구간은 아무도 닫지 않는다.
        // 세션 종료가 마지막 방어선이다(V9).
        int closed = ingestService.closeOpenCollectionGaps(sessionId, endedAt);
        if (closed > 0) {
            log.info("종료 시각으로 닫은 열린 수집 공백: sessionId={} count={}", sessionId, closed);
        }

        statusStore.record(sessionId, CollectorState.STOPPED, endedAt);
    }

    private void authorize(HttpHeaders headers) {
        if (properties.controlToken() != null && !properties.controlToken().isBlank()) {
            headers.setBearerAuth(properties.controlToken());
        }
    }
}
