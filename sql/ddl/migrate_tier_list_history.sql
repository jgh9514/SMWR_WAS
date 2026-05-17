-- 사용자 티어리스트 저장 히스토리
-- 로그인한 사용자가 만든 티어리스트를 저장하고 불러올 수 있는 테이블

CREATE TABLE IF NOT EXISTS public.tier_list_history (
    id          BIGSERIAL       PRIMARY KEY,
    user_id     VARCHAR(100)    NOT NULL,
    title       VARCHAR(200)    NOT NULL DEFAULT '내 티어리스트',
    tier_data   TEXT            NOT NULL,  -- JSON string: [{l, c, m:[monster_id,...]}]
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tier_list_history_user
    ON public.tier_list_history (user_id, created_at DESC);

COMMENT ON TABLE  public.tier_list_history              IS '사용자 티어리스트 저장 히스토리';
COMMENT ON COLUMN public.tier_list_history.user_id      IS '세션 유저 ID (user_account.user_id)';
COMMENT ON COLUMN public.tier_list_history.title        IS '티어리스트 제목';
COMMENT ON COLUMN public.tier_list_history.tier_data    IS 'JSON 직렬화된 티어 상태 [{l:label, c:color, m:[monster_id...]}]';
COMMENT ON COLUMN public.tier_list_history.created_at   IS '최초 저장 시각';
COMMENT ON COLUMN public.tier_list_history.updated_at   IS '마지막 수정 시각';
