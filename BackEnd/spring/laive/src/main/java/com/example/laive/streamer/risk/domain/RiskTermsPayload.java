package com.example.laive.streamer.risk.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 서버로 보내는 개인 사전 표현이다. 티켓(접속 시)과 pub/sub(방송 중 변경)이 같은 모양을 쓴다.
 *
 * <p><b>정규화된 term이 아니라 사용자 원문(display)을 보낸다.</b> AI 서버가 자기
 * {@code app/detect/normalize.py}로 다시 처리해 automaton을 만들기 때문이다. 이렇게 두면
 * Spring의 {@link RiskTermNormalizer}가 파이썬과 어긋나더라도 매칭 결과는 항상 AI 쪽
 * 구현을 따라가고, 어긋남의 피해가 등록 검증·중복 판정 선에서 멈춘다.
 *
 * <p>허용어에 severity가 없는 이유: 감지를 없애는 쪽이라 등급이라는 개념이 없다.
 */
public record RiskTermsPayload(
        List<BlockTerm> block,
        List<String> allow
) {

    public record BlockTerm(String term, String severity) {
    }

    public static RiskTermsPayload from(List<StreamerRiskTerm> terms) {
        List<BlockTerm> block = new ArrayList<>();
        List<String> allow = new ArrayList<>();

        for (StreamerRiskTerm term : terms) {
            if (term.kind() == RiskTermKind.BLOCK) {
                block.add(new BlockTerm(term.display(), term.effectiveSeverity().name()));
            } else {
                allow.add(term.display());
            }
        }
        return new RiskTermsPayload(block, allow);
    }

    public static RiskTermsPayload empty() {
        return new RiskTermsPayload(List.of(), List.of());
    }
}
