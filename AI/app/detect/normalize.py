import re

# 한글 음절, 호환 자모(ㅅㅂ 같은 초성 표기), 영소문자, 숫자만 남긴다.
_KEEP = re.compile(r"[0-9a-z가-힣ㄱ-ㆎ]")

_HANGUL_BASE = 0xAC00
_HANGUL_COUNT = 11172
_CHO_IEUNG = 11  # 초성 'ㅇ'의 인덱스


def _syllable(ch: str) -> tuple[int, int, int] | None:
    """한글 음절이면 (초성, 중성, 종성) 인덱스, 아니면 None."""
    code = ord(ch) - _HANGUL_BASE
    if not 0 <= code < _HANGUL_COUNT:
        return None
    return code // 588, (code % 588) // 28, code % 28


def normalize(text: str) -> tuple[str, list[int]]:
    """(정규화된 문자열, 각 글자의 원문 인덱스)를 돌려준다.

    처리하는 것:
      1. 공백/문장부호 제거      "씨 발!"   -> "씨발"
      2. 늘여쓰기 축약           "씨이이발" -> "씨발"
      3. 같은 글자 3연속 이상 축약 "개새끼끼끼" -> "개새끼끼"
    """
    out: list[str] = []
    idx: list[int] = []
    prev_vowel: int | None = None

    for i, ch in enumerate(text.lower()):
        if not _KEEP.match(ch):
            continue

        parts = _syllable(ch)
        if parts is not None:
            cho, vowel, jong = parts
            # 받침 없는 'ㅇ+모음'이 직전 모음을 반복하면 늘여쓰기로 본다.
            # "씨이이발"의 '이'가 여기 걸린다. "안녕"의 '안'은 받침이 있어 안 걸린다.
            if cho == _CHO_IEUNG and jong == 0 and vowel == prev_vowel:
                continue

        if len(out) >= 2 and out[-1] == ch and out[-2] == ch:
            continue

        out.append(ch)
        idx.append(i)
        prev_vowel = parts[1] if parts is not None else None

    return "".join(out), idx
