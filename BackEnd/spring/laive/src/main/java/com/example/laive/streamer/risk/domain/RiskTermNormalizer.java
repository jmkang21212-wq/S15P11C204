package com.example.laive.streamer.risk.domain;

import java.util.Locale;

/**
 * AI 서버의 {@code app/detect/normalize.py}를 옮긴 것이다.
 *
 * <p><b>왜 Spring에도 필요한가</b> — 등록 검증과 중복 판정을 정규화된 형태로 해야 하기
 * 때문이다. "두 글자 미만 거부" 규칙이 특히 그렇다. 원문 길이로 재면 {@code "아!"}(2자)가
 * 통과하는데 정규화하면 {@code "아"} 한 글자가 되어, 다른 단어에 묻히는 표기를 막겠다는
 * 규칙의 의도가 무너진다.
 *
 * <p><b>중요: 이 구현이 매칭의 정본은 아니다.</b> AI 서버는 티켓으로 받은 원문(display)을
 * 자기 {@code normalize()}로 다시 처리해 automaton을 만든다. 그래서 이쪽 구현이 파이썬과
 * 어긋나더라도 <b>매칭이 틀리지는 않고</b> 검증·중복 판정만 어색해진다. 두 구현이 영원히
 * 같기를 기대하는 대신, 어긋났을 때의 피해를 그 정도로 묶어 둔 선택이다.
 *
 * <p>파이썬 쪽을 고치면 여기도 함께 고치고 {@code RiskTermNormalizerTest}의 공통 케이스를
 * 갱신한다.
 */
public final class RiskTermNormalizer {

    private static final int HANGUL_BASE = 0xAC00;
    private static final int HANGUL_COUNT = 11172;
    /** 초성 'ㅇ'의 인덱스. */
    private static final int CHO_IEUNG = 11;

    private RiskTermNormalizer() {
    }

    /**
     * 한글 음절, 호환 자모(ㅅㅂ 같은 초성 표기), 영소문자, 숫자만 남기고
     * 늘여쓰기와 3연속 반복을 줄인다.
     *
     * <pre>
     *   "씨 발!"    -> "씨발"     (공백·문장부호 제거)
     *   "씨이이발"  -> "씨발"     (늘여쓰기 축약)
     *   "개새끼끼끼" -> "개새끼끼" (3연속 이상 축약)
     * </pre>
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder(text.length());
        // 직전 글자의 중성. 한글 음절이 아니었으면 -1.
        int previousVowel = -1;

        // Locale.ROOT를 명시한다. 기본 로케일이 터키어면 'I'가 'ı'로 내려가
        // 서버 로케일에 따라 정규화 결과가 달라진다.
        for (char raw : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (!isKept(raw)) {
                continue;
            }

            int vowel = -1;
            int code = raw - HANGUL_BASE;
            if (0 <= code && code < HANGUL_COUNT) {
                int cho = code / 588;
                vowel = (code % 588) / 28;
                int jong = code % 28;

                // 받침 없는 'ㅇ+모음'이 직전 모음을 반복하면 늘여쓰기로 본다.
                // "씨이이발"의 '이'가 여기 걸린다. "안녕"의 '안'은 받침이 있어 안 걸린다.
                if (cho == CHO_IEUNG && jong == 0 && vowel == previousVowel) {
                    continue;
                }
            }

            int length = out.length();
            if (length >= 2 && out.charAt(length - 1) == raw && out.charAt(length - 2) == raw) {
                continue;
            }

            out.append(raw);
            previousVowel = vowel;
        }

        return out.toString();
    }

    /** 파이썬의 {@code _KEEP = re.compile(r"[0-9a-z가-힣ㄱ-ㆎ]")}와 같은 범위다. */
    private static boolean isKept(char ch) {
        return (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'z')
                || (ch >= '가' && ch <= '힣')
                || (ch >= 'ㄱ' && ch <= 'ㆎ');
    }
}
