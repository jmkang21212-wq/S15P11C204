package com.example.laive.streamer.risk.repository;

import com.example.laive.streamer.risk.domain.RiskTermKind;
import com.example.laive.streamer.risk.domain.RiskTermSeverity;
import com.example.laive.streamer.risk.domain.StreamerRiskTerm;

import java.util.List;

/**
 * 개인 사전 영속성 Port다. 구현은 repository.mybatis에 있다.
 */
public interface StreamerRiskTermRepository {

    /** 종류 구분 없이 전량. 티켓 발급·pub/sub 발행이 한 번에 다 필요로 한다. */
    List<StreamerRiskTerm> findAll(long streamerId);

    /** 상한 검사용. 종류별로 센다. */
    long countByKind(long streamerId, RiskTermKind kind);

    /**
     * 이미 등록된 표기인지 본다. 종류를 가리지 않고 확인해 BLOCK과 ALLOW에 같은 표기가
     * 동시에 들어가는 것을 서비스 계층이 막을 수 있게 한다.
     */
    List<StreamerRiskTerm> findByTerm(long streamerId, String term);

    long insert(long streamerId, RiskTermKind kind, String term, String display,
                RiskTermSeverity severity);

    /** 남의 항목을 지우지 못하도록 streamerId를 조건에 함께 건다. 지운 행 수를 돌려준다. */
    int delete(long streamerId, long id);
}
