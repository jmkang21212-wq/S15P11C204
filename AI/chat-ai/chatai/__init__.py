"""
chat-ai 공통 패키지.

ChatClassifier는 onnxruntime·transformers(수백 MB)를 필요로 하므로 **지연 import** 한다.
리플레이 시뮬레이터처럼 cmd_type이나 text_norm만 쓰는 쪽은 무거운 추론 의존성 없이
동작해야 하기 때문이다. `from chatai import ChatClassifier` 시점에만 실제로 로드된다.
"""
from .text_norm import normalize

__all__ = ['ChatClassifier', 'LABELS', 'normalize']


def __getattr__(name):          # PEP 562 — 실제 사용 시점에만 로드
    if name in ('ChatClassifier', 'LABELS'):
        from . import classifier
        return getattr(classifier, name)
    raise AttributeError(f'module {__name__!r} has no attribute {name!r}')
