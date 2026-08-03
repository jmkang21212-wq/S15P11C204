package com.example.laive.streamer.risk.service;

import com.example.laive.streamer.risk.domain.RiskTermKind;
import com.example.laive.streamer.risk.domain.RiskTermLimits;
import com.example.laive.streamer.risk.domain.RiskTermSeverity;
import com.example.laive.streamer.risk.domain.StreamerRiskTerm;
import com.example.laive.streamer.risk.exception.RiskTermException;
import com.example.laive.streamer.risk.repository.StreamerRiskTermRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamerRiskTermServiceTest {

    private static final long STREAMER = 7L;

    private StreamerRiskTermRepository repository;
    private RiskTermBroadcaster broadcaster;
    private StreamerRiskTermService service;

    @BeforeEach
    void setUp() {
        repository = mock(StreamerRiskTermRepository.class);
        broadcaster = mock(RiskTermBroadcaster.class);
        when(repository.findAll(anyLong())).thenReturn(List.of());
        when(repository.findByTerm(anyLong(), anyString())).thenReturn(List.of());
        when(repository.countByKind(anyLong(), any())).thenReturn(0L);
        service = new StreamerRiskTermService(repository, broadcaster);
    }

    /** 매칭이 정규화된 문자열 위에서 일어나므로 저장도 그 형태여야 한다. 원문은 화면 표시용으로 함께 남긴다. */
    @Test
    void 정규화해서_저장하고_원문도_보관한다() {
        service.register(STREAMER, RiskTermKind.ALLOW, "  씨 발!  ", null);

        verify(repository).insert(eq(STREAMER), eq(RiskTermKind.ALLOW),
                eq("씨발"), eq("씨 발!"), eq(null));
    }

    @Test
    void 금칙어_등급을_그대로_저장한다() {
        service.register(STREAMER, RiskTermKind.BLOCK, "개새기", RiskTermSeverity.HIGH);

        verify(repository).insert(anyLong(), any(), anyString(), anyString(),
                eq(RiskTermSeverity.HIGH));
    }

    /** 허용어는 감지를 없애는 쪽이라 등급이라는 개념이 없다. 실려 와도 버린다. */
    @Test
    void 허용어의_등급은_버린다() {
        service.register(STREAMER, RiskTermKind.ALLOW, "씨발", RiskTermSeverity.HIGH);

        verify(repository).insert(anyLong(), any(), anyString(), anyString(), eq(null));
    }

    /**
     * 한 글자는 다른 단어에 묻힌다(terms.yaml 설계 원칙 3). "아!"처럼 정규화 후 한 글자가
     * 되는 입력도 막아야 해서 원문이 아니라 정규화 결과로 잰다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"아", "아!", "가 ", "1"})
    void 정규화_후_두_글자_미만은_거절한다(String input) {
        assertThatThrownBy(() -> service.register(STREAMER, RiskTermKind.BLOCK, input, null))
                .isInstanceOf(RiskTermException.class)
                .hasMessageContaining("두 글자");
        verify(repository, never()).insert(anyLong(), any(), anyString(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "!!!", "@@@"})
    void 남는_글자가_없으면_거절한다(String input) {
        assertThatThrownBy(() -> service.register(STREAMER, RiskTermKind.BLOCK, input, null))
                .isInstanceOf(RiskTermException.class);
        verify(repository, never()).insert(anyLong(), any(), anyString(), anyString(), any());
    }

    /** display 컬럼이 40자라, 정규화가 대부분을 삼키는 긴 입력도 걸러야 INSERT에서 죽지 않는다. */
    @Test
    void 원문이_너무_길면_정규화_결과가_짧아도_거절한다() {
        String longInput = "가".repeat(400);

        assertThatThrownBy(() -> service.register(STREAMER, RiskTermKind.BLOCK, longInput, null))
                .isInstanceOf(RiskTermException.class)
                .hasMessageContaining("40자");
    }

    @Test
    void 같은_종류에_이미_있으면_중복이다() {
        when(repository.findByTerm(STREAMER, "씨발")).thenReturn(List.of(
                new StreamerRiskTerm(1L, RiskTermKind.ALLOW, "씨발", "씨발", null)));

        assertThatThrownBy(() -> service.register(STREAMER, RiskTermKind.ALLOW, "씨발", null))
                .isInstanceOf(RiskTermException.class)
                .hasMessageContaining("이미 등록된");
    }

    /**
     * 같은 표기를 금칙어와 허용어에 동시에 두면 사전에서 빼면서 넣는 모순이 된다.
     * DB의 UNIQUE는 (streamer, kind, term)이라 이 조합을 막지 못한다.
     */
    @Test
    void 반대_종류에_같은_표기가_있으면_거절한다() {
        when(repository.findByTerm(STREAMER, "씨발")).thenReturn(List.of(
                new StreamerRiskTerm(1L, RiskTermKind.ALLOW, "씨발", "씨발", null)));

        assertThatThrownBy(() -> service.register(STREAMER, RiskTermKind.BLOCK, "씨발", null))
                .isInstanceOf(RiskTermException.class)
                .hasMessageContaining("반대쪽 목록");
    }

    @Test
    void 상한을_넘으면_거절한다() {
        when(repository.countByKind(STREAMER, RiskTermKind.BLOCK))
                .thenReturn((long) RiskTermLimits.MAX_PER_KIND);

        assertThatThrownBy(() -> service.register(STREAMER, RiskTermKind.BLOCK, "개새기", null))
                .isInstanceOf(RiskTermException.class)
                .hasMessageContaining(String.valueOf(RiskTermLimits.MAX_PER_KIND));
    }

    /** 상한은 종류별로 따로 센다. 허용어가 가득 차도 금칙어는 등록할 수 있어야 한다. */
    @Test
    void 상한은_종류별로_따로_적용된다() {
        when(repository.countByKind(STREAMER, RiskTermKind.ALLOW))
                .thenReturn((long) RiskTermLimits.MAX_PER_KIND);

        service.register(STREAMER, RiskTermKind.BLOCK, "개새기", null);

        verify(repository).insert(anyLong(), eq(RiskTermKind.BLOCK), anyString(), anyString(), any());
    }

    /** streamerId를 조건에 함께 걸었으므로 0건은 없는 항목이거나 남의 항목이다. 둘을 구분해 알리지 않는다. */
    @Test
    void 없는_항목을_지우면_찾을_수_없다고_답한다() {
        when(repository.delete(STREAMER, 99L)).thenReturn(0);

        assertThatThrownBy(() -> service.remove(STREAMER, 99L))
                .isInstanceOf(RiskTermException.class)
                .hasMessageContaining("등록되지 않은");
        verify(broadcaster, never()).publishAfterCommit(anyLong(), any());
    }

    /** 방송 중 변경이 즉시 반영되려면 등록·삭제 모두 알려야 한다. */
    @Test
    void 등록하면_변경을_알린다() {
        List<StreamerRiskTerm> updated = List.of(
                new StreamerRiskTerm(1L, RiskTermKind.ALLOW, "씨발", "씨발", null));
        when(repository.findAll(STREAMER)).thenReturn(updated);

        assertThat(service.register(STREAMER, RiskTermKind.ALLOW, "씨발", null))
                .isEqualTo(updated);

        ArgumentCaptor<List<StreamerRiskTerm>> sent = ArgumentCaptor.captor();
        verify(broadcaster).publishAfterCommit(eq(STREAMER), sent.capture());
        assertThat(sent.getValue()).isEqualTo(updated);
    }

    @Test
    void 삭제하면_변경을_알린다() {
        when(repository.delete(STREAMER, 1L)).thenReturn(1);

        service.remove(STREAMER, 1L);

        verify(broadcaster).publishAfterCommit(eq(STREAMER), any());
    }
}
