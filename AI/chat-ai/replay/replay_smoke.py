"""
리플레이 시뮬레이터 스모크 테스트 겸 백엔드 접속 예제

실제 치지직 접속 코드(collect.py)와 동일한 절차로 시뮬레이터에 붙는다:
connect(100) → sid 수신(10100) → 최근 채팅 요청(5101) → 프레임 수신 루프

사용법:
    python replay.py --video <videoNo> --speed 0    # 터미널 1
    python replay_smoke.py [--seconds 15]           # 터미널 2
"""
import argparse
import json
import os
import sys
import time

import requests
from websocket import WebSocket, WebSocketTimeoutException

# 패키지 루트를 경로에 추가 — 어느 디렉터리에서 실행해도 chatai를 찾도록
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatai.cmd_type import CHZZK_CHAT_CMD

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=8899)
parser.add_argument('--seconds', type=int, default=15, help='수신 대기 시간')
parser.add_argument('--show', type=int, default=5, help='샘플로 출력할 채팅 수')
args = parser.parse_args()

sock = WebSocket()
sock.connect(f'ws://127.0.0.1:{args.port}/chat', timeout=10)

# 1. 입장 (실서버와 동일한 형식, 인증 토큰은 시뮬레이터가 무시)
sock.send(json.dumps({
    'ver': '2', 'svcid': 'game', 'cid': 'replay',
    'cmd': CHZZK_CHAT_CMD['connect'], 'tid': 1,
    'bdy': {'uid': 'smoke-test', 'devType': 2001, 'accTkn': 'dummy', 'auth': 'READ'},
}))
resp = json.loads(sock.recv())
assert resp['cmd'] == 10100, f'핸드셰이크 실패: {resp}'
sid = resp['bdy']['sid']
print(f'핸드셰이크 OK (sid={sid})')

# 2. 최근 채팅 요청
sock.send(json.dumps({
    'ver': '2', 'svcid': 'game', 'cid': 'replay',
    'cmd': CHZZK_CHAT_CMD['request_recent_chat'], 'tid': 2, 'sid': sid,
    'bdy': {'recentMessageCount': 50},
}))

# 3. 수신 루프
counts   = {'chat': 0, 'donation': 0, 'recent': 0, 'ping': 0, 'other': 0}
shown    = 0
deadline = time.time() + args.seconds

while time.time() < deadline:
    sock.settimeout(max(1, deadline - time.time()))
    try:
        raw = sock.recv()
    except WebSocketTimeoutException:
        break
    except Exception:
        break
    if not raw:
        continue
    frame = json.loads(raw)
    cmd = frame.get('cmd')

    if cmd == CHZZK_CHAT_CMD['ping']:
        counts['ping'] += 1
        sock.send(json.dumps({'ver': '2', 'cmd': CHZZK_CHAT_CMD['pong']}))
    elif cmd == 15101:
        counts['recent'] += len(frame['bdy'].get('messageList', []))
    elif cmd == CHZZK_CHAT_CMD['chat']:
        for item in frame['bdy']:
            counts['chat'] += 1
            if shown < args.show:   # collect.py와 동일한 파싱 경로 검증
                nickname = json.loads(item['profile'])['nickname'] if item.get('profile') else '?'
                print(f'  [채팅] {nickname} : {item["msg"][:40]}')
                shown += 1
    elif cmd == CHZZK_CHAT_CMD['donation']:
        for item in frame['bdy']:
            counts['donation'] += 1
            extras = json.loads(item.get('extras') or '{}')
            print(f'  [후원] {extras.get("payAmount")}원 : {item["msg"][:30]}')
    else:
        counts['other'] += 1

sock.close()

# 4. 시청자 수 엔드포인트
try:
    status = requests.get(f'http://127.0.0.1:{args.port}/polling/v2/channels/x/live-status',
                          timeout=5).json()['content']
    print(f"live-status: {status['status']} | 시청자 {status['concurrentUserCount']}"
          f"{' (합성)' if status.get('synthetic_viewers') else ' (실측)'}")
except Exception as e:
    print(f'live-status 조회 실패: {type(e).__name__}')

print(f'\n수신 집계: {counts}')
