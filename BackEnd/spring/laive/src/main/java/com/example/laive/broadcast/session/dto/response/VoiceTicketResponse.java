package com.example.laive.broadcast.session.dto.response;

/**
 * 음성 AI WebSocket 접속 티켓.
 *
 * <p>프론트는 이 값을 핸드셰이크의 {@code ticket} 필드에 그대로 실어 보낸다.
 * 일회용이며 짧은 시간 뒤 만료되므로, 재연결할 때마다 새로 발급받아야 한다.
 */
public record VoiceTicketResponse(String ticket, long expiresInSec) {
}
