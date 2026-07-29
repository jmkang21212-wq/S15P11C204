package com.example.laive.broadcast.realtime.domain;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 채팅 감정 8종 라벨이다.
 *
 * <p>AI 서버는 한국어 문자열로 주고 DB·API는 ASCII 코드를 쓴다(명세 13.6).
 * 변환은 chat-ai 응답을 받는 지점 한 곳({@code HttpChatAiGateway})에서만 한다 —
 * 라벨 집합이 모델 버전에 묶여 있어, 변환이 여러 곳에 흩어지면 모델 교체 시
 * 어긋난 지점을 찾을 수 없다.
 *
 * <p>이름은 V8의 {@code chat_messages.sentiment_label} CHECK 제약과 일치해야 한다.
 */
public enum SentimentLabel {

    JOY("웃음/재미"),
    EXCITEMENT("환호/감탄"),
    SUPPORT("응원/호감"),
    SURPRISE("놀람"),
    SADNESS("슬픔/아쉬움"),
    ANGER("분노/불만"),
    CONFUSION("혼란/질문"),
    NEUTRAL("중립");

    private static final Map<String, SentimentLabel> BY_KOREAN = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(label -> label.koreanLabel, label -> label));

    private final String koreanLabel;

    SentimentLabel(String koreanLabel) {
        this.koreanLabel = koreanLabel;
    }

    /** AI 서버의 한국어 라벨을 코드로 바꾼다. 모르는 라벨이면 비어 있다(모델 버전 불일치 신호). */
    public static Optional<SentimentLabel> fromKorean(String koreanLabel) {
        return Optional.ofNullable(BY_KOREAN.get(koreanLabel));
    }
}
