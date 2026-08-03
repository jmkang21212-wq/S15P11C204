package com.example.laive.streamer.risk.dto.response;

import com.example.laive.streamer.risk.domain.RiskTermKind;
import com.example.laive.streamer.risk.domain.RiskTermLimits;
import com.example.laive.streamer.risk.domain.StreamerRiskTerm;

import java.util.ArrayList;
import java.util.List;

/**
 * 개인 사전 전체 목록이다. 등록·삭제 응답도 같은 모양을 돌려주므로 프론트가 다시 조회할 필요가 없다.
 *
 * <p>사용자에게는 정규화된 {@code term}이 아니라 입력 원문({@code display})을 보여준다.
 * 정규화 결과는 읽기 나빠서 자기가 뭘 등록했는지 알아보지 못한다.
 */
public record RiskTermsResponse(
        List<BlockItem> block,
        List<AllowItem> allow,
        Limits limits
) {

    public record BlockItem(long id, String display, String severity) {
    }

    public record AllowItem(long id, String display) {
    }

    /** 설정 화면이 "N/200" 같은 표시를 하드코딩하지 않게 상한을 함께 내린다. */
    public record Limits(int maxPerKind, int maxTermLength) {
    }

    public static RiskTermsResponse from(List<StreamerRiskTerm> terms) {
        List<BlockItem> block = new ArrayList<>();
        List<AllowItem> allow = new ArrayList<>();

        for (StreamerRiskTerm term : terms) {
            if (term.kind() == RiskTermKind.BLOCK) {
                block.add(new BlockItem(term.id(), term.display(),
                        term.effectiveSeverity().name()));
            } else {
                allow.add(new AllowItem(term.id(), term.display()));
            }
        }

        return new RiskTermsResponse(block, allow,
                new Limits(RiskTermLimits.MAX_PER_KIND, RiskTermLimits.MAX_TERM_LENGTH));
    }
}
