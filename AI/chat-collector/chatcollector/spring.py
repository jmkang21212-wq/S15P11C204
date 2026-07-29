"""Spring 내부 API 클라이언트 (API 명세 13.1, 13.3).

Spring이 느리거나 죽어 있어도 채팅 수신 자체는 계속돼야 하므로, 이 모듈을
호출하는 쪽은 전송 실패를 수집 중단으로 다루지 않는다.
"""
import logging
import time
import uuid
from datetime import datetime, timezone
from typing import Optional

import httpx

from chatcollector.config import (
    SPRING_BASE_URL,
    SPRING_MAX_RETRIES,
    SPRING_TIMEOUT,
    SPRING_TOKEN,
)

log = logging.getLogger(__name__)


def iso(ts: float) -> str:
    """epoch 초 -> ISO8601 UTC. Java의 Instant.parse()가 그대로 받는다."""
    return (datetime.fromtimestamp(ts, timezone.utc)
            .isoformat(timespec='milliseconds')
            .replace('+00:00', 'Z'))


class SpringUnavailable(RuntimeError):
    """재시도를 다 쓰고도 Spring에 닿지 못했다."""


class SpringRejected(RuntimeError):
    """Spring이 4xx로 거절했다. 같은 요청을 다시 보내도 결과가 같다."""


class SpringClient:
    def __init__(self, base_url: str = SPRING_BASE_URL, token: str = SPRING_TOKEN):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = f'Bearer {token}'
        self._client = httpx.Client(base_url=base_url, timeout=SPRING_TIMEOUT,
                                    headers=headers)

    def close(self) -> None:
        self._client.close()

    # --- 조회 ---

    def fetch_credentials(self, session_id: int) -> tuple[str, str]:
        """치지직 Access Token과 채널 ID를 받아온다.

        핸드셰이크마다 호출한다. 토큰을 들고 있다가 만료되면 재핸드셰이크가
        계속 401로 실패해 남은 방송 내내 수집이 죽는다.
        """
        body = self._request(
            'GET', f'/internal/v1/sessions/{session_id}/collector-credentials')
        data = body['data']
        return data['accessToken'], data['channelId']

    # --- 저장 ---

    def send_chat_batch(self, session_id: int, items: list[dict]) -> dict:
        """채팅 배치를 저장한다(명세 13.3.1)."""
        return self._request(
            'POST',
            f'/internal/v1/sessions/{session_id}/chat-messages:batch',
            json={'items': items},
            # 배치 하나에 ID 하나. 재시도는 같은 ID를 다시 써야 Spring이
            # 중복 저장을 막을 수 있다(_request가 재시도 내내 재사용한다).
            request_id=str(uuid.uuid4()),
        )['data']

    def send_viewer_metrics(self, session_id: int, items: list[dict]) -> dict:
        """시청자 수 배치를 저장한다(명세 13.3.2)."""
        return self._request(
            'POST',
            f'/internal/v1/sessions/{session_id}/viewer-metrics:batch',
            json={'items': items},
            request_id=str(uuid.uuid4()),
        )['data']

    # --- 상태 ---

    def report_status(self, session_id: int, status: str, occurred_at: float) -> None:
        """연결 상태를 보고한다. 하트비트를 겸한다."""
        self._request(
            'POST',
            f'/internal/v1/sessions/{session_id}/collector-status',
            json={'status': status, 'occurredAt': iso(occurred_at)},
        )

    def report_gap(
        self,
        session_id: int,
        started_at: float,
        ended_at: Optional[float],
        reason: str,
    ) -> None:
        """수집 공백 구간을 보고한다.

        끊긴 시점에 ended_at 없이 먼저 보내고, 복구한 뒤 같은 started_at으로
        다시 보내 닫는다. 먼저 열어두는 이유는 수집기가 공백 도중에 죽어도
        "여기서부터 못 봤다"가 남아야 하기 때문이다.
        """
        self._request(
            'POST',
            f'/internal/v1/sessions/{session_id}/collection-gaps',
            json={
                'startedAt': iso(started_at),
                'endedAt': iso(ended_at) if ended_at is not None else None,
                'reason': reason,
            },
        )

    # --- 내부 ---

    def _request(
        self,
        method: str,
        path: str,
        json: Optional[dict] = None,
        request_id: Optional[str] = None,
    ) -> dict:
        headers = {}
        if request_id:
            headers['X-Request-Id'] = request_id

        # 최초 시도 1회 + 재시도 SPRING_MAX_RETRIES회. 로그에는 총 횟수를 찍는다 —
        # 분모에 재시도 수만 넣으면 마지막 시도가 "4/3"으로 보인다.
        total_attempts = SPRING_MAX_RETRIES + 1

        for attempt in range(total_attempts):
            try:
                response = self._client.request(method, path, json=json, headers=headers)
                if response.status_code < 300:
                    return response.json() if response.content else {}
                if response.status_code == 409:
                    # 같은 X-Request-Id가 처리 중이다. 잠시 뒤 재시도하면
                    # 완료된 응답을 그대로 돌려받는다.
                    log.debug('Spring 처리 중 %s (%d/%d)',
                              path, attempt + 1, total_attempts)
                elif 400 <= response.status_code < 500:
                    raise SpringRejected(
                        f'{response.status_code} {path}: {response.text[:200]}')
                else:
                    log.warning('Spring %s 응답 %s (%d/%d)',
                                path, response.status_code,
                                attempt + 1, total_attempts)
            except httpx.HTTPError as exception:
                log.warning('Spring 요청 실패 %s: %s (%d/%d)',
                            path, exception, attempt + 1, total_attempts)

            if attempt < SPRING_MAX_RETRIES:
                time.sleep(0.5 * (2 ** attempt))  # 0.5 -> 1 -> 2초

        raise SpringUnavailable(f'{method} {path}')
