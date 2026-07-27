package com.example.laive.platform.oauth;

/**
 * 치지직 Open API의 공통 응답 Envelope다.
 * 성공과 실패 모두 같은 구조로 내려오며 실패 시 content가 null이다.
 * 예) {"code":200,"message":null,"content":{...}}
 *     {"code":401,"message":"INVALID_TOKEN"}
 */
public record ChzzkApiResponse<T>(int code, String message, T content) {

    public boolean hasContent() {
        return content != null;
    }
}
