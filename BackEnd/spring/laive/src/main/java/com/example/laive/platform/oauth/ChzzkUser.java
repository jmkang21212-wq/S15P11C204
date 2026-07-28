package com.example.laive.platform.oauth;

/**
 * 치지직 유저 조회(/open/v1/users/me) 응답이다.
 * 모든 유저가 채널을 소유하므로 channelId가 채널 식별자이자 유저 식별자다.
 * nickname은 문서에 없지만 실제 응답에 포함된다.
 */
public record ChzzkUser(String channelId, String channelName, String nickname) {
}
