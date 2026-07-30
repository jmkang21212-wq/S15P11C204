package com.example.laive.report.service;

import com.example.laive.report.domain.ReportStatus;
import com.example.laive.report.domain.SentimentCounts;
import com.example.laive.report.domain.SessionReport;
import com.example.laive.report.exception.ReportException;
import com.example.laive.report.repository.SessionReportQueryRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportQueryServiceTest {
    private final SessionReportQueryRepository repository = mock(SessionReportQueryRepository.class);
    private final ReportQueryService service = new ReportQueryService(repository);

    @Test
    void processingReportReturnsOnlyProgressData() {
        when(repository.existsOwnedSession(1, 2)).thenReturn(true);
        when(repository.findReport(1, 2)).thenReturn(Optional.of(report(ReportStatus.PROCESSING)));
        when(repository.findTriggers(2)).thenReturn(List.of());

        var result = service.get(1, 2);

        assertThat(result.progress()).isEqualTo(0.7);
        assertThat(result.viewerTimeline()).isEmpty();
    }

    @Test
    void failedReportIsPublicFailure() {
        when(repository.existsOwnedSession(1, 2)).thenReturn(true);
        when(repository.findReport(1, 2)).thenReturn(Optional.of(report(ReportStatus.FAILED)));

        assertThatThrownBy(() -> service.get(1, 2))
                .isInstanceOf(ReportException.class)
                .hasMessageContaining("실패");
    }

    private SessionReport report(ReportStatus status) {
        return new SessionReport(
                3, 2, "title", Instant.EPOCH, Instant.EPOCH.plusSeconds(60),
                status, null, 10, 20, 30,
                new SentimentCounts(0, 0, 0, 0, 0, 0, 0, 0, 0),
                null
        );
    }
}
