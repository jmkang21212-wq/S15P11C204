package com.example.laive.streamer.risk.service;

import com.example.laive.streamer.risk.domain.RiskTermKind;
import com.example.laive.streamer.risk.domain.RiskTermLimits;
import com.example.laive.streamer.risk.domain.RiskTermNormalizer;
import com.example.laive.streamer.risk.domain.RiskTermSeverity;
import com.example.laive.streamer.risk.domain.StreamerRiskTerm;
import com.example.laive.streamer.risk.exception.RiskTermException;
import com.example.laive.streamer.risk.repository.StreamerRiskTermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 스트리머 개인 사전의 조회·등록·삭제를 담당한다 (AI/RISK_POLICY_SPEC.md).
 *
 * <p>변경이 일어나면 방송 중인 세션에도 즉시 반영되도록 {@link RiskTermBroadcaster}로
 * 알린다. 발행은 반드시 커밋 이후에 일어나야 한다 — 순서가 바뀌면 AI 서버가 아직
 * 보이지 않는 상태를 읽어간다.
 */
@Service
@Transactional
public class StreamerRiskTermService {

    private final StreamerRiskTermRepository repository;
    private final RiskTermBroadcaster broadcaster;

    public StreamerRiskTermService(
            StreamerRiskTermRepository repository,
            RiskTermBroadcaster broadcaster
    ) {
        this.repository = repository;
        this.broadcaster = broadcaster;
    }

    @Transactional(readOnly = true)
    public List<StreamerRiskTerm> list(long streamerId) {
        return repository.findAll(streamerId);
    }

    /**
     * 표기를 등록하고 갱신된 전체 목록을 돌려준다.
     *
     * @param rawTerm 사용자가 입력한 원문. 정규화해서 저장하되 원문도 함께 보관한다.
     */
    public List<StreamerRiskTerm> register(
            long streamerId,
            RiskTermKind kind,
            String rawTerm,
            RiskTermSeverity severity
    ) {
        String display = rawTerm == null ? "" : rawTerm.strip();
        String normalized = RiskTermNormalizer.normalize(display);

        validate(display, normalized);
        rejectDuplicate(streamerId, kind, normalized);
        rejectOverLimit(streamerId, kind);

        // 허용어에는 등급이 없다. 요청에 실려 와도 버린다.
        RiskTermSeverity stored = kind == RiskTermKind.BLOCK ? severity : null;
        repository.insert(streamerId, kind, normalized, display, stored);

        return publishAndReturn(streamerId);
    }

    /** 항목을 지우고 갱신된 전체 목록을 돌려준다. */
    public List<StreamerRiskTerm> remove(long streamerId, long id) {
        // streamerId를 조건에 함께 걸었으므로, 0이면 없는 항목이거나 남의 항목이다.
        // 둘을 구분해 알려주면 남의 id 존재 여부가 새어 나가므로 똑같이 404로 답한다.
        if (repository.delete(streamerId, id) == 0) {
            throw RiskTermException.notFound();
        }
        return publishAndReturn(streamerId);
    }

    // --- 내부 ---

    private void validate(String display, String normalized) {
        // 원문 길이도 함께 본다. 정규화가 대부분을 버리는 긴 입력(공백·문장부호 도배)이
        // display 컬럼(40자)을 넘겨 INSERT에서 죽는 것을 막는다.
        if (display.length() > RiskTermLimits.MAX_TERM_LENGTH
                || normalized.length() > RiskTermLimits.MAX_TERM_LENGTH) {
            throw RiskTermException.tooLong();
        }
        if (normalized.isEmpty()) {
            throw RiskTermException.empty();
        }
        if (normalized.length() < RiskTermLimits.MIN_TERM_LENGTH) {
            throw RiskTermException.tooShort();
        }
    }

    /**
     * 같은 표기가 이미 있는지 본다.
     *
     * <p>반대쪽 종류까지 확인하는 이유: 같은 표기를 BLOCK과 ALLOW에 동시에 두면 사전에서
     * 빼면서 넣는 모순이 된다. DB의 UNIQUE는 (streamer, kind, term)이라 이 조합을 막지
     * 못하므로 여기서 걸러야 한다.
     */
    private void rejectDuplicate(long streamerId, RiskTermKind kind, String normalized) {
        for (StreamerRiskTerm existing : repository.findByTerm(streamerId, normalized)) {
            throw existing.kind() == kind
                    ? RiskTermException.duplicate()
                    : RiskTermException.conflictingKind();
        }
    }

    private void rejectOverLimit(long streamerId, RiskTermKind kind) {
        if (repository.countByKind(streamerId, kind) >= RiskTermLimits.MAX_PER_KIND) {
            throw RiskTermException.limitExceeded(RiskTermLimits.MAX_PER_KIND);
        }
    }

    private List<StreamerRiskTerm> publishAndReturn(long streamerId) {
        List<StreamerRiskTerm> terms = repository.findAll(streamerId);
        broadcaster.publishAfterCommit(streamerId, terms);
        return terms;
    }
}
