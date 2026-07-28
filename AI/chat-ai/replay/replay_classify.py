"""
리플레이 + 감정 분류 통합 확인

replay.py가 흘려보내는 채팅을 받아 실시간으로 감정 분류하고,
슬라이딩 윈도우의 여론 분포와 호응도를 주기적으로 출력한다.

이건 앞으로 만들 수신 서비스의 축소판이다. 실제 서비스는 여기에
Redis 저장 · 하이라이트 트리거 · LLM 요약 · Spring push가 더 붙는다.
지금은 "채팅을 받아서 분류가 제대로 되는가"만 눈으로 확인한다.

사용법 (터미널 2개):
    python replay/replay.py --video 14194693 --speed 60
    python replay/replay_classify.py

배속을 올리면 긴 방송을 짧게 훑을 수 있다. 60배속이면 5시간 방송이 5분.
"""
import argparse
import json
import os
import sys
import time
from collections import Counter, deque

import requests
from websocket import WebSocket, WebSocketTimeoutException

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatai.classifier import LABELS, ChatClassifier
from chatai.cmd_type import CHZZK_CHAT_CMD

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=8899)
parser.add_argument('--model', default='models/kcelectra-chat-8emo-onnx')
parser.add_argument('--int8', action='store_true', help='int8 모델 사용 (2배 빠름, 정확도 소폭 하락)')
parser.add_argument('--window', type=int, default=180, help='슬라이딩 윈도우 길이(초)')
parser.add_argument('--every', type=float, default=3.0, help='출력 주기(초)')
parser.add_argument('--flush', type=float, default=1.0, help='분류 배치 주기(초)')
parser.add_argument('--seconds', type=int, default=0, help='실행 시간(초). 0이면 재생이 끝날 때까지')
args = parser.parse_args()

print('모델 로드 중...')
clf = ChatClassifier(args.model, quantized=args.int8)

sock = WebSocket()
sock.connect(f'ws://127.0.0.1:{args.port}/chat', timeout=10)
sock.send(json.dumps({
    'ver': '2', 'svcid': 'game', 'cid': 'replay',
    'cmd': CHZZK_CHAT_CMD['connect'], 'tid': 1,
    'bdy': {'uid': 'classify', 'devType': 2001, 'accTkn': 'dummy', 'auth': 'READ'},
}))
resp = json.loads(sock.recv())
assert resp['cmd'] == 10100, f'핸드셰이크 실패: {resp}'
print(f"접속 완료 (sid={resp['bdy']['sid']}) — 재생 시작\n")

STATUS_URL = f'http://127.0.0.1:{args.port}/polling/v2/channels/x/live-status'

window = deque()          # (수신시각, 확률dict, uid)
pending = []              # 아직 분류 안 한 (텍스트, uid)
total = 0
started = time.time()
last_flush = last_print = time.time()
viewers = 0
last_poll = 0.0


def flush():
    """모아둔 채팅을 한 번에 분류해 윈도우에 넣는다. 건당 호출은 낭비다."""
    global pending
    if not pending:
        return
    texts = [t for t, _ in pending]
    now = time.time()
    for (_, uid), r in zip(pending, clf.predict(texts)):
        window.append((now, r['probs'], uid))
    pending = []


def render():
    cutoff = time.time() - args.window
    while window and window[0][0] < cutoff:
        window.popleft()
    if not window:
        return

    dist = {lab: 0.0 for lab in LABELS}
    for _, probs, _ in window:
        for lab, v in probs.items():
            dist[lab] += v
    n = len(window)
    dist = {k: v / n for k, v in dist.items()}
    top = sorted(dist.items(), key=lambda x: -x[1])[:4]

    uniq = len({uid for _, _, uid in window})
    rate = f'{uniq / viewers:.1%}' if viewers else 'n/a'
    elapsed = time.time() - started

    print(f'[{elapsed:6.0f}s] 윈도우 {n:>5}건 · 누적 {total:>7,}건 · '
          f'유니크 {uniq:>4}명 · 시청자 {viewers:>6,} · 호응도 {rate:>6}')
    print('          ' + '  '.join(f'{k} {v:.0%}' for k, v in top))


try:
    while True:
        if args.seconds and time.time() - started > args.seconds:
            break

        sock.settimeout(0.3)
        try:
            raw = sock.recv()
        except WebSocketTimeoutException:
            raw = None
        except Exception:
            break

        if raw:
            frame = json.loads(raw)
            cmd = frame.get('cmd')
            if cmd == CHZZK_CHAT_CMD['ping']:
                sock.send(json.dumps({'ver': '2', 'cmd': CHZZK_CHAT_CMD['pong']}))
            elif cmd in (CHZZK_CHAT_CMD['chat'], CHZZK_CHAT_CMD['donation']):
                for item in frame['bdy']:
                    msg = item.get('msg')
                    if msg:
                        pending.append((msg, item.get('uid') or item.get('userId') or '?'))
                        total += 1

        now = time.time()
        if now - last_flush >= args.flush:
            flush()
            last_flush = now
        if now - last_print >= args.every:
            if now - last_poll >= 10:
                try:
                    c = requests.get(STATUS_URL, timeout=2).json()['content']
                    viewers = c.get('concurrentUserCount') or 0
                    if c.get('status') == 'CLOSE' and not pending and not window:
                        print('\n재생 종료')
                        break
                except Exception:
                    pass
                last_poll = now
            render()
            last_print = now

except KeyboardInterrupt:
    print('\n중단')

flush()
render()
sock.close()
print(f'\n총 {total:,}건 분류 완료 ({time.time() - started:.0f}초)')
