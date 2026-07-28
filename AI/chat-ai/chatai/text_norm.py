r"""
채팅 텍스트 정규화 — 학습과 서빙이 반드시 공유해야 하는 단일 기준

⚠️ 이 파일이 유일한 정본이다. 다른 곳에 복사하지 말고 import 할 것.
   학습 때와 서빙 때의 전처리가 조금이라도 다르면 모델 성능이 조용히
   떨어지고 원인 추적이 매우 어렵다 (train-serve skew).

백엔드(Spring/Java 등 다른 언어)에서 재구현할 경우:
    python text_norm.py --export-tests
로 테스트 벡터(text_norm_testcases.json)를 뽑아 검증할 것.
모든 입력에 대해 동일한 출력이 나와야 한다.

--- 정규화 사양 (순서 중요: 1 → 2 → 3) ---

1) 이모티콘 코드 제거
   치지직 커스텀 이모티콘은 본문에 `{:코드명:}` 형태로 들어온다 (예: "{:d_1:}").
   시각 정보라 텍스트 모델에는 노이즈이므로 공백으로 치환한다.
   정규식  \{:[a-zA-Z0-9_]+:\}  →  ' '

2) 반복 문자 압축 (최대 3개)
   "ㅋㅋㅋㅋㅋㅋㅋㅋ" 같은 도배성 반복은 길이만 늘리고 의미는 같다.
   3개를 초과하는 반복을 정확히 3개로 줄인다. 2개는 그대로 둔다.
   정규식  (.)\1{2,}  →  \1\1\1
   예) ㅋㅋㅋㅋㅋㅋ → ㅋㅋㅋ  /  ㅋㅋ → ㅋㅋ  /  ㅋㅋㅋ → ㅋㅋㅋ

3) 공백 정규화
   연속 공백·탭·개행을 단일 공백으로 합치고 앞뒤 공백을 제거한다.
   (1번에서 생긴 공백을 정리하는 역할도 겸한다)

주의: 순서를 바꾸면 결과가 달라진다. 반드시 1 → 2 → 3.
"""
import argparse
import json
import re

EMOTE_RE = re.compile(r'\{:[a-zA-Z0-9_]+:\}')
REPEAT_RE = re.compile(r'(.)\1{2,}')


def normalize(text):
    """채팅 원문 → 모델 입력 텍스트. 학습·서빙 공통."""
    text = EMOTE_RE.sub(' ', str(text))
    text = REPEAT_RE.sub(r'\1\1\1', text)
    return ' '.join(text.split())


# 다른 언어로 재구현할 때 반드시 통과해야 하는 테스트 벡터
TEST_CASES = [
    ('ㅋㅋㅋㅋㅋㅋㅋㅋ', 'ㅋㅋㅋ'),
    ('ㅋㅋ', 'ㅋㅋ'),
    ('ㅋㅋㅋ', 'ㅋㅋㅋ'),
    ('{:d_1:}{:d_1:}', ''),
    ('와{:smFm:}대박', '와 대박'),
    ('  앞뒤   공백  ', '앞뒤 공백'),
    ('줄바꿈\n포함\t탭', '줄바꿈 포함 탭'),
    ('ㅠㅠㅠㅠㅠ 아깝다', 'ㅠㅠㅠ 아깝다'),
    ('!!!!!!', '!!!'),
    ('가나다', '가나다'),
    ('{:a:}ㅋㅋㅋㅋ{:b:}', 'ㅋㅋㅋ'),
    ('개웃기네ㅋㅋㅋㅋㅋㅋㅋㅋㅋㅋ', '개웃기네ㅋㅋㅋ'),
    ('', ''),
    ('a', 'a'),
    ('aaaa bbbb', 'aaa bbb'),
]


def run_tests():
    failed = 0
    for src, expected in TEST_CASES:
        got = normalize(src)
        ok = got == expected
        if not ok:
            failed += 1
        print(f'  {"OK  " if ok else "FAIL"} {src!r} → {got!r}'
              + ('' if ok else f'   (기대: {expected!r})'))
    print(f'\n{len(TEST_CASES) - failed}/{len(TEST_CASES)} 통과')
    return failed == 0


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--export-tests', action='store_true',
                        help='백엔드 재구현 검증용 테스트 벡터를 JSON으로 내보냄')
    args = parser.parse_args()

    if args.export_tests:
        out = 'text_norm_testcases.json'
        with open(out, 'w', encoding='utf-8') as f:
            json.dump([{'input': s, 'expected': e} for s, e in TEST_CASES],
                      f, ensure_ascii=False, indent=2)
        print(f'테스트 벡터 {len(TEST_CASES)}건 저장 → {out}')
        print('백엔드 구현이 이 입력들에 대해 동일한 출력을 내야 한다.')
    else:
        run_tests()
