package com.example.laive.broadcast.collect.dto.response;

/**
 * 수집기가 치지직 핸드셰이크에 쓸 자격 정보다.
 *
 * <p>수집기가 시작 시점에 한 번 받아 들고 있지 않고 핸드셰이크마다 새로 받는다.
 * Access Token은 방송보다 먼저 만료될 수 있는데, 들고 있던 토큰이 만료되면
 * 재핸드셰이크가 401로 계속 실패해 남은 방송 내내 수집이 죽는다. 매번 받으면
 * Spring이 필요할 때 갱신한 토큰을 돌려주므로 만료가 장애로 이어지지 않는다.
 *
 * @param accessToken 복호화·갱신된 치지직 Access Token
 * @param channelId   시청자 수 폴링에 쓰는 채널 식별자
 */
public record CollectorCredentialsResponse(String accessToken, String channelId) {
}
