#!/bin/sh
# 컨테이너가 켜질 때 서버보다 먼저 실행된다.
#
# 하는 일은 하나: 채팅 감정 분류 모델이 없으면 내려받는다.
#   - BAKE_MODEL=true로 구운 이미지  → 이미 있으므로 건너뛴다
#   - 볼륨에 이미 받아둔 경우          → 건너뛴다
#   - 둘 다 아닌 첫 실행              → Google Drive에서 받는다 (641MB, 1~3분)
#
# 채팅 감정 분류(8001) 외의 서버는 모델이 필요 없으므로 확인 자체를 건너뛴다.
set -e

MODEL_DIR="${CHATAI_MODEL_DIR:-/app/chat-ai/models/kcelectra-chat-8emo-onnx}"

# 모델이 필요한 서비스에서만 CHATAI_NEEDS_MODEL=1을 준다 (compose에서 지정).
# 명령 문자열을 훑어 판단하지 않는 이유: 'chat-ai'라는 단어가 경로·인자 어디에나
# 나올 수 있어, 모델이 필요 없는 명령에서도 641MB를 받는 오작동이 난다.
if [ "$CHATAI_NEEDS_MODEL" = "1" ] && [ ! -f "$MODEL_DIR/model.onnx" ]; then
    echo "[entrypoint] 감정 분류 모델이 없습니다. 내려받는 중... (641MB)"
    if ! (cd /app/chat-ai && python fetch_model.py); then
        echo "[entrypoint] 모델 다운로드 실패." >&2
        echo "[entrypoint] Google Drive 공유 설정('링크가 있는 모든 사용자')을" >&2
        echo "[entrypoint] 확인하거나, BAKE_MODEL=true로 빌드한 이미지를 쓰세요." >&2
        exit 1
    fi
    echo "[entrypoint] 모델 준비 완료."
fi

# 자리를 넘겨준다. exec를 써야 uvicorn이 PID 1이 되어
# docker stop의 종료 신호를 직접 받고 우아하게 종료할 수 있다.
# (이 스크립트가 PID 1로 남으면 신호가 전달되지 않아 강제 종료된다)
exec "$@"
