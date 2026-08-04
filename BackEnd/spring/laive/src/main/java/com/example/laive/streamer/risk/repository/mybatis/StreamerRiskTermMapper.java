package com.example.laive.streamer.risk.repository.mybatis;

import com.example.laive.streamer.risk.domain.RiskTermKind;
import com.example.laive.streamer.risk.domain.RiskTermSeverity;
import com.example.laive.streamer.risk.domain.StreamerRiskTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StreamerRiskTermMapper {

    List<StreamerRiskTerm> findAll(@Param("streamerId") long streamerId);

    long countByKind(
            @Param("streamerId") long streamerId,
            @Param("kind") RiskTermKind kind
    );

    List<StreamerRiskTerm> findByTerm(
            @Param("streamerId") long streamerId,
            @Param("term") String term
    );

    long insert(
            @Param("streamerId") long streamerId,
            @Param("kind") RiskTermKind kind,
            @Param("term") String term,
            @Param("display") String display,
            @Param("severity") RiskTermSeverity severity
    );

    int delete(
            @Param("streamerId") long streamerId,
            @Param("id") long id
    );
}
