package com.example.laive.broadcast.realtime.domain.highlight;

import java.time.Duration;

/**
 * CHAT_SURGE 판정 파라미터다. 초기값은 HIGHLIGHT_SPEC §5 — 백테스트로 검증되지
 * 않은 출발점이므로 전부 설정으로 뺐다.
 *
 * @param engageWindow   유니크 채터를 세는 윈도우. ingest의 180초 윈도우를 쓰지 않는
 *                       이유는 스펙 §0 — 하이라이트 시작점이 밀리지 않으려면 짧아야 한다
 * @param baselineSize   baseline 링버퍼 크기(틱 수). 900틱 = 15분
 * @param zEnter         진입 임계. zExit와 다르게 두는 히스테리시스가 경계에서
 *                       구간이 잘게 쪼개지는 것을 막는다
 * @param enterTicks     진입 확정에 필요한 연속 틱 수
 * @param zExit          이탈 임계
 * @param exitTicks      이탈 확정에 필요한 연속 틱 수
 * @param lead           확정 시 시작점을 앞으로 당기는 리드. 반응은 원인보다 늦다
 * @param maxActive      이 시간을 넘긴 구간은 강제 확정한다
 * @param cooldown       확정 직후 판정을 쉬는 시간
 * @param warmup         방송 시작 후 판정을 쉬는 시간(가드 1 — 인사 러시 오탐 방지)
 * @param baselineDuringWarmup 워밍업 중에도 baseline을 적재할지. 기본 true —
 *                       워밍업의 원래 목적이 baseline 채우기다. false면 워밍업 표본을
 *                       버려서, 시작 직후 인사 러시가 "평소 수준"을 선점하는 것을 막는다
 *                       (짧은 시연처럼 러시가 baseline의 다수를 차지할 때 유효).
 *                       대신 판정이 거의 빈 baseline으로 시작하므로 초반 z가 불안정하다
 * @param minWindowChats 윈도우 채팅이 이보다 적으면 그 틱은 판정하지 않는다(가드 3)
 * @param madEpsilon     MAD 절대 하한(가드 2 — 같은 값이 절반을 넘으면 MAD가 0이 된다)
 * @param madFloorChatters MAD 동적 하한(채터 수 단위). 실제 하한은
 *                       max(madEpsilon, madFloorChatters / viewers)로, 채터 수 단위라
 *                       채널 규모와 무관하게 같은 의미를 갖는다. 0이면 비활성(ε만 적용)
 */
public record HighlightParams(
        Duration engageWindow,
        int baselineSize,
        double zEnter,
        int enterTicks,
        double zExit,
        int exitTicks,
        Duration lead,
        Duration maxActive,
        Duration cooldown,
        Duration warmup,
        boolean baselineDuringWarmup,
        int minWindowChats,
        double madEpsilon,
        double madFloorChatters
) {
}
