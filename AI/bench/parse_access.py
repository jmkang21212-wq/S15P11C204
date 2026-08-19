"""2단계 보조: chat-ai 액세스 로그에서 서버 측 실측치를 뽑는다.

  docker exec laive-chat-ai cat /app/logs/chatai.log > server.log
  python parse_access.py server.log --bid-prefix bench

서버 코드를 새로 계측하지 않는다. S15P11C204-132에서 넣은 액세스 로그가
요청마다 이미 다음을 남기고 있다:

  2026-08-18 02:48:13 WARNING [chatai] POST /broadcasts/bench41/ingest 200
      28427ms bid=bench41 chats=4 pred=901ms redis=10ms win=140 [느림]
                        ^^^^^^^      ^^^^^^^^^ ^^^^^^^^^
                        전체         추론       Redis

이 셋의 차이가 핵심이다.

  대기 = 전체 - 추론 - Redis

/ingest 핸들러는 동기 함수(def)라 FastAPI가 anyio 스레드풀에 넘긴다.
스레드풀 자리를 기다린 시간이 여기로 들어온다. 이 값이 크면 병목은
추론이 아니라 대기열이다 - 모델을 빠르게 해도 안 낫는다.

⚠ '대기'에는 응답 전송 시간도 섞인다. 미들웨어가 재는 구간이 ASGI
   진입~응답 완료이기 때문이다. 클라이언트가 느리면 그것도 포함된다.
   그래서 클라이언트 측 rtt(load_ingest.py)와 대조해서 봐야 한다.
"""
import argparse
import collections
import csv
import json
import re
import sys

# 2026-08-18 02:48:13 WARNING [chatai] POST /broadcasts/bench41/ingest 200 28427ms ...
LINE = re.compile(
    r'^(?P<ts>\d{4}-\d\d-\d\d \d\d:\d\d:\d\d)\s+'
    r'(?P<level>\w+)\s+\[chatai\]\s+'
    r'(?P<method>[A-Z]+)\s+(?P<path>\S+)\s+'
    r'(?P<status>\d{3})\s+(?P<total>\d+)ms'
    r'(?P<extra>.*)$')

KV = re.compile(r'(\w+)=(\S+)')


def parse(path, bid_prefix, only_ingest=True):
    rows = []
    bad = 0
    with open(path, encoding='utf-8', errors='replace') as fh:
        for line in fh:
            m = LINE.match(line.strip())
            if not m:
                bad += 1
                continue
            if only_ingest and not m.group('path').endswith('/ingest'):
                continue
            kv = dict(KV.findall(m.group('extra')))
            bid = kv.get('bid', '')
            if bid_prefix and not bid.startswith(bid_prefix):
                continue

            def num(key):
                v = kv.get(key, '')
                v = v.rstrip('ms')
                try:
                    return float(v)
                except ValueError:
                    return 0.0

            total = float(m.group('total'))
            pred = num('pred')
            redis = num('redis')
            rows.append({
                'ts': m.group('ts'),
                'status': int(m.group('status')),
                'total_ms': total,
                'pred_ms': pred,
                'redis_ms': redis,
                # 음수가 나오면 파싱이 틀린 것이다. 0으로 깎지 말고 그대로 둔다
                'queue_ms': round(total - pred - redis, 1),
                'chats': int(num('chats')),
                'bid': bid,
            })
    return rows, bad


def _pct(v, q):
    if not v:
        return 0.0
    s = sorted(v)
    return round(s[min(int(len(s) * q), len(s) - 1)], 1)


def dist(rows, key):
    v = [r[key] for r in rows]
    return {'p50': _pct(v, 0.50), 'p95': _pct(v, 0.95),
            'p99': _pct(v, 0.99), 'max': round(max(v), 1) if v else 0}


def per_second(rows):
    """초당 완료 건수·메시지 수. 서버가 실제로 소화한 양이다."""
    req = collections.Counter()
    msg = collections.Counter()
    for r in rows:
        req[r['ts']] += 1
        msg[r['ts']] += r['chats']
    return req, msg


def main():
    p = argparse.ArgumentParser()
    p.add_argument('logfile')
    p.add_argument('--bid-prefix', default='',
                   help='이 접두사로 시작하는 방송만 (부하 테스트는 bench)')
    p.add_argument('--out', default='')
    a = p.parse_args()

    rows, bad = parse(a.logfile, a.bid_prefix)
    if not rows:
        print('해당하는 요청이 없습니다. --bid-prefix 를 확인하세요.', file=sys.stderr)
        return 1
    print(f'요청 {len(rows):,}건 (형식 불일치 {bad:,}줄 건너뜀)', file=sys.stderr)

    req, msg = per_second(rows)
    counts = sorted(req.values())
    msgs = sorted(msg.values())
    chats_total = sum(r['chats'] for r in rows)

    summary = {
        'requests': len(rows),
        'messages': chats_total,
        'seconds_observed': len(req),
        # 서버가 실제로 소화한 초당 처리량. 클라이언트 집계와 독립이다
        'completed_req_per_sec': {'p50': _pct(counts, 0.5), 'max': counts[-1]},
        'completed_msg_per_sec': {'p50': _pct(msgs, 0.5), 'max': msgs[-1]},
        'total_ms': dist(rows, 'total_ms'),
        'pred_ms': dist(rows, 'pred_ms'),
        'redis_ms': dist(rows, 'redis_ms'),
        # 핵심: 이 값이 total의 대부분이면 병목은 대기열이다
        'queue_ms': dist(rows, 'queue_ms'),
        'statuses': dict(collections.Counter(r['status'] for r in rows)),
    }
    t50 = summary['total_ms']['p50'] or 1
    summary['breakdown_at_p50'] = {
        '추론': f"{summary['pred_ms']['p50'] / t50 * 100:.1f}%",
        'Redis': f"{summary['redis_ms']['p50'] / t50 * 100:.1f}%",
        '대기': f"{summary['queue_ms']['p50'] / t50 * 100:.1f}%",
    }

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if a.out:
        with open(a.out, 'w', newline='', encoding='utf-8-sig') as fh:
            w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
            w.writeheader()
            w.writerows(rows)
        print(f'상세: {a.out}', file=sys.stderr)
    return 0


if __name__ == '__main__':
    sys.exit(main())
