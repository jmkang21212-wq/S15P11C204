package com.example.laive.broadcast.analysis.service;

import com.example.laive.broadcast.analysis.domain.PromiseDetection;
import com.example.laive.broadcast.analysis.domain.RiskAlert;
import com.example.laive.broadcast.analysis.domain.TranscriptItem;
import com.example.laive.broadcast.analysis.repository.mybatis.AnalysisMapper;
import com.example.laive.broadcast.analysis.repository.mybatis.AnalysisSessionRef;
import com.example.laive.broadcast.analysis.repository.mybatis.TranscriptRow;
import com.example.laive.broadcast.analysis.repository.mybatis.TranscriptUpsert;
import com.example.laive.broadcast.collect.domain.BatchIngestResult;
import com.example.laive.broadcast.session.exception.BroadcastSessionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 서버가 보낸 전사·위험 발언·약속을 영속화한다(명세 13.4, 13.5).
 *
 * <p>세 가지가 서로 다른 속도로 도착한다. 전사는 배치라 늦고 위험 경고와 약속은
 * 즉시 온다. 그래서 경고·약속을 저장할 때 그 안에 실린 원문으로 전사를 Upsert한 뒤
 * 연결한다. 도착 순서를 따지지 않아도 되고, 뒤늦게 같은 발화가 배치로 와도
 * (session_id, start_ms, end_ms) Unique가 흡수한다.
 */
@Service
public class AnalysisIngestService {

    private final AnalysisMapper mapper;

    public AnalysisIngestService(AnalysisMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * STT 전사를 배치 저장한다.
     *
     * <p>같은 배치 안의 중복은 DB에 보내기 전에 접는다. PostgreSQL은 하나의
     * ON CONFLICT 명령이 같은 행을 두 번 건드리면 거부하므로, 접지 않으면 배치
     * 전체가 실패한다.
     */
    @Transactional
    public BatchIngestResult appendTranscripts(long sessionId, List<TranscriptItem> items) {
        AnalysisSessionRef session = requireSession(sessionId);

        Map<Long, TranscriptUpsert> byStartMs = new LinkedHashMap<>();
        for (TranscriptItem item : items) {
            TranscriptUpsert upsert = toUpsert(session, item);
            byStartMs.put(upsert.startMs(), upsert);
        }

        List<TranscriptRow> rows =
                mapper.upsertTranscripts(sessionId, List.copyOf(byStartMs.values()));
        int inserted = (int) rows.stream().filter(TranscriptRow::inserted).count();
        return new BatchIngestResult(inserted, items.size() - inserted);
    }

    /** 위험 발언을 저장하고 실시간 콘솔용 브리핑을 만든다(명세 13.5). */
    @Transactional
    public long appendRiskEvent(long sessionId, RiskAlert alert) {
        AnalysisSessionRef session = requireSession(sessionId);

        TranscriptRow transcript = mapper.upsertTranscript(
                sessionId,
                toUpsert(session, new TranscriptItem(alert.text(), alert.startedAt(), alert.endedAt()))
        );

        long riskEventId = requireId(mapper.insertRiskEvent(
                sessionId,
                transcript.transcriptId(),
                alert.severity(),
                alert.category(),
                null
        ), "위험 발언");

        mapper.insertBriefing(sessionId, "RISK", alert.briefingMessage());
        return riskEventId;
    }

    /** 약속 후보를 DETECTED로 저장하고 브리핑을 만든다(명세 13.5). */
    @Transactional
    public long appendPromise(long sessionId, PromiseDetection detection) {
        AnalysisSessionRef session = requireSession(sessionId);

        // 약속은 발화 구간이 따로 오지 않는다. 감지된 시각 한 점만 있어
        // end_ms > start_ms 제약을 맞추려면 최소 폭을 준다.
        Instant spokenAt = detection.spokenAt();
        TranscriptRow transcript = mapper.upsertTranscript(
                sessionId,
                toUpsert(session, new TranscriptItem(
                        detection.rawText(),
                        spokenAt,
                        spokenAt.plusMillis(1)
                ))
        );

        long promiseId = requireId(mapper.insertPromise(
                session.streamerId(),
                sessionId,
                transcript.transcriptId(),
                detection.content(),
                detection.dueDate()
        ), "약속");

        mapper.insertBriefing(sessionId, "PROMISE", detection.briefingMessage());
        return promiseId;
    }

    private AnalysisSessionRef requireSession(long sessionId) {
        return mapper.findSessionRef(sessionId)
                .orElseThrow(BroadcastSessionException::notFound);
    }

    /**
     * 벽시계 시각을 방송 시작 기준 밀리초로 바꾼다(명세 4.2).
     *
     * <p>방송 시작 직전에 잡힌 발화는 음수가 나올 수 있는데 ck_transcripts_start_ms가
     * 0 이상을 요구한다. 발화를 버리는 대신 0으로 당긴다. 끝 시각도 함께 밀어
     * end_ms > start_ms를 지킨다.
     */
    private TranscriptUpsert toUpsert(AnalysisSessionRef session, TranscriptItem item) {
        long startMs = Math.max(0L, millisSinceStart(session.startedAt(), item.startedAt()));
        long endMs = Math.max(startMs + 1, millisSinceStart(session.startedAt(), item.endedAt()));
        return new TranscriptUpsert(startMs, endMs, item.text());
    }

    private long millisSinceStart(Instant sessionStartedAt, Instant at) {
        return Duration.between(sessionStartedAt, at).toMillis();
    }

    private long requireId(Long id, String what) {
        if (id == null) {
            throw new IllegalStateException(what + " INSERT 결과에 ID가 없습니다.");
        }
        return id;
    }
}
