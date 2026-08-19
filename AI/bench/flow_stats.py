"""1단계: 실제 방송 채팅의 유량 실측.

수집된 jsonl을 오프라인 분석한다. 서버 코드를 건드리지 않으므로 계측
오버헤드가 없고, 같은 입력에 같은 결과가 나온다(재현 가능).

  python flow_stats.py --data <데이터경로> --out <결과폴더>

자료가 두 종류이고 스키마가 다르다. 역할을 나눠 쓴다:

  chats_*.jsonl      라이브 수신분(20파일, 156MB)  <- 주 분석
      _received_at   우리 수집기가 실제로 받은 시각. '우리 시스템이 겪는 유량'
      mbrCnt         동시 접속자 -> 시청자 대비 TPS를 낼 수 있다
  vod_chats_*.jsonl  다시보기 수집분(118파일, 5.2GB)  <- 보조
      messageTime    발화 시각. 표본이 26배 커서 피크 배수·길이 분포를 뒷받침한다
      시청자 수·수신 시각이 없다 - 이것만으로는 '수신 유량'을 못 낸다
"""
import argparse
import collections
import csv
import glob
import json
import os
import statistics
import sys
from datetime import datetime

# 이 시간 이상 채팅이 끊기면 다른 방송으로 본다.
# 파일 하나가 하루치라 방송 사이 공백이 섞여 있다. 안 자르면 그 공백이
# 분모에 들어가 평균 TPS가 실제의 몇 분의 1로 찌그러진다.
DEFAULT_GAP_SEC = 300

# 피크 판정 배수. 평균의 이 배를 넘는 초를 '피크'로 본다.
DEFAULT_PEAK_K = 3.0

# 메시지 길이는 리스트로 모으면 안 된다. VOD 5.2GB는 수천만 건이라
# int 리스트만으로도 수백 MB를 먹는다. 길이는 값의 범위가 좁으니
# 히스토그램(Counter)으로 누적하면 메모리가 O(길이 종류)로 고정된다.
LEN_CAP = 500


def _iter_live(path):
    """chats_*.jsonl -> (수신초, 메시지길이, 시청자수)

    _received_at(우리가 받은 시각)을 쓴다. msgTime(발화 시각)이 아니다 -
    측정 목표가 '우리 시스템에 초당 몇 건이 들어오는가'이기 때문이다.
    """
    with open(path, encoding='utf-8') as fh:
        for line in fh:
            if not line.strip():
                continue
            try:
                r = json.loads(line)
            except ValueError:
                continue
            # donation·ws_* 이벤트는 채팅이 아니다. 넣으면 TPS가 부풀려진다.
            if r.get('_type') != 'chat':
                continue
            raw = r.get('_received_at')
            if not raw:
                continue
            # '2026-07-27T16:38:31.460+09:00' - 오프셋이 붙어 있다.
            # fromisoformat이 tz까지 읽으므로 timestamp()가 곧 UTC 절대초다.
            try:
                sec = int(datetime.fromisoformat(raw).timestamp())
            except ValueError:
                continue
            yield sec, len(r.get('msg') or ''), r.get('mbrCnt')


