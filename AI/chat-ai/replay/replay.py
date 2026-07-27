"""
치지직 채팅 리플레이 시뮬레이터 — "가짜 치지직 채팅 서버"

수집된 VOD 채팅(data/<채널ID>/vod_chats_<videoNo>.jsonl)을 원래 타이밍대로
재생하는 웹소켓 서버. 백엔드는 접속 주소만 ws://localhost:8899/chat 으로
바꾸면 실방송과 동일한 프로토콜(cmd 100/10100, 5101/15101, ping/pong,
93101 채팅, 93102 후원)로 채팅을 받는다.

시청자 수는 실서비스와 같은 모양의 HTTP 폴링으로 제공:
    GET /polling/v2/channels/{채널ID}/live-status

사용법:
    python replay.py --list
    python replay.py --video 14188543                 # 1배속
    python replay.py --video 14188543 --speed 4       # 4배속 (0 = 최대속)
    python replay.py --video 14188543 --start-offset 1200
    python replay.py --video 14188543 --no-rebase     # 원본 타임스탬프 유지

재생은 첫 클라이언트가 접속하면 시작된다 (--autostart 로 즉시 시작).
"""
import argparse
import asyncio
import datetime
import glob
import json
import os
import sys
import time

# 이 PC의 Windows 인증서 저장소에 손상된 항목이 있어 ssl 기본 컨텍스트 생성이
# 실패한다 (aiohttp가 import 시점에 생성). localhost 전용 서버라 TLS가 필요
# 없으므로 손상 인증서 로드 실패만 무시하도록 가드.
import ssl
_orig_load = ssl.SSLContext._load_windows_store_certs
def _safe_load(self, storename, purpose):
    try:
        _orig_load(self, storename, purpose)
    except ssl.SSLError:
        pass
ssl.SSLContext._load_windows_store_certs = _safe_load

from aiohttp import web, WSMsgType

# 패키지 루트를 경로에 추가 — 어느 디렉터리에서 실행해도 chatai를 찾도록
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from chatai.cmd_type import CHZZK_CHAT_CMD

# 채팅 데이터 위치. 실행 디렉터리에 의존하지 않도록 패키지 루트(chat-ai/) 기준으로 잡는다.
# 다른 곳에 두려면 환경변수 CHATAI_DATA_DIR 로 지정.
DATA_DIR = os.getenv(
    'CHATAI_DATA_DIR',
    os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'data'))
BATCH_MS       = 100     # 이 간격 안의 메시지는 한 프레임으로 배칭
PING_INTERVAL  = 20      # 초
SYNTH_FACTOR   = 4       # 합성 시청자수 = 최근 5분 유니크 채터 × 배수


def log(message):
    print(f'[{datetime.datetime.now().strftime("%H:%M:%S")}] {message}', flush=True)


# ---------------------------------------------------------------- VOD 인덱스/로딩

def index_vods():
    """{video_no: 파일경로}"""
    index = {}
    for path in glob.glob(os.path.join(DATA_DIR, '*', 'vod_chats_*.jsonl')):
        video_no = os.path.basename(path).split('_')[-1].split('.')[0]
        index[int(video_no)] = path
    return index


def list_vods():
    rows = []
    for video_no, path in sorted(index_vods().items()):
        size_mb = os.path.getsize(path) / 1e6
        first = None
        with open(path, encoding='utf-8') as f:
            for line in f:
                if line.strip():
                    first = json.loads(line)
                    break
        if first is None:
            continue
        rows.append({
            'video_no': video_no,
            'channel' : first.get('_streamer_id', '')[:8],
            'title'   : (first.get('_video_title') or '')[:30],
            'size_mb' : round(size_mb, 1),
            'has_viewers': bool(load_viewer_series(first.get('_streamer_id', ''),
                                                   first.get('chatChannelId', ''))),
        })
    print(f'{"videoNo":>10s} {"채널":8s} {"크기MB":>7s} {"시청자실측":>6s}  제목')
    for r in rows:
        print(f"{r['video_no']:>10d} {r['channel']:8s} {r['size_mb']:>7.1f}"
              f" {('O' if r['has_viewers'] else '-'):>6s}  {r['title']}")
    print(f'\n총 {len(rows)}개 VOD')


