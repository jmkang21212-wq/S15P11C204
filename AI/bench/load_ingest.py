"""2단계: chat-ai /ingest 부하 주입.

  python load_ingest.py --data <데이터경로> --env-file <AI/.env 경로>
  python load_ingest.py --stages 0.9,1.0,1.1 --out fine       # 정밀 구간

1단계 실측으로 정한 목표 (out_full/summary.json):
  동시 방송 50개의 합산 피크 p95 = 227 TPS
  피크 지속 최대 73초 (VOD 142세션)

부하 '형태'를 실제와 맞추는 것이 중요하다. CHAT_AI_API.md 계약상
Spring은 방송마다 1초에 한 번 그 1초치 채팅을 실어 보낸다. 채팅이
없는 초에도 RealtimeTicker가 빈 배치를 보낸다 - 즉 방송 50개면
채팅량과 무관하게 초당 50요청이 바닥으로 깔린다.

같은 227 TPS라도 '1요청 227건'과 '50요청 각 4.5건'은 전혀 다른 부하다.
후자가 실제이고, 요청당 고정비용(HTTP·Redis 파이프라인)이 지배한다.

⚠ 서버는 GMS_KEY 를 비우고 띄울 것. 질문 임베딩이 꺼져 외부 API 호출이
   0이 된다(결정 C). /summary 는 이 스크립트가 호출하지 않는다.

     docker compose -f compose.yaml -f bench/compose.bench.yaml up -d --force-recreate chat-ai

인증: 서버의 CHATAI_TOKEN 이 채워져 있으면 /ingest 가 401을 준다.
--env-file 로 서버가 쓰는 .env 를 가리키면 자동으로 읽는다.
"""
import argparse
import asyncio
import collections
import csv
import glob
import json
import os
import random
import sys
import time

import httpx


