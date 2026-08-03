package com.example.laive.streamer.risk.repository.mybatis;

import com.example.laive.streamer.risk.domain.RiskTermKind;
import com.example.laive.streamer.risk.domain.RiskTermSeverity;
import com.example.laive.streamer.risk.domain.StreamerRiskTerm;
import com.example.laive.streamer.risk.repository.StreamerRiskTermRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MyBatis Mapper 타입이 Service 계층으로 노출되지 않게 하는 Repository Adapter다.
 */
@Repository
public class MyBatisStreamerRiskTermRepository implements StreamerRiskTermRepository {

    private final StreamerRiskTermMapper mapper;

    public MyBatisStreamerRiskTermRepository(StreamerRiskTermMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<StreamerRiskTerm> findAll(long streamerId) {
        return mapper.findAll(streamerId);
    }

    @Override
    public long countByKind(long streamerId, RiskTermKind kind) {
        return mapper.countByKind(streamerId, kind);
    }

    @Override
    public List<StreamerRiskTerm> findByTerm(long streamerId, String term) {
        return mapper.findByTerm(streamerId, term);
    }

    @Override
    public long insert(long streamerId, RiskTermKind kind, String term, String display,
                       RiskTermSeverity severity) {
        return mapper.insert(streamerId, kind, term, display, severity);
    }

    @Override
    public int delete(long streamerId, long id) {
        return mapper.delete(streamerId, id);
    }
}
