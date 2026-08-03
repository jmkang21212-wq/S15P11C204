package com.example.laive.streamer.risk.dto.request;

import com.example.laive.streamer.risk.domain.RiskTermKind;
import com.example.laive.streamer.risk.domain.RiskTermSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 개인 사전 항목 등록 요청이다.
 *
 * <p>길이 제약을 여기에 걸지 않는 이유: 판정 기준이 원문이 아니라 <b>정규화된</b> 길이라
 * 서비스 계층에서 봐야 한다. {@code "아!"}는 두 글자지만 정규화하면 한 글자가 되어
 * 거절 대상이다.
 */
public record CreateRiskTermRequest(
        @NotNull(message = "kind는 BLOCK 또는 ALLOW여야 합니다.")
        RiskTermKind kind,

        @NotBlank(message = "등록할 표현을 입력해 주세요.")
        String term,

        /** BLOCK일 때만 쓴다. 생략하면 MEDIUM. ALLOW에 실려 오면 무시한다. */
        RiskTermSeverity severity
) {
}