def load_vod(video_no):
    """메시지 리스트(playerMessageTime 정렬)와 메타데이터 적재"""
    path = index_vods().get(video_no)
    if path is None:
        raise SystemExit(f'videoNo {video_no} 를 data/ 에서 찾을 수 없음 (--list 로 확인)')

    messages, meta = [], None
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            r = json.loads(line)
            if meta is None:
                meta = {
                    'streamer_id'    : r.get('_streamer_id', ''),
                    'chat_channel_id': r.get('chatChannelId', ''),
                    'title'          : r.get('_video_title', ''),
                    'category'       : r.get('_video_category', ''),
                }
            messages.append((
                r.get('playerMessageTime') or 0,
                r.get('messageTime') or 0,
                r.get('userIdHash') or '',
                r.get('content') or '',
                r.get('profile'),
                r.get('extras'),
                r.get('messageTypeCode') or 1,
                r.get('messageStatusType') or 'NORMAL',
            ))
    messages.sort(key=lambda m: m[0])
    log(f'적재: {len(messages):,}건 | {meta["title"][:40]}')
    return meta, messages


def load_viewer_series(streamer_id, chat_channel_id):
    """라이브 수집 events에서 이 방송 회차(chatChannelId 일치)의 시청자 곡선 추출.
    반환: [(epoch_ms, viewers)] 정렬 리스트 (없으면 빈 리스트)"""
    if not streamer_id or not chat_channel_id:
        return []
    series = []
    for path in glob.glob(os.path.join(DATA_DIR, streamer_id, 'events_*.jsonl')):
        with open(path, encoding='utf-8') as f:
            for line in f:
                try:
                    r = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if r.get('_type') != 'snapshot' or r.get('chatChannelId') != chat_channel_id:
                    continue
                if r.get('concurrentUserCount') is None:
                    continue
                ts = datetime.datetime.fromisoformat(r['_received_at']).timestamp() * 1000
                series.append((ts, r['concurrentUserCount']))
    series.sort()
    return series


# ---------------------------------------------------------------- 재생 상태

class ReplayState:
    def __init__(self, meta, messages, viewer_series, speed, start_offset_ms, rebase):
        self.meta          = meta
        self.messages      = messages
        self.viewer_series = viewer_series   # [(epoch_ms, viewers)]
        self.speed         = speed
        self.start_offset  = start_offset_ms
        self.rebase        = rebase
        self.clients       = set()           # 핸드셰이크 끝난 ws
        self.started       = asyncio.Event() # 첫 클라이언트 접속(또는 autostart)
        self.t0            = None            # 재생 시작 monotonic
        self.pos_ms        = start_offset_ms # 현재 재생 위치 (영상 오프셋)
        self.finished      = False
        # 세션 절대 시작시각 (영상 0초의 epoch ms) — 시청자 곡선 정렬용
        first_pt, first_abs = messages[0][0], messages[0][1]
        self.session_start_ms = first_abs - first_pt

    def current_viewers(self):
        """현재 재생 위치의 시청자 수. (값, 합성여부)"""
        if self.viewer_series:
            target = self.session_start_ms + self.pos_ms
            value = self.viewer_series[0][1]
            for ts, v in self.viewer_series:
                if ts <= target:
                    value = v
                else:
                    break
            return value, False
        # 합성: 최근 5분 유니크 채터 × 배수
        lo = self.pos_ms - 5 * 60 * 1000
        uids = {m[2] for m in self.messages if lo <= m[0] <= self.pos_ms}
        return max(10, len(uids) * SYNTH_FACTOR), True


# ---------------------------------------------------------------- 프레임 생성

def to_live_item(state, m, emit_ms, viewers):
    """VOD 저장 필드명 → 라이브 프레임 필드명 변환"""
    pt, abs_ms, uid, content, profile, extras, type_code, status = m
    msg_time = emit_ms if state.rebase else abs_ms
    return {
        'svcid'        : 'game',
        'cid'          : state.meta['chat_channel_id'],
        'mbrCnt'       : viewers,
        'uid'          : uid,
        'profile'      : profile,
        'msg'          : content,
        'msgTypeCode'  : type_code,
        'msgStatusType': status,
        'extras'       : extras,
        'ctime'        : msg_time,
        'utime'        : msg_time,
        'msgTid'       : None,
        'msgTime'      : msg_time,
    }


