FROM ghcr.io/astral-sh/uv:0.9.30 AS uv

FROM jenkins/jenkins:lts-jdk21

USER root

RUN apt-get update \
        && apt-get install -y --no-install-recommends \
            python3 \
            python3-venv \
            openssh-client \
    && rm -rf /var/lib/apt/lists/*

COPY --from=uv /uv /usr/local/bin/uv

USER jenkins
