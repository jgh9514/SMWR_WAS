-- SWEX 계정 요약(룬/몬스터) 저장용 테이블
-- 실행 대상: PostgreSQL (summonerswar DB)

-- 1) 임포트(원본 JSON 저장)
CREATE TABLE IF NOT EXISTS swex_account_import (
	import_id      BIGSERIAL PRIMARY KEY,
	user_id        VARCHAR(50) NOT NULL,
	source_filename VARCHAR(255),
	wizard_id      BIGINT,
	wizard_name    VARCHAR(100),
	server_id      INTEGER,
	unit_count     INTEGER NOT NULL DEFAULT 0,
	rune_count     INTEGER NOT NULL DEFAULT 0,
	raw_json       JSONB NOT NULL,
	crt_user_id    VARCHAR(50),
	crt_date       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	upt_user_id    VARCHAR(50),
	upt_date       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_swex_account_import_user_date
	ON swex_account_import (user_id, crt_date DESC);

-- 2) 몬스터(보유 유닛)
CREATE TABLE IF NOT EXISTS swex_monster (
	import_id    BIGINT NOT NULL,
	unit_id      BIGINT NOT NULL,
	master_id    INTEGER,
	level        INTEGER,
	stars        INTEGER,
	attribute    INTEGER,
	awaken_level INTEGER,
	is_awakened  INTEGER,
	PRIMARY KEY (import_id, unit_id),
	CONSTRAINT fk_swex_monster_import
		FOREIGN KEY (import_id) REFERENCES swex_account_import (import_id)
		ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_swex_monster_import_master
	ON swex_monster (import_id, master_id);

-- 3) 룬
CREATE TABLE IF NOT EXISTS swex_rune (
	import_id        BIGINT NOT NULL,
	rune_id          BIGINT NOT NULL,
	unit_id          BIGINT,
	slot             INTEGER,
	set_id           INTEGER,
	grade            INTEGER,
	level            INTEGER,
	rank             INTEGER,
	main_stat_type   INTEGER,
	main_stat_value  INTEGER,
	-- SWEX 원본 룬 스탯 정보
	innate_stat_type   INTEGER,
	innate_stat_value  INTEGER,
	pri_eff_json      JSONB,
	prefix_eff_json   JSONB,
	substats_json     JSONB,
	raw_json          JSONB,
	PRIMARY KEY (import_id, rune_id),
	CONSTRAINT fk_swex_rune_import
		FOREIGN KEY (import_id) REFERENCES swex_account_import (import_id)
		ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_swex_rune_import_unit
	ON swex_rune (import_id, unit_id);

-- 기존 설치된 DB용(안전) ALTER
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS innate_stat_type INTEGER;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS innate_stat_value INTEGER;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS pri_eff_json JSONB;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS prefix_eff_json JSONB;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS raw_json JSONB;

-- SWEX Basic Info 추가 컬럼
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS wizard_id BIGINT;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS occupied_type INTEGER;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS occupied_id BIGINT;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS extra INTEGER;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS base_value INTEGER;
ALTER TABLE swex_rune ADD COLUMN IF NOT EXISTS sell_value INTEGER;


