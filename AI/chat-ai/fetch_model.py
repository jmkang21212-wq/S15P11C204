"""
모델 내려받기 — 최초 1회 실행

ONNX 모델(fp32 511MB / int8 129MB)은 git에 넣지 않고 팀 Google Drive에서 받는다.
파일 ID가 아래 FILES에 박혀 있으므로 그냥 실행하면 된다:

    python fetch_model.py

전제: Drive 폴더가 "링크가 있는 모든 사용자(뷰어)"로 공유돼 있어야 한다.
https://drive.google.com/drive/folders/1aEUXDciPMKllT-FvAAUgCy-hwNRdG5qU

모델을 갱신할 때는 새 파일을 같은 폴더에 올리고 아래 FILES의 ID·크기를 바꿀 것.
"""
import os
import sys

import requests

DEST = os.getenv('CHATAI_MODEL_DIR', 'models/kcelectra-chat-8emo-onnx')

# 파일명 → (Drive 파일 ID, 기대 크기 bytes)
# 크기는 무결성 확인용 — 내려받은 결과가 다르면 절단·오염을 의심할 것
FILES = {
    'model.onnx':              ('1hz3EoMPwjAaW0XJ4V4dBM29aQJqwaKXK', 511_331_849),
    'model.int8.onnx':         ('1YtosAL-_x6L1AKoTf4eARuCfVHj3gbUi', 128_946_716),
    'tokenizer.json':          ('1-oTEyo42Mn1xHPiaShh3yIiEen_N002X', 1_310_689),
    'vocab.txt':               ('1p4_j5YWEeVUTye9rRWLeOFSZzerV8OQd', 449_581),
    'config.json':             ('1e5wkoxDjKnF3NluTb14qh4QeEoHH7pmw', 1_376),
    'tokenizer_config.json':   ('1nID5GhkwWjuHIUulZpwBUqR15v7TuTa4', 1_265),
    'special_tokens_map.json': ('1cSYH-cC_JqYTJ45Ds39xL0Qy3N5RFbw2', 125),
}


def drive_download(file_id, dest_path, expected_size):
    """Google Drive 개별 파일 다운로드.

    100MB 초과 파일은 일반 uc?export=download 로는 '바이러스 검사 불가' 안내
    HTML이 내려온다. usercontent 엔드포인트 + confirm=t 로 우회한다.
    """
    url = 'https://drive.usercontent.google.com/download'
    params = {'id': file_id, 'export': 'download', 'confirm': 't'}
    with requests.get(url, params=params, stream=True, timeout=1800) as r:
        r.raise_for_status()
        if 'text/html' in r.headers.get('content-type', ''):
            raise RuntimeError(
                'HTML 페이지가 내려왔습니다. 공유 설정 문제일 가능성이 큽니다.\n'
                '  Drive 폴더 → 공유 → "링크가 있는 모든 사용자(뷰어)" 인지 확인하세요.')
        got = 0
        with open(dest_path, 'wb') as f:
            for chunk in r.iter_content(1 << 20):
                f.write(chunk)
                got += len(chunk)
                if expected_size > 10_000_000:   # 큰 파일만 진행률 표시
                    print(f'\r    {got / 1e6:>6.0f} / {expected_size / 1e6:.0f} MB', end='')
        if expected_size > 10_000_000:
            print()
    return got


def main():
    os.makedirs(DEST, exist_ok=True)
    done = skipped = 0
    for name, (fid, size) in FILES.items():
        path = os.path.join(DEST, name)
        if os.path.exists(path) and os.path.getsize(path) == size:
            skipped += 1
            continue
        print(f'  {name} 내려받는 중...')
        try:
            got = drive_download(fid, path, size)
        except Exception as e:
            # 절단된 파일이 남으면 다음 실행에서 '이미 있음'으로 오인할 수 있어 지운다
            if os.path.exists(path):
                os.remove(path)
            sys.exit(f'실패: {name}\n{e}')
        if got != size:
            os.remove(path)
            sys.exit(f'실패: {name} 크기 불일치 (기대 {size:,} vs 수신 {got:,})\n'
                     '네트워크 문제일 수 있으니 다시 실행해보세요.')
        done += 1

    print(f'\n완료 → {DEST}  (신규 {done}건, 기존 유지 {skipped}건)')
    for f in sorted(os.listdir(DEST)):
        print(f'  {f:<28} {os.path.getsize(os.path.join(DEST, f)) / 1e6:>7.1f} MB')


if __name__ == '__main__':
    main()
