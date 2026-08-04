# AI 서버 3종 공용 이미지 (8000 음성 / 8001 채팅감정 / 8002 채팅수집)
#
# 셋이 같은 venv·pyproject를 공유하므로 이미지도 하나다.
# 무엇을 띄울지는 compose의 command로 정한다.
#
# 빌드:
#   docker build -t laive-ai .                          # 모델 미포함 (개발용, 가볍다)
#   docker build --build-arg BAKE_MODEL=true -t laive-ai .   # 모델 포함 (배포용)
#
# BAKE_MODEL=true로 구우면 이미지가 641MB 커지는 대신, 그 이미지를 받는 쪽은
# Google Drive에 접근하지 않고 바로 뜬다. 레지스트리에 push해두면 Drive가
# 죽어도 배포·재시작에 영향이 없다.

FROM python:3.12-slim

# uv: pyproject/uv.lock을 그대로 쓰기 위해. 복사만으로 설치된다
COPY --from=ghcr.io/astral-sh/uv:0.9.30 /uv /usr/local/bin/uv

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/app/.venv \
    PATH="/app/.venv/bin:$PATH"

# ── 의존성 레이어 ────────────────────────────────────────────────
# 소스보다 먼저 복사한다. 코드만 바뀌면 이 레이어가 캐시에서 재사용되어
# 재빌드가 수 초로 끝난다. 순서를 바꾸면 매번 전체 재설치가 된다.
# --no-dev: pytest·fakeredis를 운영 이미지에서 제외
# --frozen: uv.lock을 그대로 따른다 (빌드 중 lock이 바뀌면 실패시켜 재현성 보장)
# --no-cache: uv 캐시(약 1GB)가 레이어에 남지 않도록. 캐시를 남기면
#   이미지가 그만큼 커지는데, 컨테이너 안에서 재설치할 일이 없어 쓸모가 없다.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project --no-cache

# ── 소스 레이어 ──────────────────────────────────────────────────
COPY . .

# ── 모델 (선택) ──────────────────────────────────────────────────
# false면 건너뛴다. 그 경우 entrypoint가 첫 실행 때 볼륨으로 내려받는다.
ARG BAKE_MODEL=false
RUN if [ "$BAKE_MODEL" = "true" ]; then \
        echo "모델을 이미지에 포함합니다 (641MB)..." && \
        cd chat-ai && python fetch_model.py; \
    fi

# 로그 디렉터리를 미리 만든다 (볼륨을 안 붙여도 서버가 뜨도록)
RUN mkdir -p /app/logs /app/chat-ai/logs

# CRLF 제거 후 실행 권한.
#
# Windows의 git은 기본값(core.autocrlf=true)으로 체크아웃할 때 LF를 CRLF로 바꾼다.
# 그대로 빌드하면 리눅스가 셔뱅을 '#!/bin/sh\r'로 읽어 컨테이너가 즉시 죽는다.
#     exec /app/entrypoint.sh: no such file or directory
#
# 저장소 루트의 .gitattributes가 근본 해결이지만, 그 파일이 생기기 전에 clone한
# 작업 트리는 이미 CRLF다. 빌드 시점에도 한 번 더 막는다.
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod +x /app/entrypoint.sh
ENTRYPOINT ["/app/entrypoint.sh"]

# 기본값은 채팅 감정 분류. compose에서 서비스별로 덮어쓴다
CMD ["uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8001", "--app-dir", "chat-ai"]