def to_recent_item(state, m):
    """5101 응답(15101)용 — 실서버는 이 응답만 필드명이 다르다"""
    pt, abs_ms, uid, content, profile, extras, type_code, status = m
    return {
        'serviceId'        : 'game',
        'channelId'        : state.meta['chat_channel_id'],
        'messageTime'      : abs_ms,
        'userId'           : uid,
        'profile'          : profile,
        'content'          : content,
        'extras'           : extras,
        'memberCount'      : None,
        'messageTypeCode'  : type_code,
        'messageStatusType': status,
        'createTime'       : abs_ms,
        'updateTime'       : abs_ms,
        'msgTid'           : None,
    }


def make_frame(state, cmd, body):
    return json.dumps({
        'svcid': 'game',
        'ver'  : '2',
        'cmd'  : cmd,
        'tid'  : None,
        'cid'  : state.meta['chat_channel_id'],
        'bdy'  : body,
    }, ensure_ascii=False)


# ---------------------------------------------------------------- 재생 루프

async def broadcast(state, text):
    dead = []
    for ws in state.clients:
        try:
            await ws.send_str(text)
        except Exception:
            dead.append(ws)
    for ws in dead:
        state.clients.discard(ws)


async def replay_loop(state):
    await state.started.wait()
    log(f'재생 시작 (speed={"max" if state.speed == 0 else state.speed}, '
        f'offset={state.start_offset // 1000}초, rebase={state.rebase})')

    state.t0 = time.monotonic()
    msgs     = state.messages
    i        = 0
    n        = len(msgs)
    last_log = time.monotonic()

    while i < n and msgs[i][0] < state.start_offset:
        i += 1

    while i < n:
        pt = msgs[i][0]

        if state.speed > 0:
            target = state.t0 + (pt - state.start_offset) / 1000 / state.speed
            delay = target - time.monotonic()
            if delay > 0:
                await asyncio.sleep(delay)
        state.pos_ms = pt

        # 같은 100ms 창의 메시지를 채팅/후원으로 나눠 배칭
        chat_body, donation_body = [], []
        emit_ms = int(time.time() * 1000)
        viewers, _ = state.current_viewers()
        j = i
        while j < n and msgs[j][0] <= pt + BATCH_MS:
            item = to_live_item(state, msgs[j], emit_ms, viewers)
            (donation_body if msgs[j][6] == 10 else chat_body).append(item)
            j += 1
        i = j

        if chat_body:
            await broadcast(state, make_frame(state, CHZZK_CHAT_CMD['chat'], chat_body))
        if donation_body:
            await broadcast(state, make_frame(state, CHZZK_CHAT_CMD['donation'], donation_body))

        if time.monotonic() - last_log > 30:
            last_log = time.monotonic()
            total_ms = msgs[-1][0]
            log(f'재생 위치 {state.pos_ms // 60000}분 / {total_ms // 60000}분 '
                f'({state.pos_ms / max(1, total_ms) * 100:.0f}%) | {i:,}/{n:,}건 | '
                f'클라이언트 {len(state.clients)}')

    state.finished = True
    state.pos_ms = msgs[-1][0]
    log('재생 종료 — 방송 종료 상태(CLOSE)로 전환, 접속은 유지')


async def ping_loop(state):
    while True:
        await asyncio.sleep(PING_INTERVAL)
        await broadcast(state, json.dumps({'ver': '2', 'cmd': CHZZK_CHAT_CMD['ping']}))


# ---------------------------------------------------------------- 핸들러

