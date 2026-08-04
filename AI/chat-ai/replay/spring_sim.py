"""
Spring 역할 시뮬레이터 — /ingest 종단 검증 도구 겸 Spring 참고 구현

리플레이 서버(가짜 치지직)에 WS로 붙어 채팅을 받고, 실제 Spring이 할 일을
그대로 한다: 1초 모아서 → AI 서버 /ingest 에 POST → 응답의 window 지표 출력.

사용법 (터미널 3개):
    python replay/replay.py --video 14189073 --speed 20    # 가짜 치지직
    uvicorn server:app --port 8001                          # AI 서버
    python replay/spring_sim.py                             # 이 스크립트 (Spring 역)

옵션:
    --no-viewers   시청자 결측 시나리오 (viewers를 null로 보냄 → engagement null 확인)
    --broadcast    방송 ID (기본 sim-1)
    --timeout      AI 서버 타임아웃(초). 계약 권장값 2초

Spring 구현 시 그대로 가져갈 규칙 (계약 '반드시 볼 것'):
    1. AI 서버 실패/타임아웃 → 기다리지 말고 그 배치는 건너뛴다 (여기 except 참고)
    2. 채팅이 없어도 1초마다 빈 배치를 보낸다 (윈도우가 미끄러져야 함)
"""
import argparse
import json
import os
import sys
import time

import requests
from websocket import WebSocket, WebSocketTimeoutException

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatai.cmd_type import CHZZK_CHAT_CMD

parser = argparse.ArgumentParser()
parser.add_argument('--replay-port', type=int, default=8899)
parser.add_argument('--ai', default='http://127.0.0.1:8001')
parser.add_argument('--broadcast', default='sim-1')
parser.add_argument('--token', default=os.getenv('CHATAI_TOKEN', ''))
parser.add_argument('--no-viewers', action='store_true', help='viewers를 항상 null로 (결측 시나리오)')
parser.add_argument('--timeout', type=float, default=2.0)
parser.add_argument('--seconds', type=int, default=0, help='실행 시간(초). 0이면 Ctrl-C까지')
args = parser.parse_args()

INGEST_URL = f'{args.ai}/broadcasts/{args.broadcast}/ingest'
STATUS_URL = f'http://127.0.0.1:{args.replay_port}/polling/v2/channels/x/live-status'
HEADERS = {'Content-Type': 'application/json'}
if args.token:
    HEADERS['Authorization'] = f'Bearer {args.token}'

# --- 가짜 치지직 접속 (collect.py와 동일 절차) ---
sock = WebSocket()
sock.connect(f'ws://127.0.0.1:{args.replay_port}/chat', timeout=10)
sock.send(json.dumps({
    'ver': '2', 'svcid': 'game', 'cid': 'replay',
    'cmd': CHZZK_CHAT_CMD['connect'], 'tid': 1,
    'bdy': {'uid': 'spring-sim', 'devType': 2001, 'accTkn': 'dummy', 'auth': 'READ'},
}))
resp = json.loads(sock.recv())
assert resp['cmd'] == 10100, f'핸드셰이크 실패: {resp}'
print(f"리플레이 접속 OK — {INGEST_URL} 로 1초 배치 전송 시작\n")

pending = []                    # 이번 1초 동안 모은 채팅
sent = ok = skipped = 0
viewers = None
last_flush = last_poll = time.time()
started = time.time()

try:
    while True:
        if args.seconds and time.time() - started > args.seconds:
            break

        # 채팅 수신 (남는 시간 동안)
        sock.settimeout(0.2)
        try:
            raw = sock.recv()
        except WebSocketTimeoutException:
            raw = None
        except Exception:
            print('WS 종료')
            break
        if raw:
            frame = json.loads(raw)
            cmd = frame.get('cmd')
            if cmd == CHZZK_CHAT_CMD['ping']:
                sock.send(json.dumps({'ver': '2', 'cmd': CHZZK_CHAT_CMD['pong']}))
            elif cmd in (CHZZK_CHAT_CMD['chat'], CHZZK_CHAT_CMD['donation']):
                for item in frame['bdy']:
                    if item.get('msg'):
                        pending.append({
                            'uid': item.get('uid') or item.get('userId') or '',
                            'msg': item['msg'],
                            'ts': int(item.get('msgTime') or 0),
                        })

        now = time.time()

        # 시청자 수 폴링 (10초마다 — 실서비스는 30초)
        if not args.no_viewers and now - last_poll >= 10:
            try:
                c = requests.get(STATUS_URL, timeout=2).json()['content']
                viewers = c.get('concurrentUserCount')
            except Exception:
                viewers = None          # 폴링 실패 → null 전송 (계약대로)
            last_poll = now

        # 1초마다 배치 전송 — 빈 배치라도 보낸다 (계약 '반드시 볼 것 2')
        if now - last_flush >= 1.0:
            batch, pending = pending, []
            body = {'viewers': None if args.no_viewers else viewers, 'chats': batch}
            sent += 1
            try:
                r = requests.post(INGEST_URL, json=body, headers=HEADERS,
                                  timeout=args.timeout)
                r.raise_for_status()
                ok += 1
                w = r.json()['window']
                eng = f"{w['engagement']:.1%}" if w['engagement'] is not None else 'null'
                print(f"[{now - started:6.0f}s] 배치 {len(batch):>3}건 → "
                      f"윈도우 {w['chatCount']:>5}건 · 유니크 {w['uniqueChatters']:>4} · "
                      f"시청자 {w['viewers'] or '-':>6} · 호응도 {eng:>6} · {w['dominant'] or '-'}")
            except Exception as e:
                # 계약 '반드시 볼 것 1': 실패하면 건너뛴다. 재시도하지 않는다.
                skipped += 1
                print(f"[{now - started:6.0f}s] 배치 {len(batch):>3}건 → 건너뜀 ({type(e).__name__})")
            last_flush = now

except KeyboardInterrupt:
    pass

sock.close()
print(f'\n전송 {sent}배치 (성공 {ok} / 건너뜀 {skipped})')
