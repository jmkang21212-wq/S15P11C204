package com.example.laive.internal.idempotency;

import com.example.laive.common.exception.BusinessException;
import com.example.laive.internal.config.InternalApiProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * `X-Request-Id`로 내부 변경 요청의 재시도를 멱등하게 만든다(명세 13.1).
 *
 * <p>수집기는 네트워크 오류에 재시도로 대응하는데, 실패가 "요청이 닿지 않음"인지
 * "저장은 됐고 응답만 유실됨"인지 구분할 수 없다. 그래서 재시도는 이미 저장된
 * 배치를 다시 보낼 수 있다. 채팅 메시지에는 자연키가 없어 DB 제약으로 걸러낼
 * 수 없으므로 요청 단위로 막는다.
 *
 * <p>완료된 요청은 응답 본문까지 보관해 재시도에 같은 응답을 돌려준다. 처리 중인
 * 요청과 겹치면 409를 준다 — 이 경우 저장 여부가 아직 확정되지 않아 어떤 응답을
 * 줘도 거짓말이 될 수 있고, 호출자가 잠시 뒤 다시 시도하는 것이 맞다.
 */
@Component
public class RequestIdGuard {

    private static final String KEY_PREFIX = "internal:request-id:";

    /** 처리 중 표식. 응답 본문은 항상 '{'로 시작하는 JSON이라 값이 겹치지 않는다. */
    private static final String IN_PROGRESS = "IN_PROGRESS";

    private final StringRedisTemplate redisTemplate;
    private final InternalApiProperties properties;

    public RequestIdGuard(
            StringRedisTemplate redisTemplate,
            InternalApiProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 요청 처리를 선점한다.
     *
     * @return 이미 완료된 동일 요청이면 그때의 응답 본문, 처음 보는 요청이면 empty
     * @throws BusinessException 동일 요청이 아직 처리 중일 때(409)
     */
    public Optional<String> begin(String requestId) {
        String key = key(requestId);
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(key, IN_PROGRESS, properties.requestIdTtl());

        if (Boolean.TRUE.equals(claimed)) {
            return Optional.empty();
        }

        String recorded = redisTemplate.opsForValue().get(key);
        if (recorded == null) {
            // setIfAbsent와 get 사이에 TTL이 만료됐다. 처음 보는 요청과 같게 다룬다.
            return Optional.empty();
        }
        if (IN_PROGRESS.equals(recorded)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INTERNAL_REQUEST_IN_PROGRESS",
                    "동일한 X-Request-Id 요청이 처리 중입니다."
            );
        }
        return Optional.of(recorded);
    }

    /** 처리에 성공했음을 기록하고 재시도에 돌려줄 응답 본문을 남긴다. */
    public void complete(String requestId, String responseBody) {
        redisTemplate.opsForValue().set(key(requestId), responseBody, properties.requestIdTtl());
    }

    /**
     * 선점을 취소한다.
     *
     * <p>처리가 실패했을 때 표식을 남겨두면 그 requestId는 TTL이 끝날 때까지
     * 재시도를 409로 되돌려보내 영구 차단된다. 일시적 실패는 재시도로 복구되어야
     * 하므로 반드시 지운다.
     */
    public void abandon(String requestId) {
        redisTemplate.delete(key(requestId));
    }

    private String key(String requestId) {
        return KEY_PREFIX + requestId;
    }
}