async def ws_handler(request):
    state = request.app['state']
    ws = web.WebSocketResponse()
    await ws.prepare(request)
    log('클라이언트 접속')

    try:
        async for msg in ws:
            if msg.type != WSMsgType.TEXT:
                continue
            try:
                data = json.loads(msg.data)
            except json.JSONDecodeError:
                continue
            cmd = data.get('cmd')

            if cmd == CHZZK_CHAT_CMD['connect']:
                await ws.send_str(json.dumps({
                    'svcid': 'game', 'ver': '2',
                    'cmd': 10100, 'tid': data.get('tid', 1),
                    'retCode': 0, 'retMsg': '',
                    'cid': state.meta['chat_channel_id'],
                    'bdy': {'sid': f'replay-{id(ws):x}'},
                }))
                state.clients.add(ws)
                state.started.set()          # 첫 클라이언트 접속 시 재생 시작

            elif cmd == CHZZK_CHAT_CMD['request_recent_chat']:
                recent = [m for m in state.messages if m[0] <= state.pos_ms][-50:]
                await ws.send_str(json.dumps({
                    'svcid': 'game', 'ver': '2',
                    'cmd': 15101, 'tid': data.get('tid', 2),
                    'retCode': 0, 'retMsg': '',
                    'cid': state.meta['chat_channel_id'],
                    'bdy': {
                        'messageList': [to_recent_item(state, m) for m in recent],
                        'userCount': state.current_viewers()[0],
                        'notice': None,
                    },
                }, ensure_ascii=False))

            elif cmd == CHZZK_CHAT_CMD['pong']:
                pass
    finally:
        state.clients.discard(ws)
        log('클라이언트 접속 해제')
    return ws


async def live_status_handler(request):
    state = request.app['state']
    viewers, synthetic = state.current_viewers()
    status = 'CLOSE' if state.finished else 'OPEN'
    return web.json_response({
        'code': 200, 'message': None,
        'content': {
            'liveTitle'          : state.meta['title'],
            'status'             : status,
            'concurrentUserCount': viewers if status == 'OPEN' else 0,
            'accumulateCount'    : 0,
            'paidPromotion'      : False,
            'adult'              : False,
            'chatChannelId'      : state.meta['chat_channel_id'],
            'categoryType'       : 'ETC',
            'liveCategory'       : '',
            'liveCategoryValue'  : state.meta['category'],
            'synthetic_viewers'  : synthetic,   # 시뮬레이터 전용 표시
        },
    })


# ---------------------------------------------------------------- main

async def run_server(args):
    meta, messages = load_vod(args.video)
    viewer_series  = load_viewer_series(meta['streamer_id'], meta['chat_channel_id'])
    log(f'시청자 곡선: {"실측 " + str(len(viewer_series)) + "포인트" if viewer_series else "합성(유니크 채터 기반)"}')

    state = ReplayState(meta, messages, viewer_series,
                        speed=args.speed,
                        start_offset_ms=args.start_offset * 1000,
                        rebase=not args.no_rebase)

    app = web.Application()
    app['state'] = state
    app.router.add_get('/chat', ws_handler)
    app.router.add_get('/polling/v2/channels/{channel_id}/live-status', live_status_handler)

    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, '127.0.0.1', args.port)
    await site.start()

    log(f'서버 시작: ws://127.0.0.1:{args.port}/chat | '
        f'http://127.0.0.1:{args.port}/polling/v2/channels/{meta["streamer_id"]}/live-status')
    if args.autostart:
        state.started.set()
    else:
        log('첫 클라이언트 접속을 기다리는 중... (--autostart 로 즉시 시작 가능)')

    asyncio.create_task(ping_loop(state))
    await replay_loop(state)

    # 재생이 끝나도 CLOSE 상태 응답을 위해 서버 유지
    while True:
        await asyncio.sleep(3600)


if __name__ == '__main__':

    parser = argparse.ArgumentParser()
    parser.add_argument('--list', action='store_true', help='재생 가능한 VOD 목록')
    parser.add_argument('--video', type=int, help='재생할 videoNo')
    parser.add_argument('--speed', type=float, default=1.0, help='배속 (0 = 최대속)')
    parser.add_argument('--start-offset', type=int, default=0, help='시작 지점(초)')
    parser.add_argument('--port', type=int, default=8899)
    parser.add_argument('--no-rebase', action='store_true',
                        help='타임스탬프를 현재 시각으로 옮기지 않고 원본 유지')
    parser.add_argument('--autostart', action='store_true',
                        help='클라이언트 접속을 기다리지 않고 즉시 재생 시작')
    args = parser.parse_args()

    if args.list:
        list_vods()
    elif args.video:
        try:
            asyncio.run(run_server(args))
        except KeyboardInterrupt:
            print('\n종료')
    else:
        parser.print_help()
