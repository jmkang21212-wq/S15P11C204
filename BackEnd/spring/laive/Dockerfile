# syntax=docker/dockerfile:1
#
# Spring 서버 이미지 (8080)
#
# 빌드:
#   docker compose build spring
#   docker compose up -d --build spring     # 코드를 고친 뒤 재배포할 때
#
# 2단계로 나눈다. 빌드에는 JDK와 Gradle(약 700MB)이 필요하지만 실행에는 JRE만
# 있으면 되므로, 최종 이미지에는 jar와 JRE만 남긴다. 한 단계로 만들면 이미지가
# 3배 이상 커지고 빌드 도구가 운영 서버에 그대로 실려 나간다.

# ── 1단계: 빌드 ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /build

# 소스보다 먼저 복사한다. 자바 코드만 고치면 아래 Gradle 배포본 내려받기
# 레이어가 캐시에서 재사용되어 재빌드가 훨씬 빨라진다.
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./

# CRLF 제거 후 실행 권한.
#
# Windows의 git은 체크아웃할 때 LF를 CRLF로 바꾼다. 그대로 빌드하면 리눅스가
# 셔뱅을 '#!/bin/sh\r'로 읽어 즉시 실패한다.
#     ./gradlew: /bin/sh^M: bad interpreter: No such file or directory
# 저장소 루트의 .gitattributes에 gradlew를 추가해 두었지만, 그 전에 clone한
# 작업 트리는 이미 CRLF다. 빌드 시점에도 한 번 더 막는다.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# --mount=type=cache: Gradle 홈(배포본 + 의존성 캐시)을 이미지 레이어가 아니라
#   빌드 캐시에 둔다. 이미지가 커지지 않으면서 두 번째 빌드부터는 재다운로드가
#   없다. 서버에서 git pull → 재빌드를 반복할 때 체감 차이가 크다.
# --no-daemon: 컨테이너는 빌드 후 사라지므로 데몬을 띄워 봐야 메모리만 먹는다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --version

COPY src src

# bootJar만 실행한다(build가 아니라). 테스트는 CI나 로컬에서 돌리고, 이미지
# 빌드는 산출물만 만든다 — 여기서 테스트를 돌리면 DB가 없어 실패하고,
# 배포가 테스트 환경에 묶인다.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar && \
    cp "$(ls build/libs/*.jar | grep -v -- '-plain' | head -1)" /build/app.jar

# ── 2단계: 실행 ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

# curl은 compose healthcheck(/actuator/health)에서 쓴다. JRE 이미지에는 없다.
RUN apt-get update && apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# root로 돌리지 않는다. 컨테이너가 뚫려도 호스트로 번지는 경로를 줄인다.
RUN groupadd --system laive && useradd --system --gid laive --home /app laive

WORKDIR /app

COPY --from=build /build/app.jar app.jar

# 클립 저장 위치(laive.clip.storage-dir 기본값). compose에서 named volume을
# 여기에 붙인다 — 호스트의 /var를 건드리지 않으면서 컨테이너를 지웠다 만들어도
# 클립이 남는다.
#
# jar에는 chown을 걸지 않는다. 기본 퍼미션(644)으로 이미 읽을 수 있는데,
# chown -R을 /app에 걸면 파일 메타데이터가 바뀌면서 127MB짜리 jar 레이어가
# 통째로 한 벌 더 생긴다(이미지가 그만큼 커진다).
RUN install -d -o laive -g laive /var/laive/clips

USER laive

EXPOSE 8080

# TZ: 로그·리포트 시각이 UTC로 찍히는 것을 막는다. Postgres 쪽도 Asia/Seoul이다.
# MaxRAMPercentage: 컨테이너 메모리 한도의 70%까지만 힙으로 쓴다. 기본값(25%)은
#   너무 보수적이고, 100%에 가까우면 JVM 바깥(스레드 스택·메타스페이스)이 눌려
#   OOM Killer에 걸린다.
ENV TZ=Asia/Seoul \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Seoul"

# sh -c + exec: JAVA_OPTS를 셸이 펼치게 하면서도 java가 PID 1을 물려받게 한다.
# exec가 없으면 셸이 PID 1로 남아 SIGTERM을 java에 전달하지 않고, 컨테이너를
# 멈출 때마다 10초 타임아웃 뒤 강제 종료된다(진행 중이던 트랜잭션이 끊긴다).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
