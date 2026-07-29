"""시청자 식별자 가명화.

플랫폼의 원본 사용자 식별자는 Spring으로 보내지 않는다(API 명세 13.3.1).
해시는 수집기에서만 만들고 Spring은 해시만 받는다.
"""
import hashlib

from chatcollector.config import VIEWER_HASH_SALT

# viewer_profiles.viewer_hash의 예시 형식이 "sha256:a91c..."다(V1 주석).
# 나중에 알고리즘을 바꿔도 기존 행과 구분되도록 접두사를 붙여 저장한다.
_PREFIX = 'sha256:'


def viewer_hash(sender_channel_id: str) -> str:
    """senderChannelId를 viewer_hash로 바꾼다.

    소금을 앞에 붙이는 이유는 config.VIEWER_HASH_SALT 주석에 적었다.
    같은 시청자는 항상 같은 값이 나와야 viewer_profiles가 한 행으로 모인다.
    """
    digest = hashlib.sha256(
        (VIEWER_HASH_SALT + sender_channel_id).encode('utf-8')
    ).hexdigest()
    return _PREFIX + digest
