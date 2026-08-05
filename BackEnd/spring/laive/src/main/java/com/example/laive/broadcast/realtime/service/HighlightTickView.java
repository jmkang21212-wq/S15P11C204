package com.example.laive.broadcast.realtime.service;

import java.time.Instant;

/**
 * 하이라이트 판정기의 마지막 틱 상태 스냅샷이다 — 콘솔(live-snapshot)이
 * "지금 판정이 어디까지 와 있나"를 보여주는 관측 전용 값으로, 판정에는
 * 아무 영향이 없다. 파라미터가 백테스트 미검증이라(HIGHLIGHT_SPEC §5)
 * 실방송에서 z가 눈에 보여야 튜닝이 가능하다는 것이 존재 이유다.
 *
 * @param phase            IDLE | ACTIVE | COOLDOWN
 * @param z                마지막 정상 판정 틱의 z. 가드에 걸린 틱이면 null
 * @param engagement       마지막 틱의 유니크 채터 ÷ 시청자. 계산 전이면 null
 * @param windowChats      판정 윈도우 안의 채팅 수
 * @param uniqueChatters   판정 윈도우 안의 유니크 채터 수
 * @param skipReason       마지막 틱이 가드에 걸렸으면 그 이유
 *                         (gap | no-viewers | warmup | empty-baseline | quiet). 정상 판정이면 null
 * @param warmupUntil      워밍업(가드 1)이 끝나는 시각 — 이 전에는 판정하지 않는다
 * @param zEnter           진입 임계 — 콘솔이 "z가 임계까지 얼마나 왔나"를 그릴 때 쓴다
 * @param engageWindowSec  판정 윈도우 길이(초). 기본 60이지만 환경변수로 튜닝되는 값이라
 *                         화면 표기가 하드코딩되면 어긋난다
 */
public record HighlightTickView(
        String phase,
        Double z,
        Double engagement,
        int windowChats,
        int uniqueChatters,
        String skipReason,
        Instant warmupUntil,
        double zEnter,
        int engageWindowSec
) {
}
