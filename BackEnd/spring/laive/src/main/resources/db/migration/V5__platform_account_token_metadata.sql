-- =============================================================================
-- LAIVE - 1인 스트리머 AI 매니저
-- V5: platform_accounts 토큰 메타데이터
--
-- 배경
--   치지직 Open API는 Access Token 1일, Refresh Token 30일의 수명을 가지며
--   Refresh Token은 1회용으로 회전된다. 갱신 시점을 판단하고 재연동 안내를
--   하려면 각 토큰의 만료 시각을 저장해야 한다.
--   scope는 애플리케이션 등록 시 승인된 목록이 그대로 부여되며 토큰 발급
--   응답에 공백으로 구분된 한글 표시명 문자열로 내려온다.
--   ("채널 정보 조회 채널 관리자 조회 ...") 구분자와 항목명 안의 공백을
--   구별할 수 없으므로 파싱하지 않고 원문 그대로 보관한다.
-- =============================================================================

ALTER TABLE platform_accounts
    ADD COLUMN access_token_expires_at  TIMESTAMPTZ,
    ADD COLUMN refresh_token_expires_at TIMESTAMPTZ,
    ADD COLUMN scope                    TEXT;

COMMENT ON COLUMN platform_accounts.access_token_expires_at
    IS '플랫폼 Access Token 만료 시각. 이 시각 직전에 Refresh Token으로 갱신한다';
COMMENT ON COLUMN platform_accounts.refresh_token_expires_at
    IS '플랫폼 Refresh Token 만료 시각. 지나면 재연동(REAUTH_REQUIRED)만 가능하다';
COMMENT ON COLUMN platform_accounts.scope
    IS '플랫폼이 부여한 scope 원문. 공백 구분 표시명이므로 파싱하지 않는다';