def _iter_vod(path):
    """vod_chats_*.jsonl -> (발화초, 메시지길이, None)"""
    with open(path, encoding='utf-8') as fh:
        for line in fh:
            if not line.strip():
                continue
            try:
                r = json.loads(line)
            except ValueError:
                continue
            ms = r.get('messageTime')
            if not ms:
                continue
            # epoch ms. 위 _received_at(ISO 문자열)과 단위가 다르다 - 섞지 말 것.
            yield int(ms // 1000), len(r.get('content') or ''), None


def _split_sessions(counts, gap_sec):
    """초 -> 건수 dict 를 방송 단위로 자른다. [(시작초, 끝초), ...]"""
    secs = sorted(counts)
    if not secs:
        return []
    out, start, prev = [], secs[0], secs[0]
    for s in secs[1:]:
        if s - prev > gap_sec:
            out.append((start, prev))
            start = s
        prev = s
    out.append((start, prev))
    return out


def _peak_runs(counts, lo, hi, threshold):
    """임계를 넘는 연속 구간들의 길이(초). 빈 초는 임계 미만으로 본다."""
    runs, cur = [], 0
    for s in range(lo, hi + 1):
        if counts.get(s, 0) >= threshold:
            cur += 1
        elif cur:
            runs.append(cur)
            cur = 0
    if cur:
        runs.append(cur)
    return runs


def _pct(values, q):
    if not values:
        return None
    v = sorted(values)
    i = min(int(len(v) * q), len(v) - 1)
    return v[i]


def _pct_hist(hist, q):
    """{길이: 건수} 히스토그램에서 백분위."""
    total = sum(hist.values())
    if not total:
        return 0
    want, seen = total * q, 0
    for ln in sorted(hist):
        seen += hist[ln]
        if seen >= want:
            return ln
    return max(hist)


def analyse(files, reader, gap_sec, peak_k, label):
    """파일 묶음 -> (방송별 지표, 초당 시계열, 전체 길이 히스토그램)."""
    sessions, timeline = [], []
    all_len = collections.Counter()

    for n, path in enumerate(files, 1):
        counts = collections.Counter()
        lenhist = collections.Counter()
        viewers = {}
        for sec, ln, mbr in reader(path):
            counts[sec] += 1
            lenhist[min(ln, LEN_CAP)] += 1
            if mbr:
                viewers[sec] = mbr
        if not counts:
            continue
        all_len += lenhist

        for lo, hi in _split_sessions(counts, gap_sec):
            # 빈 초를 포함한 전체 길이로 나눈다. 채팅이 있는 초만 세면
            # 평균이 부풀려지고 피크/평균 배수가 실제보다 작게 나온다.
            span = hi - lo + 1
            total = sum(counts[s] for s in range(lo, hi + 1) if s in counts)
            if span < 60 or total < 100:
                continue                      # 너무 짧은 조각은 통계가 무의미
            avg = total / span
            peak = max(counts.get(s, 0) for s in range(lo, hi + 1))
            runs = _peak_runs(counts, lo, hi, max(1.0, avg * peak_k))
            vs = [viewers[s] for s in range(lo, hi + 1) if s in viewers]
            med_v = statistics.median(vs) if vs else None

            sessions.append({
                'source': label,
                'file': os.path.basename(path),
                'start': datetime.fromtimestamp(lo).isoformat(timespec='seconds'),
                'span_sec': span,
                'messages': total,
                'avg_tps': round(avg, 3),
                'peak_tps': peak,
                'peak_over_avg': round(peak / avg, 2) if avg else None,
                'peak_runs': len(runs),
                'peak_run_max_sec': max(runs) if runs else 0,
                'peak_run_median_sec': int(statistics.median(runs)) if runs else 0,
                'viewers_median': med_v,
                'tps_per_1k_viewers': round(avg / med_v * 1000, 2) if med_v else None,
                'peak_tps_per_1k_viewers': round(peak / med_v * 1000, 2) if med_v else None,
                # 길이 분포는 파일 단위다(세션별로 쪼개지 않는다). 같은 방송인의
                # 같은 날 채팅이라 세션 간 차이가 작고, 쪼개면 히스토그램을
                # 세션 수만큼 들고 있어야 한다.
                'msg_len_p50': _pct_hist(lenhist, 0.50),
                'msg_len_p95': _pct_hist(lenhist, 0.95),
                'msg_len_max': max(lenhist),
            })
            for s in range(lo, hi + 1):
                timeline.append((label, os.path.basename(path), s - lo,
                                 counts.get(s, 0), viewers.get(s, '')))

        print(f'  [{n}/{len(files)}] {os.path.basename(path):40} '
              f'{sum(counts.values()):>9,}건', file=sys.stderr)
    return sessions, timeline, all_len


def _dist(rows, key):
    """세션들의 어떤 지표를 분포로. 단일 대표값 하나로는 왜곡된다."""
    v = [r[key] for r in rows if r.get(key) is not None]
    if not v:
        return None
    return {'p50': _pct(v, 0.50), 'p95': _pct(v, 0.95), 'max': max(v)}


def summarise(sess, lenhist, src):
    """
    ⚠ 대표값을 단일 중앙값으로 내면 안 된다.

    세션 규모가 시청자 79명~4,727명으로 60배 차이 난다. 첫 실행에서
    '평균 TPS 중앙값'이 0.203으로 나왔는데 가장 큰 방송은 11.12였다.
    작은 방송이 수적으로 많아 중앙값이 그쪽으로 끌려간 것이다.

    그래서 두 가지로 바꾼다.
      1) 분포(p50/p95/max)로 낸다 - 하나의 숫자로 뭉개지 않는다
      2) 시청자로 정규화한 값을 주 지표로 둔다 - 규모에 무관해 외삽에 쓸 수 있다
    """
    rows = [s for s in sess if s['source'] == src]
    if not rows:
        return None
    total_msg = sum(r['messages'] for r in rows)
    total_span = sum(r['span_sec'] for r in rows)
    return {
        'sessions': len(rows),
        'messages': total_msg,
        # 전체를 하나로 본 평균. '세션별 평균의 중앙값'과 다르다
        'avg_tps_overall': round(total_msg / total_span, 3) if total_span else None,
        'avg_tps': _dist(rows, 'avg_tps'),
        'peak_tps': _dist(rows, 'peak_tps'),
        'peak_over_avg': _dist(rows, 'peak_over_avg'),
        'peak_run_sec': _dist(rows, 'peak_run_max_sec'),
        # --- 주 지표: 규모 무관. 목표 부하 외삽은 이 값으로 한다 ---
        'peak_tps_per_1k_viewers': _dist(rows, 'peak_tps_per_1k_viewers'),
        'tps_per_1k_viewers': _dist(rows, 'tps_per_1k_viewers'),
        'msg_len': {'p50': _pct_hist(lenhist, 0.50),
                    'p95': _pct_hist(lenhist, 0.95),
                    'p99': _pct_hist(lenhist, 0.99)},
    }


def by_scale(sess):
    """시청자 규모 구간별 분해. 큰 방송과 작은 방송은 성격이 다르다."""
    buckets = [('~500명', 0, 500), ('500~2000명', 500, 2000), ('2000명~', 2000, 10 ** 9)]
    out = {}
    for name, lo, hi in buckets:
        rows = [s for s in sess
                if s['viewers_median'] and lo <= s['viewers_median'] < hi]
        if not rows:
            continue
        out[name] = {
            'sessions': len(rows),
            'avg_tps': _dist(rows, 'avg_tps'),
            'peak_tps': _dist(rows, 'peak_tps'),
            'peak_over_avg': _dist(rows, 'peak_over_avg'),
            'peak_tps_per_1k_viewers': _dist(rows, 'peak_tps_per_1k_viewers'),
        }
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--data', required=True, help='수집 데이터 루트 (하위에 채널 폴더들)')
    p.add_argument('--out', default='out', help='결과 폴더')
    p.add_argument('--gap-sec', type=int, default=DEFAULT_GAP_SEC)
    p.add_argument('--peak-k', type=float, default=DEFAULT_PEAK_K)
    p.add_argument('--skip-vod', action='store_true', help='5.2GB 보조 분석을 건너뛴다')
    a = p.parse_args()

    os.makedirs(a.out, exist_ok=True)
    live = sorted(glob.glob(os.path.join(a.data, '*', 'chats_*.jsonl')))
    vod = sorted(glob.glob(os.path.join(a.data, '*', 'vod_chats_*.jsonl')))
    print(f'라이브 {len(live)}파일 / VOD {len(vod)}파일', file=sys.stderr)

    print('[주 분석] 라이브 수신분', file=sys.stderr)
    sess, tl, len_live = analyse(live, _iter_live, a.gap_sec, a.peak_k, 'live')
    len_vod = collections.Counter()
    if not a.skip_vod:
        print('[보조] VOD (5.2GB - 수 분 걸림)', file=sys.stderr)
        s2, t2, len_vod = analyse(vod, _iter_vod, a.gap_sec, a.peak_k, 'vod')
        sess += s2
        tl += t2

    if not sess:
        print('분석할 세션이 없습니다. --data 경로를 확인하세요.', file=sys.stderr)
        return 1

    cols = list(sess[0].keys())
    with open(os.path.join(a.out, 'sessions.csv'), 'w', newline='', encoding='utf-8-sig') as fh:
        w = csv.DictWriter(fh, fieldnames=cols)
        w.writeheader()
        w.writerows(sess)
    with open(os.path.join(a.out, 'timeline.csv'), 'w', newline='', encoding='utf-8-sig') as fh:
        w = csv.writer(fh)
        w.writerow(['source', 'file', 'elapsed_sec', 'messages', 'viewers'])
        w.writerows(tl)

    summary = {
        'params': {'gap_sec': a.gap_sec, 'peak_k': a.peak_k},
        'live': summarise(sess, len_live, 'live'),
        'vod': summarise(sess, len_vod, 'vod'),
        'by_viewer_scale': by_scale(sess),
    }
    with open(os.path.join(a.out, 'summary.json'), 'w', encoding='utf-8') as fh:
        json.dump(summary, fh, ensure_ascii=False, indent=2)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == '__main__':
    sys.exit(main())
