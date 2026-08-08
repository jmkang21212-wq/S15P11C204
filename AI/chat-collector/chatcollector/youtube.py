"""YouTube Live collector using the provider's polling contracts."""
import logging
import threading
import time
from datetime import datetime

import httpx

from chatcollector.config import HEARTBEAT_SEC, VIEWER_POLL_SEC, YOUTUBE_API_BASE
from chatcollector.hashing import viewer_hash
from chatcollector.spring import PlatformSpringClient, iso

log = logging.getLogger(__name__)


class YouTubeCollectorSession:
    def __init__(self, platform_session_id: int, spring: PlatformSpringClient):
        self.session_id = platform_session_id
        self._spring = spring
        self._spec = spring.spec
        self._stop = threading.Event()
        self._threads: list[threading.Thread] = []
        self._client = httpx.Client(
            base_url=YOUTUBE_API_BASE,
            timeout=10.0,
            headers={'Authorization': f"Bearer {self._spec['accessToken']}"},
        )
        self.received = 0
        self.sent = 0
        self.dropped = 0
        self._state = 'STOPPED'
        self._last_viewer_count = None
        self._last_viewer_at = None

    def start(self) -> None:
        if self._threads:
            return
        if not self._spec.get('liveChatId'):
            raise RuntimeError('YouTube active broadcast has no liveChatId')
        self._state = 'RUNNING'
        self._threads = [
            threading.Thread(
                target=self._chat_loop,
                daemon=True,
                name=f'youtube-chat-{self.session_id}',
            ),
            threading.Thread(
                target=self._viewer_loop,
                daemon=True,
                name=f'youtube-viewers-{self.session_id}',
            ),
            # 하트비트는 별도 스레드여야 한다. 채팅 폴링 루프에 얹으면 유튜브가
            # 주는 pollingIntervalMillis에 보고 주기가 끌려가고, 채팅 루프가
            # 종료되는 순간(채팅 종료 판정) 상태 보고도 함께 멈춘다.
            threading.Thread(
                target=self._heartbeat_loop,
                daemon=True,
                name=f'youtube-heartbeat-{self.session_id}',
            ),
        ]
        for thread in self._threads:
            thread.start()
        self._report_status()

    def stop(self, grace_period_sec: float = 30.0) -> None:
        self._stop.set()
        for thread in self._threads:
            thread.join(timeout=grace_period_sec)
        self._threads = []
        self._state = 'STOPPED'
        self._report_status()
        self._client.close()

    def snapshot(self) -> dict:
        viewer_age = (
            None if self._last_viewer_at is None
            else time.time() - self._last_viewer_at
        )
        return {
            'platformSessionId': self.session_id,
            'platformCode': 'YOUTUBE',
            'state': self._state,
            'received': self.received,
            'sent': self.sent,
            'dropped': self.dropped,
            'lastViewerCount': self._last_viewer_count,
            'lastViewerAgeSec': viewer_age,
        }

    def _chat_loop(self) -> None:
        page_token = None
        backoff = 1.0
        while not self._stop.is_set():
            try:
                params = {
                    'part': 'id,snippet,authorDetails',
                    'liveChatId': self._spec['liveChatId'],
                    'maxResults': 200,
                }
                if page_token:
                    params['pageToken'] = page_token
                response = self._client.get('/youtube/v3/liveChat/messages', params=params)
                response.raise_for_status()
                payload = response.json()
                items = self._normalize_messages(payload.get('items', []))
                if items:
                    self._spring.send_chat_batch(self.session_id, items)
                    self.sent += len(items)
                page_token = payload.get('nextPageToken', page_token)
                interval = max(payload.get('pollingIntervalMillis', 1000) / 1000, 0.5)
                backoff = 1.0
                self._stop.wait(interval)
            except httpx.HTTPStatusError as exception:
                if self._is_terminal_chat_error(exception.response):
                    log.info('YouTube live chat ended: platformSessionId=%s', self.session_id)
                    self._mark_provider_ended()
                    return
                if exception.response.status_code == 401:
                    self._refresh_access_token()
                log.warning('YouTube chat request failed: %s', exception)
                self._stop.wait(self._retry_delay(exception.response, backoff))
                backoff = min(backoff * 2, 30.0)
            except Exception as exception:  # noqa: BLE001
                log.warning('YouTube chat collection failed: %s', exception)
                self._stop.wait(backoff)
                backoff = min(backoff * 2, 30.0)

    def _normalize_messages(self, rows: list[dict]) -> list[dict]:
        result = []
        for row in rows:
            snippet = row.get('snippet') or {}
            author = row.get('authorDetails') or {}
            message = snippet.get('displayMessage')
            published_at = snippet.get('publishedAt')
            author_id = author.get('channelId')
            if not message or not published_at or not author_id:
                continue
            try:
                sent_at = datetime.fromisoformat(
                    published_at.replace('Z', '+00:00')).timestamp()
            except ValueError:
                sent_at = time.time()
            result.append({
                'viewerHash': viewer_hash('YOUTUBE:' + author_id),
                'message': message,
                'sentAt': iso(sent_at),
                'platformMessageId': row.get('id'),
                'messageType': self._message_type(snippet.get('type')),
                'amount': self._amount(snippet),
                'currency': (snippet.get('superChatDetails') or {}).get('currency'),
                'providerEventType': snippet.get('type'),
                # 열성 시청자 TOP5(FR-F-03) 표기용. 역할 어휘는 치지직과 맞춘다.
                'nickname': author.get('displayName'),
                'userRole': self._user_role(author),
            })
        self.received += len(result)
        return result

    @staticmethod
    def _message_type(provider_type: str | None) -> str:
        return {
            'superChatEvent': 'DONATION',
            'superStickerEvent': 'DONATION',
            'newSponsorEvent': 'MEMBERSHIP',
            'memberMilestoneChatEvent': 'MEMBERSHIP',
            'membershipGiftingEvent': 'GIFT',
            'giftMembershipReceivedEvent': 'GIFT',
        }.get(provider_type, 'TEXT')

    @staticmethod
    def _user_role(author: dict) -> str | None:
        """치지직 역할 어휘(streamer/manager)로 통일한다. 열성 집계 제외 판정용."""
        if author.get('isChatOwner'):
            return 'streamer'
        if author.get('isChatModerator'):
            return 'manager'
        return None

    @staticmethod
    def _amount(snippet: dict):
        micros = (snippet.get('superChatDetails') or {}).get('amountMicros')
        if micros is None:
            return None
        try:
            return int(micros) / 1_000_000
        except (TypeError, ValueError):
            return None

    def _viewer_loop(self) -> None:
        video_id = self._spec.get('externalBroadcastId')
        if not video_id:
            return
        while not self._stop.wait(VIEWER_POLL_SEC):
            try:
                response = self._client.get(
                    '/youtube/v3/videos',
                    params={'part': 'liveStreamingDetails', 'id': video_id},
                )
                response.raise_for_status()
                rows = response.json().get('items', [])
                if not rows:
                    continue
                raw_count = (rows[0].get('liveStreamingDetails') or {}).get(
                    'concurrentViewers')
                if raw_count is None:
                    continue
                measured_at = time.time()
                count = int(raw_count)
                self._spring.send_viewer_metrics(self.session_id, [{
                    'measuredAt': iso(measured_at),
                    'viewerCount': count,
                }])
                self._last_viewer_count = count
                self._last_viewer_at = measured_at
            except httpx.HTTPStatusError as exception:
                if exception.response.status_code == 401:
                    self._refresh_access_token()
                log.warning('YouTube viewer metric request failed: %s', exception)
            except Exception as exception:  # noqa: BLE001
                log.warning('YouTube viewer metric failed: %s', exception)

    def _refresh_access_token(self) -> None:
        spec = self._spring.refresh_spec()
        self._spec = spec
        self._client.headers['Authorization'] = f"Bearer {spec['accessToken']}"

    @staticmethod
    def _is_terminal_chat_error(response: httpx.Response) -> bool:
        if response.status_code == 404:
            return True
        if response.status_code != 403:
            return False
        try:
            errors = (response.json().get('error') or {}).get('errors') or []
        except ValueError:
            return False
        terminal_reasons = {'liveChatEnded', 'liveChatNotFound'}
        return any(error.get('reason') in terminal_reasons for error in errors)

    @staticmethod
    def _retry_delay(response: httpx.Response, fallback: float) -> float:
        retry_after = response.headers.get('Retry-After')
        if retry_after is None:
            return fallback
        try:
            return max(float(retry_after), 0.5)
        except ValueError:
            return fallback

    def _heartbeat_loop(self) -> None:
        """상태를 주기적으로 보고한다 (session.py의 같은 이름 루프와 대응).

        이것이 없으면 start()의 단발 보고 하나로 끝나서, Spring이
        laive.collector.heartbeat-timeout(30초)을 넘긴 값을 STOPPED로 강등한다.
        채팅은 정상 수집되는데 콘솔의 수집기 표시만 꺼져 실제와 어긋났다.
        """
        while not self._stop.wait(HEARTBEAT_SEC):
            self._report_status()

    def _report_status(self) -> None:
        try:
            self._spring.report_status(self.session_id, self._state, time.time())
        except Exception as exception:  # noqa: BLE001
            log.warning('YouTube collector status report failed: %s', exception)

    def _mark_provider_ended(self) -> None:
        self._state = 'STOPPED'
        self._stop.set()
        self._report_status()
