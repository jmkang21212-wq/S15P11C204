"""시청자 가명화 검증. 원본 식별자가 Spring으로 새어 나가지 않는 것이 요점이다."""
import hashlib

import pytest

from chatcollector import hashing


@pytest.fixture(autouse=True)
def fixed_salt(monkeypatch):
    """소금을 빈 값으로 고정한다.

    config가 load_dotenv로 AI/.env를 읽으므로 소금을 설정한 개발자의 환경에서는
    실제 값이 들어온다. 그대로 두면 기대 해시를 손으로 적은 테스트가 그 사람
    로컬에서만 깨진다 — 코드가 아니라 환경 때문에 실패하는 테스트가 된다.
    """
    monkeypatch.setattr(hashing, 'VIEWER_HASH_SALT', '')


def test_같은_시청자는_항상_같은_해시다():
    # viewer_profiles가 한 행으로 모이려면 이 성질이 필요하다.
    assert hashing.viewer_hash('abc123') == hashing.viewer_hash('abc123')


def test_다른_시청자는_다른_해시다():
    assert hashing.viewer_hash('abc123') != hashing.viewer_hash('abc124')


def test_해시에_원본_식별자가_남지_않는다():
    assert 'abc123' not in hashing.viewer_hash('abc123')


def test_스키마_예시_형식을_따른다():
    # V1 주석의 예시가 "sha256:a91c..."다. viewer_hash는 VARCHAR(128).
    value = hashing.viewer_hash('abc123')

    assert value.startswith('sha256:')
    assert len(value) == len('sha256:') + 64
    assert len(value) <= 128


def test_소금이_해시를_바꾼다(monkeypatch):
    plain = hashlib.sha256(b'abc123').hexdigest()
    assert hashing.viewer_hash('abc123') == 'sha256:' + plain

    monkeypatch.setattr(hashing, 'VIEWER_HASH_SALT', 'pepper')
    salted = hashlib.sha256(b'pepperabc123').hexdigest()

    assert hashing.viewer_hash('abc123') == 'sha256:' + salted
