-- =============================================================================
-- V2: 시청자 닉네임·역할 저장 (FR-F-03 열성 시청자 TOP5)
--
-- viewer_profiles는 원본 식별자 대신 해시만 저장하는 가명화 설계였는데, 열성
-- 시청자를 화면에 보여주려면 "누구인지" 표시할 이름이 필요하다. 채널 원본 id는
-- 여전히 해시로만 받되(수집기 hashing.py), 닉네임은 표시 목적으로 예외 저장하기로
-- 확정했다. user_role은 스트리머 본인·매니저 채팅을 열성 집계에서 빼는 근거다.
--
-- 기존 행은 NULL로 시작한다 — 원본 id가 없어 백필이 불가능하고, 해당 시청자가
-- 다음에 채팅을 치는 순간 upsert가 채운다(자연 치유). NULL인 동안 화면은
-- 해시 파생 별칭(viewer-a91c)으로 폴백한다 (API 11.5의 viewerAlias 형식).
-- =============================================================================

ALTER TABLE viewer_profiles
    ADD COLUMN nickname  VARCHAR(100),
    ADD COLUMN user_role VARCHAR(20);

COMMENT ON COLUMN viewer_profiles.nickname
    IS '마지막으로 관측된 표시용 닉네임. 닉변 시 최신 값으로 갱신되며 미관측이면 NULL';
COMMENT ON COLUMN viewer_profiles.user_role
    IS '치지직 userRoleCode 어휘(streamer/manager/common_user). streamer·manager는 열성 집계 제외';
