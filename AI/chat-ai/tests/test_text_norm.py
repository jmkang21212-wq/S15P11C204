"""전처리 회귀 테스트 — 학습/서빙 불일치 방어선.

    python -m pytest tests/ -q     또는     python tests/test_text_norm.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatai.text_norm import TEST_CASES, normalize


def test_vectors():
    for src, expected in TEST_CASES:
        assert normalize(src) == expected, f'{src!r} -> {normalize(src)!r} (기대 {expected!r})'


def test_idempotent():
    """두 번 적용해도 결과가 같아야 한다 (서버가 내부에서 한 번 더 돌려도 안전)."""
    for src, _ in TEST_CASES:
        once = normalize(src)
        assert normalize(once) == once


if __name__ == '__main__':
    test_vectors()
    test_idempotent()
    print(f'{len(TEST_CASES)}건 통과 (멱등성 포함)')
