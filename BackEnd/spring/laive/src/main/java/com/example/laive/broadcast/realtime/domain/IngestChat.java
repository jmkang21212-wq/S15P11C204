package com.example.laive.broadcast.realtime.domain;

import java.time.Instant;

/**
 * chat-ai로 중계할 채팅 한 건이다(CHAT_AI_API.md의 chats[] 항목).
 *
 * <p>{@code broadcast.collect} 도메인을 그대로 받지 않고 별도 타입을 두는 이유:
 * collect → realtime 방향의 의존만 남겨 패키지 순환을 막는다. 원문은 전처리 없이
 * 그대로 보낸다 — AI가 학습과 동일한 정규화를 직접 수행한다.
 *
 * @param uid    치지직 userIdHash (viewer_hash 그대로)
 * @param msg    채팅 원문
 * @param sentAt 치지직 messageTime에서 변환한 시각. ts(epoch ms)로 직렬화되며 참고용이다
 */
public record IngestChat(String uid, String msg, Instant sentAt) {
}
