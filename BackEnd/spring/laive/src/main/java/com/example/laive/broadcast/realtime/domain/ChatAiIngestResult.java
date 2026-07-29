package com.example.laive.broadcast.realtime.domain;

import java.util.List;

/**
 * chat-ai ingest 응답을 코드 라벨로 변환한 결과다.
 *
 * @param labels 요청 채팅과 같은 순서의 감정 코드. 분류 불가·모르는 라벨이면 그 자리만 null
 * @param window 최근 윈도우 지표. 응답에 없으면 null
 */
public record ChatAiIngestResult(List<String> labels, SentimentWindow window) {
}
