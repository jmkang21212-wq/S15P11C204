package com.example.laive.streamer.risk.domain;

/**
 * 개인 사전 항목의 종류다. DB CHECK 제약(ck_srt_kind)과 이름을 반드시 동일하게 유지한다.
 *
 * <p>두 종류가 감지 파이프라인에서 하는 일이 다르다 (AI/RISK_POLICY_SPEC.md §1).
 * <ul>
 *   <li>{@link #BLOCK} — 스트리머별 사전에 표기를 <b>추가</b>한다. 사전 히트는 LLM을
 *       건너뛰고 즉시 알림이므로, 등록하는 순간 그 판정이 최종이 된다.</li>
 *   <li>{@link #ALLOW} — 사전에서 표기를 <b>제거</b>하고, 동시에 LLM 프롬프트에
 *       주입한다. 사전에서 빼기만 하면 그 발화는 LLM으로 넘어가 거기서 다시 걸린다.</li>
 * </ul>
 */
public enum RiskTermKind {
    BLOCK,
    ALLOW
}
