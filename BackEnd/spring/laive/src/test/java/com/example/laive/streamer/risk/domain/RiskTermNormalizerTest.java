package com.example.laive.streamer.risk.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 서버 {@code app/detect/normalize.py}와 결과가 같은지 확인한다.
 *
 * <p>아래 기대값은 파이썬 구현을 실제로 돌려 받아 적은 것이다. 파이썬 쪽을 고치면
 * 다시 돌려서 이 표를 갱신한다.
 *
 * <p>어긋나도 매칭이 틀리지는 않는다 — AI 서버는 원문을 받아 자기 구현으로 다시
 * 정규화한다. 어긋남의 피해는 등록 검증과 중복 판정 선에서 멈춘다.
 */
class RiskTermNormalizerTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" -> \"{1}\"")
    @CsvSource(value = {
            // 그대로 남는 것
            "씨발|씨발",
            "안녕하세요|안녕하세요",
            "가나다|가나다",
            "시발점|시발점",
            "미친듯|미친듯",
            "a1b2|a1b2",
            "ㅅㅂ|ㅅㅂ",
            // 공백·문장부호 제거
            "씨 발!|씨발",
            "밤에 혼자 뭐 하세요|밤에혼자뭐하세요",
            "아!|아",
            // 늘여쓰기 축약: 받침 없는 'ㅇ+모음'이 직전 모음을 반복하면 지운다
            "씨이이발|씨발",
            "띠이바|띠바",
            "병신이래요|병신래요",
            // 받침이 있으면 늘여쓰기가 아니다
            "안녕|안녕",
            // 3연속 이상은 2개로
            "개새끼끼끼|개새끼끼",
            // 대문자는 내린다
            "ABC|abc",
            "Hello World|helloworld",
    }, delimiter = '|')
    void 파이썬_구현과_같은_결과를_낸다(String input, String expected) {
        assertThat(RiskTermNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void 남는_글자가_없으면_빈_문자열이다() {
        assertThat(RiskTermNormalizer.normalize("")).isEmpty();
        assertThat(RiskTermNormalizer.normalize(" ")).isEmpty();
        assertThat(RiskTermNormalizer.normalize("!!!")).isEmpty();
        assertThat(RiskTermNormalizer.normalize(null)).isEmpty();
    }

    /**
     * 같은 글자 반복은 2개로 줄어든다. 길이 검증을 이 결과로 해야 하는 이유이기도 하다 —
     * 원문이 400자여도 정규화하면 2자가 될 수 있다.
     */
    @Test
    void 같은_글자_반복은_두_개로_줄어든다() {
        assertThat(RiskTermNormalizer.normalize("가".repeat(400))).isEqualTo("가가");
    }
}