# 서버가 쓰는 .env 와 같은 파일에서 토큰을 읽는다. 값을 화면·파일에
# 출력하지 않는다 - 비밀값이 로그나 결과 CSV에 새면 안 된다.
#
# ⚠ 이 스크립트를 git worktree에서 돌리면 옆의 AI/.env 가 없다.
#    .env 는 gitignore 대상이라 worktree로 복제되지 않기 때문이다.
#    그때는 --env-file 로 원래 저장소의 .env 를 가리켜야 한다.
def read_token(env_file=''):
    if os.environ.get('CHATAI_TOKEN'):
        return os.environ['CHATAI_TOKEN']
    here = os.path.dirname(os.path.abspath(__file__))
    for path in [env_file, os.path.join(here, '..', '.env')]:
        if not path or not os.path.exists(path):
            continue
        with open(path, encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if line.startswith('CHATAI_TOKEN='):
                    value = line.split('=', 1)[1].strip()
                    return value.strip('"').strip("'")
    return ''


# 1단계 실측값 (out_full/summary.json)
DEFAULT_TARGET_TPS = 227
DEFAULT_SUSTAIN_SEC = 73
DEFAULT_BROADCASTS = 50

# 실측 메시지 길이 분포 (live/vod 모두 p50=7). 실데이터가 없을 때만 쓴다.
FALLBACK_LENGTHS = [3, 5, 7, 7, 9, 12, 15, 20, 32, 40, 81]


def load_messages(data_dir, want=20000):
    """실제 채팅 본문을 표본으로 읽는다.

    합성 문자열을 쓰면 안 된다. 토크나이저가 배치 안 최장 문장에 맞춰
    패딩하므로 길이 분포가 추론 시간을 직접 좌우한다(classifier.py 주석).
    본문은 메모리에만 두고 저장하지 않는다.
    """
    msgs = []
    files = sorted(glob.glob(os.path.join(data_dir, '*', 'chats_*.jsonl')))
    files += sorted(glob.glob(os.path.join(data_dir, '*', 'vod_chats_*.jsonl')))
    for path in files:
        with open(path, encoding='utf-8') as fh:
            for line in fh:
                try:
                    r = json.loads(line)
                except ValueError:
                    continue
                m = r.get('msg') if r.get('_type') == 'chat' else r.get('content')
                if m:
                    msgs.append(m)
                if len(msgs) >= want:
                    return msgs
    return msgs


def synth(n):
    return [''.join(random.choices('ㅋㅎ가나다라마바사아자차카타파하 ',
                                   k=random.choice(FALLBACK_LENGTHS)))
            for _ in range(n)]


class Recorder:
    """요청 1건마다 대기·왕복을 따로 적는다.

    예행에서 --timeout 10 인데 지연이 30초로 찍혔다. 커넥션 풀이 꽉 차서
    풀 자리를 기다린 시간까지 한 덩어리로 재고 있었기 때문이다. 그건
    서버가 느린 게 아니라 우리가 못 보낸 것이다 - 섞으면 결론이 뒤집힌다.
    """

    def __init__(self):
        self.rows = []
        self.inflight = 0
        self.peak_inflight = 0

    def add(self, stage, tick, bid, n_msg, wait_ms, rtt_ms, status):
        self.rows.append((stage, tick, bid, n_msg,
                          round(wait_ms, 1), round(rtt_ms, 1), status))


async def fire(client, rec, sem, url, stage, tick, bid, msgs):
    """요청 1건.

    세마포어를 커넥션 풀 크기와 같게 두면, 세마포어를 얻은 뒤에는 풀에
    빈자리가 보장된다. 따라서
        wait_ms = 세마포어 대기 = 클라이언트가 밀린 시간
        rtt_ms  = 그 이후      = 서버 왕복 시간
    으로 깨끗하게 갈린다.
    """
    body = {'viewers': 1000,
            'chats': [{'uid': f'u{bid}_{i}', 'msg': m} for i, m in enumerate(msgs)]}
    t_enq = time.perf_counter()
    async with sem:
        t_go = time.perf_counter()
        rec.inflight += 1
        rec.peak_inflight = max(rec.peak_inflight, rec.inflight)
        try:
            r = await client.post(f'{url}/broadcasts/{bid}/ingest', json=body)
            status = r.status_code
        except Exception as e:                              # noqa: BLE001
            status = type(e).__name__
        finally:
            rec.inflight -= 1
        t_end = time.perf_counter()
    rec.add(stage, tick, bid, len(msgs),
            (t_go - t_enq) * 1000, (t_end - t_go) * 1000, status)


async def run_stage(client, rec, sem, url, stage, tps, secs, n_bc, pool):
    """
    한 단계를 secs 동안 유지한다.

    ⚠ 페이싱은 절대 시각 기준이어야 한다. `await asyncio.sleep(1)`을 반복하면
    처리 시간만큼 매 회 밀려서, 서버가 느려질수록 우리가 부하를 덜 넣는
    꼴이 된다 - 한계를 못 찾는다.
    """
    loop = asyncio.get_running_loop()
    base = loop.time()
    tasks = []
    for tick in range(secs):
        await asyncio.sleep(max(0.0, base + tick - loop.time()))
        # 이번 초의 메시지를 방송 N개에 나눈다. 나머지는 앞쪽 방송이 하나씩 더.
        q, r = divmod(tps, n_bc)
        for b in range(n_bc):
            k = q + (1 if b < r else 0)
            msgs = random.sample(pool, k) if k else []
            tasks.append(asyncio.create_task(
                fire(client, rec, sem, url, stage, tick, f'bench{b}', msgs)))
    t0 = loop.time()
    if tasks:
        await asyncio.gather(*tasks)
    # 마지막 tick 이후 남은 요청을 빼내는 데 걸린 시간. 이게 길면
    # 부하를 멈춘 뒤에도 서버가 밀린 것을 처리하고 있었다는 뜻이다.
    return loop.time() - t0


def _pct(v, q):
    if not v:
        return 0.0
    return round(v[min(int(len(v) * q), len(v) - 1)], 1)


def summarise(rows, stage, tps, secs, drain_sec):
    r = [x for x in rows if x[0] == stage]
    if not r:
        return None
    ok = [x for x in r if x[6] == 200]
    wait = sorted(x[4] for x in r)
    rtt = sorted(x[5] for x in r)
    offered = sum(x[3] for x in r)
    done = sum(x[3] for x in ok)
    wall = secs + drain_sec
    return {
        'stage': stage,
        'target_tps': tps,
        # 넣으려고 한 양. 성공 여부와 무관하다
        'offered_tps': round(offered / secs, 1),
        # 실제로 200으로 끝난 양을 '끝날 때까지 걸린 전체 시간'으로 나눈 값.
        # 이게 진짜 처리량이다. 이 값이 목표를 못 따라가면 그 지점이 한계다.
        'completed_tps': round(done / wall, 1) if wall else 0,
        'drain_sec': round(drain_sec, 1),
        'requests': len(r),
        'errors': len(r) - len(ok),
        'error_rate': round((len(r) - len(ok)) / len(r), 4),
        # 서버 왕복 - 판정은 이 값으로 한다
        'rtt_p50_ms': _pct(rtt, 0.50), 'rtt_p95_ms': _pct(rtt, 0.95),
        'rtt_p99_ms': _pct(rtt, 0.99), 'rtt_max_ms': round(rtt[-1], 1),
        # 클라이언트가 밀린 시간. 크면 우리가 부하를 제대로 못 넣은 것이다
        'wait_p50_ms': _pct(wait, 0.50), 'wait_p95_ms': _pct(wait, 0.95),
        'statuses': dict(collections.Counter(str(x[6]) for x in r)),
    }


async def main_async(a):
    pool = load_messages(a.data) if a.data else []
    if len(pool) < 1000:
        print(f'실데이터 {len(pool)}건 - 합성으로 보충', file=sys.stderr)
        pool += synth(20000)
    print(f'메시지 풀 {len(pool):,}건', file=sys.stderr)

    rec = Recorder()
    stages = [float(x) for x in a.stages.split(',')]
    out = []
    limits = httpx.Limits(max_connections=a.max_conn,
                          max_keepalive_connections=a.max_conn)
    sem = asyncio.Semaphore(a.max_conn)     # 커넥션 풀 크기와 같게
    token = a.token or read_token(a.env_file)
    headers = {'Authorization': f'Bearer {token}'} if token else {}
    print(f'인증 토큰: {"사용" if token else "없음(서버도 비어 있어야 함)"}',
          file=sys.stderr)

    async with httpx.AsyncClient(timeout=a.timeout, limits=limits,
                                 headers=headers) as client:
        # 워밍업 - 첫 요청은 커넥션 수립·JIT가 섞여 이상치가 된다
        await fire(client, Recorder(), sem, a.url, 'warmup', 0, 'bench0',
                   random.sample(pool, 10))

        for mult in stages:
            tps = max(1, int(a.target_tps * mult))
            name = f'x{mult:g}'
            print(f'[{name}] 목표 {tps} TPS x {a.sustain_sec}초 '
                  f'(방송 {a.broadcasts}개)', file=sys.stderr)
            drain = await run_stage(client, rec, sem, a.url, name, tps,
                                    a.sustain_sec, a.broadcasts, pool)
            s = summarise(rec.rows, name, tps, a.sustain_sec, drain)
            out.append(s)
            print(f'    처리 {s["completed_tps"]}/{s["offered_tps"]} TPS  '
                  f'왕복 p50 {s["rtt_p50_ms"]}ms p99 {s["rtt_p99_ms"]}ms  '
                  f'대기 p50 {s["wait_p50_ms"]}ms  '
                  f'배출 {s["drain_sec"]}s  오류 {s["error_rate"] * 100:.1f}%',
                  file=sys.stderr)
            await asyncio.sleep(a.cooldown_sec)

        for b in range(a.broadcasts):          # Redis 키 정리
            try:
                await client.delete(f'{a.url}/broadcasts/bench{b}')
            except Exception:                  # noqa: BLE001, S110
                pass

    os.makedirs(a.out, exist_ok=True)
    with open(os.path.join(a.out, 'requests.csv'), 'w', newline='',
              encoding='utf-8-sig') as fh:
        w = csv.writer(fh)
        w.writerow(['stage', 'tick', 'broadcast', 'messages',
                    'wait_ms', 'rtt_ms', 'status'])
        w.writerows(rec.rows)
    cfg = dict(vars(a))
    cfg['token'] = '(설정됨)' if token else ''     # 값은 저장하지 않는다
    result = {'config': cfg, 'peak_inflight': rec.peak_inflight, 'stages': out}
    with open(os.path.join(a.out, 'stages.json'), 'w', encoding='utf-8') as fh:
        json.dump(result, fh, ensure_ascii=False, indent=2)
    print(json.dumps(out, ensure_ascii=False, indent=2))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--url', default='http://127.0.0.1:8001')
    p.add_argument('--data', help='실제 채팅 본문 표본용 데이터 루트')
    p.add_argument('--out', default='load_out')
    p.add_argument('--target-tps', type=int, default=DEFAULT_TARGET_TPS)
    p.add_argument('--stages', default='0.5,1,2,4,8', help='목표 대비 배수')
    p.add_argument('--sustain-sec', type=int, default=DEFAULT_SUSTAIN_SEC)
    p.add_argument('--broadcasts', type=int, default=DEFAULT_BROADCASTS)
    p.add_argument('--cooldown-sec', type=int, default=15, help='단계 사이 회복 시간')
    p.add_argument('--timeout', type=float, default=10.0)
    p.add_argument('--max-conn', type=int, default=200)
    p.add_argument('--token', default='', help='생략하면 --env-file 에서 읽는다')
    p.add_argument('--env-file', default='',
                   help='서버가 쓰는 .env 경로 (worktree에서 돌릴 때 필요)')
    asyncio.run(main_async(p.parse_args()))


if __name__ == '__main__':
    main()
