package com.example.laive.broadcast.realtime.domain;

import java.time.Instant;
import java.util.Map;

/**
 * chat-ai 여론 요약 성공 응답이다(계약 §2). dominant와 distribution은 게이트웨이가
 * 이미 ASCII 코드({@link SentimentLabel})로 변환한 값이다.
 *
 * @param summary         LLM이 생성한 요약 단락. 환경 문제 감지 시 안내 문장이 붙어 있다
 * @param dominant        최다 감정 코드
 * @param distribution    8종 감정 분포(합 1.0)
 * @param chatCount       요약 근거가 된 윈도우 채팅 수
 * @param windowSec       윈도우 길이(초)
 * @param generatedAt     요약 생성 시각
 * @param nextAvailableAt 쿨다운이 풀려 다음 요약이 가능해지는 시각. 프론트 버튼
 *                        카운트다운의 근거다
 */
public record ChatAiSummary(
        String summary,
        String dominant,
        Map<String, Double> distribution,
        long chatCount,
        int windowSec,
        Instant generatedAt,
        Instant nextAvailableAt
) {
}
