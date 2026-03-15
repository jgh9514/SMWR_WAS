-- public.battle_log_list definition

-- Drop table

-- DROP TABLE public.battle_log_list;

CREATE TABLE public.battle_log_list (
	match_id varchar NOT NULL, -- 배틀 id
	log_id varchar NULL, -- 로그 id
	log_timestamp varchar NOT NULL, -- 타임스탬프
	base_number varchar NOT NULL, -- 거점 번호
	guild_id varchar NOT NULL, -- 공격 길드 id
	wizard_id varchar NOT NULL, -- 공격 유저 id
	wizard_name varchar NOT NULL, -- 공격 유저 명
	opp_guild_id varchar NOT NULL, -- 방어 길드 id
	opp_wizard_id varchar NOT NULL, -- 방어 유저 id
	opp_wizard_name varchar NOT NULL, -- 방어 유저 명
	win_lose varchar NOT NULL, -- 공성 여부
	request_type varchar NOT NULL, -- 결과 상태
	channel_uid varchar NULL,
	opp_channel_uid varchar NULL
);

-- Column comments

COMMENT ON COLUMN public.battle_log_list.match_id IS '배틀 id';
COMMENT ON COLUMN public.battle_log_list.log_id IS '로그 id';
COMMENT ON COLUMN public.battle_log_list.log_timestamp IS '타임스탬프';
COMMENT ON COLUMN public.battle_log_list.base_number IS '거점 번호';
COMMENT ON COLUMN public.battle_log_list.guild_id IS '공격 길드 id';
COMMENT ON COLUMN public.battle_log_list.wizard_id IS '공격 유저 id';
COMMENT ON COLUMN public.battle_log_list.wizard_name IS '공격 유저 명';
COMMENT ON COLUMN public.battle_log_list.opp_guild_id IS '방어 길드 id';
COMMENT ON COLUMN public.battle_log_list.opp_wizard_id IS '방어 유저 id';
COMMENT ON COLUMN public.battle_log_list.opp_wizard_name IS '방어 유저 명';
COMMENT ON COLUMN public.battle_log_list.win_lose IS '공성 여부';
COMMENT ON COLUMN public.battle_log_list.request_type IS '결과 상태';


-- public.battle_log_list_copy definition

-- Drop table

-- DROP TABLE public.battle_log_list_copy;

CREATE TABLE public.battle_log_list_copy (
	log_timestamp varchar NOT NULL, -- 배틀 id
	match_id varchar NOT NULL, -- 매치 id
	base_number varchar NOT NULL, -- 거점 번호
	guild_id varchar NOT NULL, -- 공격 길드 id
	wizard_id varchar NOT NULL, -- 공격 유저 id
	wizard_name varchar NOT NULL, -- 공격 유저 명
	opp_guild_id varchar NOT NULL, -- 방어 길드 id
	opp_wizard_id varchar NOT NULL, -- 방어 유저 id
	opp_wizard_name varchar NOT NULL, -- 방어 유저 명
	win_lose varchar NOT NULL, -- 공성 여부
	request_type varchar NOT NULL -- 결과 상태
);
CREATE INDEX idx_battle_log_list ON public.battle_log_list_copy USING btree (match_id, log_timestamp);

-- Column comments

COMMENT ON COLUMN public.battle_log_list_copy.log_timestamp IS '배틀 id';
COMMENT ON COLUMN public.battle_log_list_copy.match_id IS '매치 id';
COMMENT ON COLUMN public.battle_log_list_copy.base_number IS '거점 번호';
COMMENT ON COLUMN public.battle_log_list_copy.guild_id IS '공격 길드 id';
COMMENT ON COLUMN public.battle_log_list_copy.wizard_id IS '공격 유저 id';
COMMENT ON COLUMN public.battle_log_list_copy.wizard_name IS '공격 유저 명';
COMMENT ON COLUMN public.battle_log_list_copy.opp_guild_id IS '방어 길드 id';
COMMENT ON COLUMN public.battle_log_list_copy.opp_wizard_id IS '방어 유저 id';
COMMENT ON COLUMN public.battle_log_list_copy.opp_wizard_name IS '방어 유저 명';
COMMENT ON COLUMN public.battle_log_list_copy.win_lose IS '공성 여부';
COMMENT ON COLUMN public.battle_log_list_copy.request_type IS '결과 상태';


-- public.dungeon_master definition

-- Drop table

-- DROP TABLE public.dungeon_master;

CREATE TABLE public.dungeon_master (
	dungeon_id int4 NOT NULL, -- 던전 ID (Swarfarm API ID)
	enabled bool DEFAULT true NULL, -- 활성화 여부
	"name" varchar(200) NOT NULL, -- 던전명
	slug varchar(200) NULL, -- URL 슬러그
	category varchar(100) NULL, -- 카테고리 (Secret Dungeon, Cairos Dungeon, Dimensional Hole 등)
	icon varchar(200) NULL, -- 아이콘 파일명
	icon_path varchar(500) NULL, -- 아이콘 저장 경로
	swarfarm_url varchar(500) NULL, -- Swarfarm API URL
	last_sync_date timestamp NULL, -- 마지막 동기화 일시
	crt_user_id varchar(50) DEFAULT 'SYSTEM'::character varying NULL,
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	upt_user_id varchar(50) DEFAULT 'SYSTEM'::character varying NULL,
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_dungeon_master PRIMARY KEY (dungeon_id)
);
CREATE INDEX idx_dungeon_master_category ON public.dungeon_master USING btree (category);
CREATE INDEX idx_dungeon_master_enabled ON public.dungeon_master USING btree (enabled);
CREATE INDEX idx_dungeon_master_last_sync_date ON public.dungeon_master USING btree (last_sync_date);
CREATE INDEX idx_dungeon_master_name ON public.dungeon_master USING btree (name);
COMMENT ON TABLE public.dungeon_master IS '던전 마스터 정보';

-- Column comments

COMMENT ON COLUMN public.dungeon_master.dungeon_id IS '던전 ID (Swarfarm API ID)';
COMMENT ON COLUMN public.dungeon_master.enabled IS '활성화 여부';
COMMENT ON COLUMN public.dungeon_master."name" IS '던전명';
COMMENT ON COLUMN public.dungeon_master.slug IS 'URL 슬러그';
COMMENT ON COLUMN public.dungeon_master.category IS '카테고리 (Secret Dungeon, Cairos Dungeon, Dimensional Hole 등)';
COMMENT ON COLUMN public.dungeon_master.icon IS '아이콘 파일명';
COMMENT ON COLUMN public.dungeon_master.icon_path IS '아이콘 저장 경로';
COMMENT ON COLUMN public.dungeon_master.swarfarm_url IS 'Swarfarm API URL';
COMMENT ON COLUMN public.dungeon_master.last_sync_date IS '마지막 동기화 일시';


-- public.guild definition

-- Drop table

-- DROP TABLE public.guild;

CREATE TABLE public.guild (
	guild_id int8 DEFAULT nextval('guild_seq'::regclass) NOT NULL, -- 길드 ID (시퀀스 자동 생성)
	guild_name varchar(100) NOT NULL, -- 길드명
	guild_description varchar(500) NULL, -- 길드 설명
	guild_leader_id varchar(20) NULL, -- 길드장 ID
	max_members int4 DEFAULT 30 NOT NULL, -- 최대 인원수
	current_members int4 DEFAULT 0 NOT NULL, -- 현재 인원수
	join_type varchar(10) DEFAULT 'APPROVAL'::character varying NOT NULL, -- 가입 방식 (APPROVAL: 승인 필요, INVITE: 초대 코드로만 가입)
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	crt_user_id varchar(50) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	invite_key varchar(10) NULL, -- 초대 키 (10자리 랜덤 문자열, 고유값)
	CONSTRAINT pk_guild PRIMARY KEY (guild_id),
	CONSTRAINT uk_guild_invite_key UNIQUE (invite_key)
);
CREATE INDEX idx_guild_invite_key ON public.guild USING btree (invite_key);
CREATE INDEX idx_guild_leader_id ON public.guild USING btree (guild_leader_id);
CREATE INDEX idx_guild_name ON public.guild USING btree (guild_name);
CREATE INDEX idx_guild_usg_del ON public.guild USING btree (usg_yn, del_yn);
COMMENT ON TABLE public.guild IS '길드 정보';

-- Column comments

COMMENT ON COLUMN public.guild.guild_id IS '길드 ID (시퀀스 자동 생성)';
COMMENT ON COLUMN public.guild.guild_name IS '길드명';
COMMENT ON COLUMN public.guild.guild_description IS '길드 설명';
COMMENT ON COLUMN public.guild.guild_leader_id IS '길드장 ID';
COMMENT ON COLUMN public.guild.max_members IS '최대 인원수';
COMMENT ON COLUMN public.guild.current_members IS '현재 인원수';
COMMENT ON COLUMN public.guild.join_type IS '가입 방식 (APPROVAL: 승인 필요, INVITE: 초대 코드로만 가입)';
COMMENT ON COLUMN public.guild.usg_yn IS '사용여부';
COMMENT ON COLUMN public.guild.del_yn IS '삭제여부';
COMMENT ON COLUMN public.guild.crt_user_id IS '등록자';
COMMENT ON COLUMN public.guild.crt_date IS '등록일';
COMMENT ON COLUMN public.guild.upt_user_id IS '수정자';
COMMENT ON COLUMN public.guild.upt_date IS '수정일';
COMMENT ON COLUMN public.guild.invite_key IS '초대 키 (10자리 랜덤 문자열, 고유값)';


-- public.guild_siege_battle_log definition

-- Drop table

-- DROP TABLE public.guild_siege_battle_log;

CREATE TABLE public.guild_siege_battle_log (
	match_id varchar NOT NULL, -- 매치 id
	guild_id varchar NOT NULL, -- 길드 id
	guild_name varchar NOT NULL, -- 길드 명
	rating_id varchar NOT NULL, -- 길드 등급
	match_rank varchar NOT NULL -- 매치 순위
);
COMMENT ON TABLE public.guild_siege_battle_log IS '점령전 이력';

-- Column comments

COMMENT ON COLUMN public.guild_siege_battle_log.match_id IS '매치 id';
COMMENT ON COLUMN public.guild_siege_battle_log.guild_id IS '길드 id';
COMMENT ON COLUMN public.guild_siege_battle_log.guild_name IS '길드 명';
COMMENT ON COLUMN public.guild_siege_battle_log.rating_id IS '길드 등급';
COMMENT ON COLUMN public.guild_siege_battle_log.match_rank IS '매치 순위';


-- public.guild_siege_season definition

-- Drop table

-- DROP TABLE public.guild_siege_season;

CREATE TABLE public.guild_siege_season (
	season_no int4 NOT NULL,
	start_date date NOT NULL,
	end_date date NULL,
	crt_user_id varchar(50) NOT NULL,
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	upt_user_id varchar(50) NOT NULL,
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_guild_siege_season PRIMARY KEY (season_no)
);


-- public.leader_skill_master definition

-- Drop table

-- DROP TABLE public.leader_skill_master;

CREATE TABLE public.leader_skill_master (
	leader_skill_id int4 NOT NULL, -- 리더 스킬 ID (Swarfarm API ID)
	"attribute" varchar(100) NOT NULL, -- 속성 (Attack Power, HP, Defense, Attack Speed, Critical Rate, Resistance, Accuracy)
	amount int4 NOT NULL, -- 수치 (%)
	area varchar(50) NULL, -- 적용 영역 (Element, General, Arena, Guild, Dungeon)
	"element" varchar(50) NULL, -- 속성 (Fire, Water, Wind, Light, Dark 또는 null)
	swarfarm_url varchar(500) NULL, -- Swarfarm API URL
	last_sync_date timestamp NULL, -- 마지막 동기화 일시
	crt_user_id varchar(50) DEFAULT 'SYSTEM'::character varying NULL,
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	upt_user_id varchar(50) DEFAULT 'SYSTEM'::character varying NULL,
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_leader_skill_master PRIMARY KEY (leader_skill_id)
);
CREATE INDEX idx_leader_skill_master_area ON public.leader_skill_master USING btree (area);
CREATE INDEX idx_leader_skill_master_attribute ON public.leader_skill_master USING btree (attribute);
CREATE INDEX idx_leader_skill_master_element ON public.leader_skill_master USING btree (element);
CREATE INDEX idx_leader_skill_master_last_sync_date ON public.leader_skill_master USING btree (last_sync_date);
COMMENT ON TABLE public.leader_skill_master IS '리더 스킬 마스터 정보';

-- Column comments

COMMENT ON COLUMN public.leader_skill_master.leader_skill_id IS '리더 스킬 ID (Swarfarm API ID)';
COMMENT ON COLUMN public.leader_skill_master."attribute" IS '속성 (Attack Power, HP, Defense, Attack Speed, Critical Rate, Resistance, Accuracy)';
COMMENT ON COLUMN public.leader_skill_master.amount IS '수치 (%)';
COMMENT ON COLUMN public.leader_skill_master.area IS '적용 영역 (Element, General, Arena, Guild, Dungeon)';
COMMENT ON COLUMN public.leader_skill_master."element" IS '속성 (Fire, Water, Wind, Light, Dark 또는 null)';
COMMENT ON COLUMN public.leader_skill_master.swarfarm_url IS 'Swarfarm API URL';
COMMENT ON COLUMN public.leader_skill_master.last_sync_date IS '마지막 동기화 일시';


-- public.level_master definition

-- Drop table

-- DROP TABLE public.level_master;

CREATE TABLE public.level_master (
	level_id int4 NOT NULL, -- 레벨 ID (Swarfarm API ID)
	dungeon_id int4 NULL, -- 던전 ID
	floor int4 NULL, -- 층수
	difficulty varchar(50) NULL, -- 난이도 (Hell, Hard, Normal 등)
	energy_cost int4 NULL, -- 에너지 비용
	xp int4 NULL, -- 경험치
	frontline_slots int4 NULL, -- 전열 슬롯 수
	backline_slots int4 NULL, -- 후열 슬롯 수
	total_slots int4 NULL, -- 전체 슬롯 수
	swarfarm_url varchar(500) NULL, -- Swarfarm API URL
	last_sync_date timestamp NULL, -- 마지막 동기화 일시
	crt_user_id varchar(50) DEFAULT 'SYSTEM'::character varying NULL,
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	upt_user_id varchar(50) DEFAULT 'SYSTEM'::character varying NULL,
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_level_master PRIMARY KEY (level_id)
);
CREATE INDEX idx_level_master_difficulty ON public.level_master USING btree (difficulty);
CREATE INDEX idx_level_master_dungeon_id ON public.level_master USING btree (dungeon_id);
CREATE INDEX idx_level_master_floor ON public.level_master USING btree (floor);
CREATE INDEX idx_level_master_last_sync_date ON public.level_master USING btree (last_sync_date);
COMMENT ON TABLE public.level_master IS '던전 레벨 마스터 정보';

-- Column comments

COMMENT ON COLUMN public.level_master.level_id IS '레벨 ID (Swarfarm API ID)';
COMMENT ON COLUMN public.level_master.dungeon_id IS '던전 ID';
COMMENT ON COLUMN public.level_master.floor IS '층수';
COMMENT ON COLUMN public.level_master.difficulty IS '난이도 (Hell, Hard, Normal 등)';
COMMENT ON COLUMN public.level_master.energy_cost IS '에너지 비용';
COMMENT ON COLUMN public.level_master.xp IS '경험치';
COMMENT ON COLUMN public.level_master.frontline_slots IS '전열 슬롯 수';
COMMENT ON COLUMN public.level_master.backline_slots IS '후열 슬롯 수';
COMMENT ON COLUMN public.level_master.total_slots IS '전체 슬롯 수';
COMMENT ON COLUMN public.level_master.swarfarm_url IS 'Swarfarm API URL';
COMMENT ON COLUMN public.level_master.last_sync_date IS '마지막 동기화 일시';


-- public.monster definition

-- Drop table

-- DROP TABLE public.monster;

CREATE TABLE public.monster (
	monster_id varchar(100) NOT NULL, -- 몬스터 ID
	monster_elemental varchar(100) NOT NULL, -- 엘리멘탈 속성
	kr_name varchar(100) NOT NULL, -- 이름(한글)
	un_name varchar(100) NOT NULL, -- 이름(영문)
	star_type varchar NOT NULL, -- 별 타입
	star int8 NOT NULL, -- 별 개수
	arousal_type varchar NULL, -- 각성 여부
	image_url varchar(100) NOT NULL, -- 다운로드 받은 대표 이미지의 로컬 저장 경로
	leader_id varchar(100) NULL, -- 리더 ID
	crt_user_id varchar(50) DEFAULT 'jgh9514'::character varying NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) DEFAULT 'jgh9514'::character varying NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	swarfarm_id int4 NULL, -- Swarfarm API ID
	com2us_id int4 NULL, -- Com2us 게임 내 몬스터 ID
	family_id int4 NULL, -- 몬스터 패밀리 ID
	skill_group_id int4 NULL, -- 스킬 그룹 ID
	bestiary_slug varchar(200) NULL, -- Swarfarm Bestiary Slug
	image_filename varchar(300) NULL, -- Swarfarm에서 내려주는 원본 이미지 파일명
	archetype varchar(50) NULL, -- 아키타입 (Attack, Defense, HP, Support)
	base_stars int4 NULL, -- 기본 별 개수
	natural_stars int4 NULL, -- 자연 별 개수
	obtainable bool DEFAULT true NULL, -- 획득 가능 여부
	can_awaken bool DEFAULT false NULL, -- 각성 가능 여부
	awaken_level int4 DEFAULT 0 NULL, -- 각성 레벨
	awaken_bonus text NULL, -- 각성 보너스
	skill_ups_to_max int4 NULL, -- 최대 스킬업 필요 개수
	fusion_food bool DEFAULT false NULL, -- 퓨전 재료 여부
	homunculus bool DEFAULT false NULL, -- 호문쿨루스 여부
	base_hp int4 NULL,
	base_attack int4 NULL,
	base_defense int4 NULL,
	speed int4 NULL,
	crit_rate int4 NULL,
	crit_damage int4 NULL,
	resistance int4 NULL,
	accuracy int4 NULL,
	raw_hp int4 NULL,
	raw_attack int4 NULL,
	raw_defense int4 NULL,
	max_lvl_hp int4 NULL,
	max_lvl_attack int4 NULL,
	max_lvl_defense int4 NULL,
	awakens_from_id int4 NULL,
	awakens_to_id int4 NULL,
	transforms_to_id int4 NULL,
	swarfarm_url varchar(500) NULL,
	last_sync_date timestamp NULL, -- 마지막 동기화 일시
	CONSTRAINT monster_pk PRIMARY KEY (monster_id)
);
CREATE INDEX idx_monster_com2us_id ON public.monster USING btree (com2us_id);
CREATE INDEX idx_monster_family_id ON public.monster USING btree (family_id);
CREATE INDEX idx_monster_last_sync_date ON public.monster USING btree (last_sync_date);
CREATE INDEX idx_monster_swarfarm_id ON public.monster USING btree (swarfarm_id);
COMMENT ON TABLE public.monster IS '몬스터 정보';

-- Column comments

COMMENT ON COLUMN public.monster.monster_id IS '몬스터 ID';
COMMENT ON COLUMN public.monster.monster_elemental IS '엘리멘탈 속성';
COMMENT ON COLUMN public.monster.kr_name IS '이름(한글)';
COMMENT ON COLUMN public.monster.un_name IS '이름(영문)';
COMMENT ON COLUMN public.monster.star_type IS '별 타입';
COMMENT ON COLUMN public.monster.star IS '별 개수';
COMMENT ON COLUMN public.monster.arousal_type IS '각성 여부';
COMMENT ON COLUMN public.monster.image_url IS '다운로드 받은 대표 이미지의 로컬 저장 경로';
COMMENT ON COLUMN public.monster.leader_id IS '리더 ID';
COMMENT ON COLUMN public.monster.crt_user_id IS '등록자';
COMMENT ON COLUMN public.monster.crt_date IS '등록일';
COMMENT ON COLUMN public.monster.upt_user_id IS '수정자';
COMMENT ON COLUMN public.monster.upt_date IS '수정일';
COMMENT ON COLUMN public.monster.swarfarm_id IS 'Swarfarm API ID';
COMMENT ON COLUMN public.monster.com2us_id IS 'Com2us 게임 내 몬스터 ID';
COMMENT ON COLUMN public.monster.family_id IS '몬스터 패밀리 ID';
COMMENT ON COLUMN public.monster.skill_group_id IS '스킬 그룹 ID';
COMMENT ON COLUMN public.monster.bestiary_slug IS 'Swarfarm Bestiary Slug';
COMMENT ON COLUMN public.monster.image_filename IS 'Swarfarm에서 내려주는 원본 이미지 파일명';
COMMENT ON COLUMN public.monster.archetype IS '아키타입 (Attack, Defense, HP, Support)';
COMMENT ON COLUMN public.monster.base_stars IS '기본 별 개수';
COMMENT ON COLUMN public.monster.natural_stars IS '자연 별 개수';
COMMENT ON COLUMN public.monster.obtainable IS '획득 가능 여부';
COMMENT ON COLUMN public.monster.can_awaken IS '각성 가능 여부';
COMMENT ON COLUMN public.monster.awaken_level IS '각성 레벨';
COMMENT ON COLUMN public.monster.awaken_bonus IS '각성 보너스';
COMMENT ON COLUMN public.monster.skill_ups_to_max IS '최대 스킬업 필요 개수';
COMMENT ON COLUMN public.monster.fusion_food IS '퓨전 재료 여부';
COMMENT ON COLUMN public.monster.homunculus IS '호문쿨루스 여부';
COMMENT ON COLUMN public.monster.last_sync_date IS '마지막 동기화 일시';


-- public.monster_collaboration_mapping definition

-- Drop table

-- DROP TABLE public.monster_collaboration_mapping;

CREATE TABLE public.monster_collaboration_mapping (
	original_monster_id varchar(20) NOT NULL, -- 오리지날 몬스터 id
	collaboration_monster_id varchar(20) NOT NULL, -- 콜라보 몬스터 id
	CONSTRAINT monster_collaboration_mapping_unique UNIQUE (original_monster_id)
);

-- Column comments

COMMENT ON COLUMN public.monster_collaboration_mapping.original_monster_id IS '오리지날 몬스터 id';
COMMENT ON COLUMN public.monster_collaboration_mapping.collaboration_monster_id IS '콜라보 몬스터 id';


-- public.monster_leaders definition

-- Drop table

-- DROP TABLE public.monster_leaders;

CREATE TABLE public.monster_leaders (
	leader_id varchar(50) NOT NULL, -- 리더 Id
	"type" varchar(100) NOT NULL, -- 타입
	stat varchar(100) NOT NULL, -- 스탯
	increase_by int4 NOT NULL, -- 증가율
	icon_path varchar(200) NOT NULL,
	crt_user_id varchar(50) DEFAULT 'jgh9514'::character varying NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) DEFAULT 'jgh9514'::character varying NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT monster_leaders_pkey PRIMARY KEY (leader_id)
);
COMMENT ON TABLE public.monster_leaders IS '몬스터 리더 정보';

-- Column comments

COMMENT ON COLUMN public.monster_leaders.leader_id IS '리더 Id';
COMMENT ON COLUMN public.monster_leaders."type" IS '타입';
COMMENT ON COLUMN public.monster_leaders.stat IS '스탯';
COMMENT ON COLUMN public.monster_leaders.increase_by IS '증가율';
COMMENT ON COLUMN public.monster_leaders.crt_user_id IS '등록자';
COMMENT ON COLUMN public.monster_leaders.crt_date IS '등록일';
COMMENT ON COLUMN public.monster_leaders.upt_user_id IS '수정자';
COMMENT ON COLUMN public.monster_leaders.upt_date IS '수정일';


-- public.ranker_rtpvp_replay_list definition

-- Drop table

-- DROP TABLE public.ranker_rtpvp_replay_list;

CREATE TABLE public.ranker_rtpvp_replay_list (
	rid int8 NOT NULL, -- key
	battle_type varchar(2) NULL, -- 배틀 구분
	battle_ver varchar(20) NULL, -- 모름
	date_add timestamp NULL, -- 일시
	proto_ver varchar(20) NULL, -- 모름
	win_lose varchar(1) NULL, -- 승자 위치
	wizard_id varchar(20) NULL, -- 승자 id
	CONSTRAINT ranker_rtpvp_replay_list_pk PRIMARY KEY (rid)
);

-- Column comments

COMMENT ON COLUMN public.ranker_rtpvp_replay_list.rid IS 'key';
COMMENT ON COLUMN public.ranker_rtpvp_replay_list.battle_type IS '배틀 구분';
COMMENT ON COLUMN public.ranker_rtpvp_replay_list.battle_ver IS '모름';
COMMENT ON COLUMN public.ranker_rtpvp_replay_list.date_add IS '일시';
COMMENT ON COLUMN public.ranker_rtpvp_replay_list.proto_ver IS '모름';
COMMENT ON COLUMN public.ranker_rtpvp_replay_list.win_lose IS '승자 위치';
COMMENT ON COLUMN public.ranker_rtpvp_replay_list.wizard_id IS '승자 id';


-- public.ranker_rtpvp_replay_pick_list definition

-- Drop table

-- DROP TABLE public.ranker_rtpvp_replay_pick_list;

CREATE TABLE public.ranker_rtpvp_replay_pick_list (
	rid int8 NOT NULL, -- key
	wizard_id varchar(20) NOT NULL, -- 유저 id
	banned_slot_id int8 NULL, -- 벤 유닛 번호
	leader_slot_id int8 NULL -- 리더 유닛 번호
);

-- Column comments

COMMENT ON COLUMN public.ranker_rtpvp_replay_pick_list.rid IS 'key';
COMMENT ON COLUMN public.ranker_rtpvp_replay_pick_list.wizard_id IS '유저 id';
COMMENT ON COLUMN public.ranker_rtpvp_replay_pick_list.banned_slot_id IS '벤 유닛 번호';
COMMENT ON COLUMN public.ranker_rtpvp_replay_pick_list.leader_slot_id IS '리더 유닛 번호';


-- public.ranker_rtpvp_replay_unit_list definition

-- Drop table

-- DROP TABLE public.ranker_rtpvp_replay_unit_list;

CREATE TABLE public.ranker_rtpvp_replay_unit_list (
	rid int8 NOT NULL, -- key
	pick_slot_id int8 NOT NULL, -- 순서
	unit_master_id varchar(20) NULL, -- 유닛번호
	wizard_id varchar(20) NOT NULL, -- 유저 id
	CONSTRAINT ranker_rtpvp_replay_unit_list_pk PRIMARY KEY (rid, wizard_id, pick_slot_id)
);

-- Column comments

COMMENT ON COLUMN public.ranker_rtpvp_replay_unit_list.rid IS 'key';
COMMENT ON COLUMN public.ranker_rtpvp_replay_unit_list.pick_slot_id IS '순서';
COMMENT ON COLUMN public.ranker_rtpvp_replay_unit_list.unit_master_id IS '유닛번호';
COMMENT ON COLUMN public.ranker_rtpvp_replay_unit_list.wizard_id IS '유저 id';


-- public.ranker_rtpvp_replay_user_list definition

-- Drop table

-- DROP TABLE public.ranker_rtpvp_replay_user_list;

CREATE TABLE public.ranker_rtpvp_replay_user_list (
	rid int8 NOT NULL, -- key
	alive_count int8 NOT NULL, -- 생존 몬스터 수
	country varchar(5) NULL, -- 국가
	is_first_pick varchar(1) NULL, -- 선픽 여부
	is_placement varchar(10) NULL, -- 모름
	"rank" varchar(10) NULL, -- 등수
	rating_id varchar(10) NULL, -- 등급
	score varchar(10) NULL, -- 점수
	win_lose varchar(1) NULL, -- 승리 여부
	wizard_id varchar(20) NOT NULL, -- 유저 id
	wizard_name varchar(20) NULL, -- 유저 닉네임
	CONSTRAINT ranker_rtpvp_replay_user_list_pk PRIMARY KEY (rid, wizard_id)
);

-- Column comments

COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.rid IS 'key';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.alive_count IS '생존 몬스터 수';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.country IS '국가';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.is_first_pick IS '선픽 여부';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.is_placement IS '모름';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list."rank" IS '등수';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.rating_id IS '등급';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.score IS '점수';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.win_lose IS '승리 여부';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.wizard_id IS '유저 id';
COMMENT ON COLUMN public.ranker_rtpvp_replay_user_list.wizard_name IS '유저 닉네임';


-- public.siege_recommended_attack_deck definition

-- Drop table

-- DROP TABLE public.siege_recommended_attack_deck;

CREATE TABLE public.siege_recommended_attack_deck (
	id serial4 NOT NULL,
	def_monster_1 varchar NOT NULL, -- 방어몬스터1
	def_monster_2 varchar NOT NULL, -- 방어몬스터2
	def_monster_3 varchar NOT NULL, -- 방어몬스터3
	atk_monster_1 varchar NOT NULL, -- 공격몬스터 1
	atk_monster_2 varchar NOT NULL, -- 공격몬스터 2
	atk_monster_3 varchar NOT NULL, -- 공격몬스터 3
	recommend_count int4 DEFAULT 0 NOT NULL, -- 추천 수
	not_recommend_count int4 DEFAULT 0 NOT NULL, -- 비추천 수
	crt_user_id varchar(50) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT siege_recommended_attack_deck_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE public.siege_recommended_attack_deck IS '점령전 추천 공덱';

-- Column comments

COMMENT ON COLUMN public.siege_recommended_attack_deck.def_monster_1 IS '방어몬스터1';
COMMENT ON COLUMN public.siege_recommended_attack_deck.def_monster_2 IS '방어몬스터2';
COMMENT ON COLUMN public.siege_recommended_attack_deck.def_monster_3 IS '방어몬스터3';
COMMENT ON COLUMN public.siege_recommended_attack_deck.atk_monster_1 IS '공격몬스터 1';
COMMENT ON COLUMN public.siege_recommended_attack_deck.atk_monster_2 IS '공격몬스터 2';
COMMENT ON COLUMN public.siege_recommended_attack_deck.atk_monster_3 IS '공격몬스터 3';
COMMENT ON COLUMN public.siege_recommended_attack_deck.recommend_count IS '추천 수';
COMMENT ON COLUMN public.siege_recommended_attack_deck.not_recommend_count IS '비추천 수';
COMMENT ON COLUMN public.siege_recommended_attack_deck.crt_user_id IS '등록자';
COMMENT ON COLUMN public.siege_recommended_attack_deck.crt_date IS '등록일';
COMMENT ON COLUMN public.siege_recommended_attack_deck.upt_user_id IS '수정자';
COMMENT ON COLUMN public.siege_recommended_attack_deck.upt_date IS '수정일';


-- public.skill_effect_master definition

-- Drop table

-- DROP TABLE public.skill_effect_master;

CREATE TABLE public.skill_effect_master (
	effect_id int4 NOT NULL, -- 이펙트 ID (Swarfarm API ID)
	"name" varchar(200) NOT NULL, -- 이펙트명
	is_buff bool DEFAULT false NULL, -- 버프 여부
	"type" varchar(50) NULL, -- 이펙트 타입 (Buff, Debuff, Neutral)
	description text NULL, -- 이펙트 설명
	icon_filename varchar(200) NULL, -- 아이콘 파일명
	icon_path varchar(500) NULL, -- 아이콘 저장 경로
	swarfarm_url varchar(500) NULL, -- Swarfarm API URL
	last_sync_date timestamp NULL, -- 마지막 동기화 일시
	crt_user_id varchar(50) DEFAULT 'SYSTEM'::character varying NULL,
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	upt_user_id varchar(50) DEFAULT 'SYSTEM'::character varying NULL,
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_skill_effect_master PRIMARY KEY (effect_id)
);
CREATE INDEX idx_skill_effect_master_last_sync_date ON public.skill_effect_master USING btree (last_sync_date);
CREATE INDEX idx_skill_effect_master_name ON public.skill_effect_master USING btree (name);
CREATE INDEX idx_skill_effect_master_type ON public.skill_effect_master USING btree (type);
COMMENT ON TABLE public.skill_effect_master IS '스킬 이펙트 마스터 정보';

-- Column comments

COMMENT ON COLUMN public.skill_effect_master.effect_id IS '이펙트 ID (Swarfarm API ID)';
COMMENT ON COLUMN public.skill_effect_master."name" IS '이펙트명';
COMMENT ON COLUMN public.skill_effect_master.is_buff IS '버프 여부';
COMMENT ON COLUMN public.skill_effect_master."type" IS '이펙트 타입 (Buff, Debuff, Neutral)';
COMMENT ON COLUMN public.skill_effect_master.description IS '이펙트 설명';
COMMENT ON COLUMN public.skill_effect_master.icon_filename IS '아이콘 파일명';
COMMENT ON COLUMN public.skill_effect_master.icon_path IS '아이콘 저장 경로';
COMMENT ON COLUMN public.skill_effect_master.swarfarm_url IS 'Swarfarm API URL';
COMMENT ON COLUMN public.skill_effect_master.last_sync_date IS '마지막 동기화 일시';


-- public.sys_api definition

-- Drop table

-- DROP TABLE public.sys_api;

CREATE TABLE public.sys_api (
	api_id varchar(20) NOT NULL, -- API ID
	bsns_cd varchar(20) NULL, -- 업무 코드
	dtl_bsns_cd varchar(20) NULL, -- 상세 업무 코드
	api_txt varchar(1000) NULL, -- API 설명
	api_url varchar(300) NULL, -- API URL
	crt_user_id varchar(50) NOT NULL, -- 생성자 ID
	crt_date varchar(255) DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 생성일시
	upt_user_id varchar(50) NOT NULL, -- 수정자 ID
	upt_date varchar(255) DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일시
	CONSTRAINT pk_sys_api PRIMARY KEY (api_id)
);
COMMENT ON TABLE public.sys_api IS 'API 정보 테이블';

-- Column comments

COMMENT ON COLUMN public.sys_api.api_id IS 'API ID';
COMMENT ON COLUMN public.sys_api.bsns_cd IS '업무 코드';
COMMENT ON COLUMN public.sys_api.dtl_bsns_cd IS '상세 업무 코드';
COMMENT ON COLUMN public.sys_api.api_txt IS 'API 설명';
COMMENT ON COLUMN public.sys_api.api_url IS 'API URL';
COMMENT ON COLUMN public.sys_api.crt_user_id IS '생성자 ID';
COMMENT ON COLUMN public.sys_api.crt_date IS '생성일시';
COMMENT ON COLUMN public.sys_api.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN public.sys_api.upt_date IS '수정일시';


-- public.sys_api_exe_log definition

-- Drop table

-- DROP TABLE public.sys_api_exe_log;

CREATE TABLE public.sys_api_exe_log (
	api_exe_log_sn int8 DEFAULT nextval('sys_api_exe_log_seq'::regclass) NOT NULL, -- API 실행 로그 일련번호
	exe_dtm varchar(23) NULL, -- 실행 일시
	user_id varchar(50) NULL, -- 사용자 ID
	api_exe_url varchar(300) NULL, -- API 실행 URL
	mthd_tp_cd varchar(20) NULL, -- 메소드 타입 코드
	ip_addr varchar(300) NULL, -- IP 주소
	wkpl_id varchar(50) NULL, -- 작업장 ID
	lang_cd varchar(20) NULL, -- 언어 코드
	tzon_cd varchar(20) NULL, -- 시간대 코드
	svr_ip_addr varchar(300) NULL, -- 서버 IP 주소
	inp_param_txt varchar(4000) NULL, -- 입력 파라미터 텍스트
	rslt_item_nm varchar(300) NULL, -- 결과 항목명
	rslt_txt varchar(3000) NULL, -- 결과 텍스트
	api_id varchar(20) NULL, -- API ID
	CONSTRAINT pk_sys_api_exe_log PRIMARY KEY (api_exe_log_sn)
);
CREATE INDEX idx_sys_api_exe_log_api_exe_url ON public.sys_api_exe_log USING btree (api_exe_url);
CREATE INDEX idx_sys_api_exe_log_api_id ON public.sys_api_exe_log USING btree (api_id);
CREATE INDEX idx_sys_api_exe_log_exe_dtm ON public.sys_api_exe_log USING btree (exe_dtm);
CREATE INDEX idx_sys_api_exe_log_user_id ON public.sys_api_exe_log USING btree (user_id);
COMMENT ON TABLE public.sys_api_exe_log IS 'API 실행 로그';

-- Column comments

COMMENT ON COLUMN public.sys_api_exe_log.api_exe_log_sn IS 'API 실행 로그 일련번호';
COMMENT ON COLUMN public.sys_api_exe_log.exe_dtm IS '실행 일시';
COMMENT ON COLUMN public.sys_api_exe_log.user_id IS '사용자 ID';
COMMENT ON COLUMN public.sys_api_exe_log.api_exe_url IS 'API 실행 URL';
COMMENT ON COLUMN public.sys_api_exe_log.mthd_tp_cd IS '메소드 타입 코드';
COMMENT ON COLUMN public.sys_api_exe_log.ip_addr IS 'IP 주소';
COMMENT ON COLUMN public.sys_api_exe_log.wkpl_id IS '작업장 ID';
COMMENT ON COLUMN public.sys_api_exe_log.lang_cd IS '언어 코드';
COMMENT ON COLUMN public.sys_api_exe_log.tzon_cd IS '시간대 코드';
COMMENT ON COLUMN public.sys_api_exe_log.svr_ip_addr IS '서버 IP 주소';
COMMENT ON COLUMN public.sys_api_exe_log.inp_param_txt IS '입력 파라미터 텍스트';
COMMENT ON COLUMN public.sys_api_exe_log.rslt_item_nm IS '결과 항목명';
COMMENT ON COLUMN public.sys_api_exe_log.rslt_txt IS '결과 텍스트';
COMMENT ON COLUMN public.sys_api_exe_log.api_id IS 'API ID';


-- public.sys_api_role definition

-- Drop table

-- DROP TABLE public.sys_api_role;

CREATE TABLE public.sys_api_role (
	api_id varchar(20) NOT NULL, -- API ID
	role_id varchar(20) NOT NULL, -- 역할 ID
	usg_yn varchar(1) NULL, -- 사용 여부
	crt_user_id varchar(50) NULL, -- 생성자 ID
	crt_date varchar(255) NULL, -- 생성일시
	upt_user_id varchar(50) NULL, -- 수정자 ID
	upt_date varchar(255) NULL, -- 수정일시
	CONSTRAINT pk_sys_api_role PRIMARY KEY (api_id, role_id)
);
COMMENT ON TABLE public.sys_api_role IS 'API-역할 매핑 테이블';

-- Column comments

COMMENT ON COLUMN public.sys_api_role.api_id IS 'API ID';
COMMENT ON COLUMN public.sys_api_role.role_id IS '역할 ID';
COMMENT ON COLUMN public.sys_api_role.usg_yn IS '사용 여부';
COMMENT ON COLUMN public.sys_api_role.crt_user_id IS '생성자 ID';
COMMENT ON COLUMN public.sys_api_role.crt_date IS '생성일시';
COMMENT ON COLUMN public.sys_api_role.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN public.sys_api_role.upt_date IS '수정일시';


-- public.sys_bat_exe_log definition

-- Drop table

-- DROP TABLE public.sys_bat_exe_log;

CREATE TABLE public.sys_bat_exe_log (
	bat_exe_log_sn int8 DEFAULT nextval('sys_bat_exe_log_seq'::regclass) NOT NULL,
	bat_id varchar(50) NULL,
	exe_dtm varchar(23) NULL,
	rslt_cd varchar(20) NULL,
	rslt_txt varchar(3000) NULL,
	CONSTRAINT sys_bat_exe_log_pkey PRIMARY KEY (bat_exe_log_sn)
);


-- public.sys_batch_config definition

-- Drop table

-- DROP TABLE public.sys_batch_config;

CREATE TABLE public.sys_batch_config (
	bat_id int8 DEFAULT nextval('sys_batch_config_seq'::regclass) NOT NULL, -- 배치 ID (시퀀스, PK)
	bat_nm varchar(100) NOT NULL, -- 배치명(한글)
	job_class varchar(255) NOT NULL, -- Quartz Job FQCN
	cron_expr varchar(255) NOT NULL, -- 크론 표현식
	use_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용 여부(Y/N)
	sort_sn int4 DEFAULT 0 NOT NULL, -- 정렬 순번
	desc_txt varchar(500) NULL, -- 설명
	crt_user_id varchar(32) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일시
	upt_user_id varchar(32) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일시
	CONSTRAINT sys_batch_config_pkey PRIMARY KEY (bat_id)
);
COMMENT ON TABLE public.sys_batch_config IS '배치 스케줄 설정';

-- Column comments

COMMENT ON COLUMN public.sys_batch_config.bat_id IS '배치 ID (시퀀스, PK)';
COMMENT ON COLUMN public.sys_batch_config.bat_nm IS '배치명(한글)';
COMMENT ON COLUMN public.sys_batch_config.job_class IS 'Quartz Job FQCN';
COMMENT ON COLUMN public.sys_batch_config.cron_expr IS '크론 표현식';
COMMENT ON COLUMN public.sys_batch_config.use_yn IS '사용 여부(Y/N)';
COMMENT ON COLUMN public.sys_batch_config.sort_sn IS '정렬 순번';
COMMENT ON COLUMN public.sys_batch_config.desc_txt IS '설명';
COMMENT ON COLUMN public.sys_batch_config.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_batch_config.crt_date IS '등록일시';
COMMENT ON COLUMN public.sys_batch_config.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_batch_config.upt_date IS '수정일시';


-- public.sys_cd definition

-- Drop table

-- DROP TABLE public.sys_cd;

CREATE TABLE public.sys_cd (
	cd_grp_no varchar(20) NOT NULL, -- 코드 그룹 번호
	cd varchar(20) NOT NULL, -- 코드
	sort_sn int8 NULL, -- 정렬순번
	memo_1 varchar(1000) NULL, -- 메모 1
	memo_2 varchar(1000) NULL, -- 메모 2
	memo_3 varchar(1000) NULL, -- 메모 3
	memo_4 varchar(1000) NULL, -- 메모 4
	memo_5 varchar(1000) NULL, -- 메모 5
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	crt_user_id varchar(12) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(12) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL -- 수정일
);
COMMENT ON TABLE public.sys_cd IS '코드';

-- Column comments

COMMENT ON COLUMN public.sys_cd.cd_grp_no IS '코드 그룹 번호';
COMMENT ON COLUMN public.sys_cd.cd IS '코드';
COMMENT ON COLUMN public.sys_cd.sort_sn IS '정렬순번';
COMMENT ON COLUMN public.sys_cd.memo_1 IS '메모 1';
COMMENT ON COLUMN public.sys_cd.memo_2 IS '메모 2';
COMMENT ON COLUMN public.sys_cd.memo_3 IS '메모 3';
COMMENT ON COLUMN public.sys_cd.memo_4 IS '메모 4';
COMMENT ON COLUMN public.sys_cd.memo_5 IS '메모 5';
COMMENT ON COLUMN public.sys_cd.usg_yn IS '사용여부';
COMMENT ON COLUMN public.sys_cd.del_yn IS '삭제여부';
COMMENT ON COLUMN public.sys_cd.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_cd.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_cd.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_cd.upt_date IS '수정일';


-- public.sys_cd_grp definition

-- Drop table

-- DROP TABLE public.sys_cd_grp;

CREATE TABLE public.sys_cd_grp (
	cd_grp_no varchar(20) NOT NULL, -- 코드 그룹 번호 (PK)
	cd_grp_nm varchar(100) NOT NULL, -- 코드 그룹명
	bsns_cd varchar(20) NULL, -- 업무 코드
	dtl_bsns_cd varchar(20) NULL, -- 세부 업무 코드
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용 여부 (Y/N)
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제 여부 (Y/N)
	crt_user_id varchar(50) NOT NULL, -- 생성자 ID
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 생성일시
	upt_user_id varchar(50) NOT NULL, -- 수정자 ID
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일시
	CONSTRAINT chk_sys_cd_grp_del_yn CHECK (((del_yn)::bpchar = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT chk_sys_cd_grp_usg_yn CHECK (((usg_yn)::bpchar = ANY (ARRAY['Y'::bpchar, 'N'::bpchar]))),
	CONSTRAINT sys_cd_grp_pkey PRIMARY KEY (cd_grp_no)
);
CREATE INDEX idx_sys_cd_grp_bsns_cd ON public.sys_cd_grp USING btree (bsns_cd);
CREATE INDEX idx_sys_cd_grp_bsns_usg_del ON public.sys_cd_grp USING btree (bsns_cd, usg_yn, del_yn);
CREATE INDEX idx_sys_cd_grp_del_yn ON public.sys_cd_grp USING btree (del_yn);
CREATE INDEX idx_sys_cd_grp_dtl_bsns_cd ON public.sys_cd_grp USING btree (dtl_bsns_cd);
CREATE INDEX idx_sys_cd_grp_dtl_bsns_usg_del ON public.sys_cd_grp USING btree (dtl_bsns_cd, usg_yn, del_yn);
CREATE INDEX idx_sys_cd_grp_usg_yn ON public.sys_cd_grp USING btree (usg_yn);
COMMENT ON TABLE public.sys_cd_grp IS '시스템 코드 그룹 관리 테이블';

-- Column comments

COMMENT ON COLUMN public.sys_cd_grp.cd_grp_no IS '코드 그룹 번호 (PK)';
COMMENT ON COLUMN public.sys_cd_grp.cd_grp_nm IS '코드 그룹명';
COMMENT ON COLUMN public.sys_cd_grp.bsns_cd IS '업무 코드';
COMMENT ON COLUMN public.sys_cd_grp.dtl_bsns_cd IS '세부 업무 코드';
COMMENT ON COLUMN public.sys_cd_grp.usg_yn IS '사용 여부 (Y/N)';
COMMENT ON COLUMN public.sys_cd_grp.del_yn IS '삭제 여부 (Y/N)';
COMMENT ON COLUMN public.sys_cd_grp.crt_user_id IS '생성자 ID';
COMMENT ON COLUMN public.sys_cd_grp.crt_date IS '생성일시';
COMMENT ON COLUMN public.sys_cd_grp.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN public.sys_cd_grp.upt_date IS '수정일시';

-- Table Triggers

create trigger trg_sys_cd_grp_upt_date before
update
    on
    public.sys_cd_grp for each row execute procedure update_upt_date();


-- public.sys_cd_rel definition

-- Drop table

-- DROP TABLE public.sys_cd_rel;

CREATE TABLE public.sys_cd_rel (
	cd_grp_no varchar(20) NOT NULL, -- 코드 그룹 번호
	cd varchar(20) NOT NULL, -- 코드
	up_cd_grp_no varchar(20) NOT NULL, -- 부모 코드 그룹 번호
	up_cd varchar(20) NOT NULL, -- 부모 코드
	usg_yn varchar(1) NOT NULL, -- 사용여부
	del_yn varchar(1) NOT NULL, -- 삭제여부
	crt_user_id varchar(12) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(12) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL -- 수정일
);
COMMENT ON TABLE public.sys_cd_rel IS '코드 관계 관리';

-- Column comments

COMMENT ON COLUMN public.sys_cd_rel.cd_grp_no IS '코드 그룹 번호';
COMMENT ON COLUMN public.sys_cd_rel.cd IS '코드';
COMMENT ON COLUMN public.sys_cd_rel.up_cd_grp_no IS '부모 코드 그룹 번호';
COMMENT ON COLUMN public.sys_cd_rel.up_cd IS '부모 코드';
COMMENT ON COLUMN public.sys_cd_rel.usg_yn IS '사용여부';
COMMENT ON COLUMN public.sys_cd_rel.del_yn IS '삭제여부';
COMMENT ON COLUMN public.sys_cd_rel.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_cd_rel.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_cd_rel.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_cd_rel.upt_date IS '수정일';


-- public.sys_file_attachment definition

-- Drop table

-- DROP TABLE public.sys_file_attachment;

CREATE TABLE public.sys_file_attachment (
	file_id int8 NOT NULL, -- 첨부파일 그룹 번호
	file_seq int4 NOT NULL, -- 첨부파일 순번
	file_path varchar(500) NOT NULL, -- 파일 경로
	file_name varchar(255) NOT NULL, -- 파일명
	file_type varchar(50) NULL, -- 파일 타입 (JSON, IMAGE, PDF 등)
	file_size int8 NULL, -- 파일 크기 (bytes)
	reference_type varchar(50) NULL, -- 참조 타입 (GUILD_APPLICATION 등)
	reference_id varchar(50) NULL, -- 참조 ID
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	crt_user_id varchar(50) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT pk_sys_file_attachment PRIMARY KEY (file_id, file_seq)
);
CREATE INDEX idx_file_attachment_file_id ON public.sys_file_attachment USING btree (file_id);
CREATE INDEX idx_file_attachment_reference ON public.sys_file_attachment USING btree (reference_type, reference_id);
CREATE INDEX idx_file_attachment_usg_del ON public.sys_file_attachment USING btree (usg_yn, del_yn);
COMMENT ON TABLE public.sys_file_attachment IS '첨부파일';

-- Column comments

COMMENT ON COLUMN public.sys_file_attachment.file_id IS '첨부파일 그룹 번호';
COMMENT ON COLUMN public.sys_file_attachment.file_seq IS '첨부파일 순번';
COMMENT ON COLUMN public.sys_file_attachment.file_path IS '파일 경로';
COMMENT ON COLUMN public.sys_file_attachment.file_name IS '파일명';
COMMENT ON COLUMN public.sys_file_attachment.file_type IS '파일 타입 (JSON, IMAGE, PDF 등)';
COMMENT ON COLUMN public.sys_file_attachment.file_size IS '파일 크기 (bytes)';
COMMENT ON COLUMN public.sys_file_attachment.reference_type IS '참조 타입 (GUILD_APPLICATION 등)';
COMMENT ON COLUMN public.sys_file_attachment.reference_id IS '참조 ID';
COMMENT ON COLUMN public.sys_file_attachment.usg_yn IS '사용여부';
COMMENT ON COLUMN public.sys_file_attachment.del_yn IS '삭제여부';
COMMENT ON COLUMN public.sys_file_attachment.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_file_attachment.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_file_attachment.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_file_attachment.upt_date IS '수정일';


-- public.sys_menu definition

-- Drop table

-- DROP TABLE public.sys_menu;

CREATE TABLE public.sys_menu (
	menu_id varchar(20) NOT NULL, -- 메뉴 id
	pr_menu_id varchar(20) NULL, -- 부모 메뉴 id
	menu_url varchar(100) NULL, -- 메뉴 url
	menu_nm varchar(100) NULL, -- 메뉴명
	page_id varchar(50) NULL, -- 화면 ID
	sort_sn int8 NULL, -- 정렬 순번
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	crt_user_id varchar(12) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(12) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT sys_menu_unique UNIQUE (menu_id)
);
COMMENT ON TABLE public.sys_menu IS '메뉴 관리';

-- Column comments

COMMENT ON COLUMN public.sys_menu.menu_id IS '메뉴 id';
COMMENT ON COLUMN public.sys_menu.pr_menu_id IS '부모 메뉴 id';
COMMENT ON COLUMN public.sys_menu.menu_url IS '메뉴 url';
COMMENT ON COLUMN public.sys_menu.menu_nm IS '메뉴명';
COMMENT ON COLUMN public.sys_menu.page_id IS '화면 ID';
COMMENT ON COLUMN public.sys_menu.sort_sn IS '정렬 순번';
COMMENT ON COLUMN public.sys_menu.usg_yn IS '사용여부';
COMMENT ON COLUMN public.sys_menu.del_yn IS '삭제여부';
COMMENT ON COLUMN public.sys_menu.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_menu.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_menu.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_menu.upt_date IS '수정일';


-- public.sys_menu_role definition

-- Drop table

-- DROP TABLE public.sys_menu_role;

CREATE TABLE public.sys_menu_role (
	menu_id varchar(20) NOT NULL, -- 메뉴 id
	role_id varchar(20) NOT NULL, -- 권한 id
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	crt_user_id varchar(12) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(12) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT sys_menu_role_unique UNIQUE (menu_id, role_id)
);
COMMENT ON TABLE public.sys_menu_role IS '메뉴 권한';

-- Column comments

COMMENT ON COLUMN public.sys_menu_role.menu_id IS '메뉴 id';
COMMENT ON COLUMN public.sys_menu_role.role_id IS '권한 id';
COMMENT ON COLUMN public.sys_menu_role.usg_yn IS '사용여부';
COMMENT ON COLUMN public.sys_menu_role.del_yn IS '삭제여부';
COMMENT ON COLUMN public.sys_menu_role.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_menu_role.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_menu_role.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_menu_role.upt_date IS '수정일';


-- public.sys_menu_scrn definition

-- Drop table

-- DROP TABLE public.sys_menu_scrn;

CREATE TABLE public.sys_menu_scrn (
	scrn_id varchar(50) NOT NULL, -- 화면 ID
	menu_id varchar(50) NOT NULL, -- 메뉴 ID
	scrn_cl_cd varchar(50) NULL, -- 화면 분류 코드
	page_id varchar(50) NULL, -- 페이지 ID
	scrn_txt varchar(500) NULL, -- 화면 텍스트
	crt_usr_id varchar(50) NOT NULL, -- 생성 사용자 ID
	crt_dtm varchar(14) DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 생성 일시 (YYYYMMDDHH24MISS)
	upt_usr_id varchar(50) NOT NULL, -- 수정 사용자 ID
	upt_dtm varchar(14) DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정 일시 (YYYYMMDDHH24MISS)
	CONSTRAINT pk_sys_menu_scrn PRIMARY KEY (scrn_id)
);
CREATE INDEX idx_sys_menu_scrn_01 ON public.sys_menu_scrn USING btree (menu_id);
CREATE INDEX idx_sys_menu_scrn_02 ON public.sys_menu_scrn USING btree (page_id);
COMMENT ON TABLE public.sys_menu_scrn IS '메뉴 종속 화면';

-- Column comments

COMMENT ON COLUMN public.sys_menu_scrn.scrn_id IS '화면 ID';
COMMENT ON COLUMN public.sys_menu_scrn.menu_id IS '메뉴 ID';
COMMENT ON COLUMN public.sys_menu_scrn.scrn_cl_cd IS '화면 분류 코드';
COMMENT ON COLUMN public.sys_menu_scrn.page_id IS '페이지 ID';
COMMENT ON COLUMN public.sys_menu_scrn.scrn_txt IS '화면 텍스트';
COMMENT ON COLUMN public.sys_menu_scrn.crt_usr_id IS '생성 사용자 ID';
COMMENT ON COLUMN public.sys_menu_scrn.crt_dtm IS '생성 일시 (YYYYMMDDHH24MISS)';
COMMENT ON COLUMN public.sys_menu_scrn.upt_usr_id IS '수정 사용자 ID';
COMMENT ON COLUMN public.sys_menu_scrn.upt_dtm IS '수정 일시 (YYYYMMDDHH24MISS)';


-- public.sys_mlang definition

-- Drop table

-- DROP TABLE public.sys_mlang;

CREATE TABLE public.sys_mlang (
	mlang_tp_cd varchar(20) NOT NULL, -- 다국어 구분 코드
	mlang_cd varchar(100) NOT NULL, -- 다국어 코드
	lang_cd varchar(20) NOT NULL, -- 언어 코드
	mlang_txt varchar(1000) NULL, -- 다국어 내용
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용 여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제 여부
	crt_user_id varchar(12) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(12) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL -- 수정일
);

-- Column comments

COMMENT ON COLUMN public.sys_mlang.mlang_tp_cd IS '다국어 구분 코드';
COMMENT ON COLUMN public.sys_mlang.mlang_cd IS '다국어 코드';
COMMENT ON COLUMN public.sys_mlang.lang_cd IS '언어 코드';
COMMENT ON COLUMN public.sys_mlang.mlang_txt IS '다국어 내용';
COMMENT ON COLUMN public.sys_mlang.usg_yn IS '사용 여부';
COMMENT ON COLUMN public.sys_mlang.del_yn IS '삭제 여부';
COMMENT ON COLUMN public.sys_mlang.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_mlang.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_mlang.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_mlang.upt_date IS '수정일';


-- public.sys_page definition

-- Drop table

-- DROP TABLE public.sys_page;

CREATE TABLE public.sys_page (
	page_id varchar(50) NOT NULL, -- 페이지 번호 (PK)
	page_nm varchar(100) NOT NULL, -- 페이지명
	page_url varchar(200) NULL, -- 페이지 URL
	page_desc varchar(500) NULL, -- 페이지 설명
	srt_sn int4 NULL, -- 정렬순서
	usg_yn bpchar(1) DEFAULT 'Y'::bpchar NOT NULL, -- 사용여부 (Y/N)
	del_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL, -- 삭제여부 (Y/N)
	crt_user_id varchar(50) NOT NULL, -- 생성자 ID
	crt_date timestamp DEFAULT now() NULL, -- 생성일시
	upt_user_id varchar(50) NULL, -- 수정자 ID
	upt_date timestamp DEFAULT now() NULL, -- 수정일시
	CONSTRAINT sys_page_pkey PRIMARY KEY (page_id)
);
CREATE INDEX idx_sys_page_del_yn ON public.sys_page USING btree (del_yn);
CREATE INDEX idx_sys_page_srt_sn ON public.sys_page USING btree (srt_sn);
CREATE INDEX idx_sys_page_usg_yn ON public.sys_page USING btree (usg_yn);
COMMENT ON TABLE public.sys_page IS '시스템 페이지 관리 테이블';

-- Column comments

COMMENT ON COLUMN public.sys_page.page_id IS '페이지 번호 (PK)';
COMMENT ON COLUMN public.sys_page.page_nm IS '페이지명';
COMMENT ON COLUMN public.sys_page.page_url IS '페이지 URL';
COMMENT ON COLUMN public.sys_page.page_desc IS '페이지 설명';
COMMENT ON COLUMN public.sys_page.srt_sn IS '정렬순서';
COMMENT ON COLUMN public.sys_page.usg_yn IS '사용여부 (Y/N)';
COMMENT ON COLUMN public.sys_page.del_yn IS '삭제여부 (Y/N)';
COMMENT ON COLUMN public.sys_page.crt_user_id IS '생성자 ID';
COMMENT ON COLUMN public.sys_page.crt_date IS '생성일시';
COMMENT ON COLUMN public.sys_page.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN public.sys_page.upt_date IS '수정일시';


-- public.sys_role definition

-- Drop table

-- DROP TABLE public.sys_role;

CREATE TABLE public.sys_role (
	role_id varchar(20) NOT NULL, -- 권한 id
	sort_sn int8 NULL, -- 정렬 순번
	role_nm varchar(100) NULL, -- 권한명
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	crt_user_id varchar(12) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(12) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL -- 수정일
);
COMMENT ON TABLE public.sys_role IS '권한';

-- Column comments

COMMENT ON COLUMN public.sys_role.role_id IS '권한 id';
COMMENT ON COLUMN public.sys_role.sort_sn IS '정렬 순번';
COMMENT ON COLUMN public.sys_role.role_nm IS '권한명';
COMMENT ON COLUMN public.sys_role.usg_yn IS '사용여부';
COMMENT ON COLUMN public.sys_role.del_yn IS '삭제여부';
COMMENT ON COLUMN public.sys_role.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_role.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_role.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_role.upt_date IS '수정일';


-- public.sys_user definition

-- Drop table

-- DROP TABLE public.sys_user;

CREATE TABLE public.sys_user (
	user_id varchar(20) NOT NULL, -- 사용자 id
	user_nm varchar(100) NULL, -- 사용자명
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	user_pw varchar(100) NOT NULL, -- 비밀번호
	fcm_token varchar(100) NULL, -- fcm 토큰
	crt_user_id varchar(12) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(12) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	lang_cd varchar(100) NULL, -- 언어 코드
	dvc_id varchar(1000) NULL, -- 디바이스 Id
	siege_view_scope bpchar(1) DEFAULT 'C'::bpchar NOT NULL, -- 전적 검색 기준
	current_guild_id int8 NULL, -- 현재 소속 길드 ID
	email varchar(255) NULL, -- 이메일 주소
	CONSTRAINT sys_user_pk PRIMARY KEY (user_id)
);
CREATE INDEX idx_sys_user_crt_date ON public.sys_user USING btree (crt_date DESC) WHERE ((del_yn)::text = 'N'::text);
CREATE UNIQUE INDEX uk_user_email ON public.sys_user USING btree (email) WHERE ((email IS NOT NULL) AND ((del_yn)::text = 'N'::text));
COMMENT ON TABLE public.sys_user IS '사용자';

-- Column comments

COMMENT ON COLUMN public.sys_user.user_id IS '사용자 id';
COMMENT ON COLUMN public.sys_user.user_nm IS '사용자명';
COMMENT ON COLUMN public.sys_user.usg_yn IS '사용여부';
COMMENT ON COLUMN public.sys_user.del_yn IS '삭제여부';
COMMENT ON COLUMN public.sys_user.user_pw IS '비밀번호';
COMMENT ON COLUMN public.sys_user.fcm_token IS 'fcm 토큰';
COMMENT ON COLUMN public.sys_user.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_user.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_user.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_user.upt_date IS '수정일';
COMMENT ON COLUMN public.sys_user.lang_cd IS '언어 코드';
COMMENT ON COLUMN public.sys_user.dvc_id IS '디바이스 Id';
COMMENT ON COLUMN public.sys_user.siege_view_scope IS '전적 검색 기준';
COMMENT ON COLUMN public.sys_user.current_guild_id IS '현재 소속 길드 ID';
COMMENT ON COLUMN public.sys_user.email IS '이메일 주소';


-- public.sys_user_login_log definition

-- Drop table

-- DROP TABLE public.sys_user_login_log;

CREATE TABLE public.sys_user_login_log (
	user_log_sn int8 DEFAULT nextval('sys_user_login_log_seq'::regclass) NOT NULL, -- 로그인 로그 순번
	user_id varchar(50) NOT NULL, -- 사용자 ID
	login_date varchar(23) NULL, -- 로그인 일시
	ip_addr varchar(300) NULL, -- IP 주소
	lang_cd varchar(20) NULL, -- 언어 코드
	CONSTRAINT sys_user_login_log_pkey PRIMARY KEY (user_log_sn)
);
CREATE INDEX idx_sys_user_login_log_login_date ON public.sys_user_login_log USING btree (login_date);
CREATE INDEX idx_sys_user_login_log_user_id ON public.sys_user_login_log USING btree (user_id);
COMMENT ON TABLE public.sys_user_login_log IS '사용자 로그인 로그';

-- Column comments

COMMENT ON COLUMN public.sys_user_login_log.user_log_sn IS '로그인 로그 순번';
COMMENT ON COLUMN public.sys_user_login_log.user_id IS '사용자 ID';
COMMENT ON COLUMN public.sys_user_login_log.login_date IS '로그인 일시';
COMMENT ON COLUMN public.sys_user_login_log.ip_addr IS 'IP 주소';
COMMENT ON COLUMN public.sys_user_login_log.lang_cd IS '언어 코드';


-- public.sys_user_role definition

-- Drop table

-- DROP TABLE public.sys_user_role;

CREATE TABLE public.sys_user_role (
	user_id varchar(12) NOT NULL, -- 사용자 id
	role_id varchar(20) NOT NULL, -- 권한 id
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	crt_user_id varchar(12) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(12) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL -- 수정일
);
COMMENT ON TABLE public.sys_user_role IS '사용자 권한';

-- Column comments

COMMENT ON COLUMN public.sys_user_role.user_id IS '사용자 id';
COMMENT ON COLUMN public.sys_user_role.role_id IS '권한 id';
COMMENT ON COLUMN public.sys_user_role.usg_yn IS '사용여부';
COMMENT ON COLUMN public.sys_user_role.del_yn IS '삭제여부';
COMMENT ON COLUMN public.sys_user_role.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_user_role.crt_date IS '등록일';
COMMENT ON COLUMN public.sys_user_role.upt_user_id IS '수정자';
COMMENT ON COLUMN public.sys_user_role.upt_date IS '수정일';


-- public.view_battle_deck_info definition

-- Drop table

-- DROP TABLE public.view_battle_deck_info;

CREATE TABLE public.view_battle_deck_info (
	match_id varchar NOT NULL, -- 매치 id
	log_timestamp varchar NOT NULL, -- 배틀 id
	"type" varchar NOT NULL, -- 공격/방어 타입(attack or defense)
	monster_id_1 varchar NOT NULL, -- 몬스터 id 1
	monster_id_2 varchar NOT NULL, -- 몬스터 id 2
	monster_id_3 varchar NOT NULL, -- 몬스터 id 3
	log_id varchar NULL
);
CREATE INDEX idx_view_battle_deck_info ON public.view_battle_deck_info USING btree (match_id, log_timestamp, type);

-- Column comments

COMMENT ON COLUMN public.view_battle_deck_info.match_id IS '매치 id';
COMMENT ON COLUMN public.view_battle_deck_info.log_timestamp IS '배틀 id';
COMMENT ON COLUMN public.view_battle_deck_info."type" IS '공격/방어 타입(attack or defense)';
COMMENT ON COLUMN public.view_battle_deck_info.monster_id_1 IS '몬스터 id 1';
COMMENT ON COLUMN public.view_battle_deck_info.monster_id_2 IS '몬스터 id 2';
COMMENT ON COLUMN public.view_battle_deck_info.monster_id_3 IS '몬스터 id 3';


-- public.dungeon_levels definition

-- Drop table

-- DROP TABLE public.dungeon_levels;

CREATE TABLE public.dungeon_levels (
	dungeon_id int4 NOT NULL, -- 던전 ID
	level_id int4 NOT NULL, -- 레벨 ID
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_dungeon_levels PRIMARY KEY (dungeon_id, level_id),
	CONSTRAINT fk_dungeon_levels_dungeon FOREIGN KEY (dungeon_id) REFERENCES public.dungeon_master(dungeon_id) ON DELETE CASCADE
);
CREATE INDEX idx_dungeon_levels_dungeon_id ON public.dungeon_levels USING btree (dungeon_id);
CREATE INDEX idx_dungeon_levels_level_id ON public.dungeon_levels USING btree (level_id);
COMMENT ON TABLE public.dungeon_levels IS '던전 레벨 매핑';

-- Column comments

COMMENT ON COLUMN public.dungeon_levels.dungeon_id IS '던전 ID';
COMMENT ON COLUMN public.dungeon_levels.level_id IS '레벨 ID';


-- public.guild_application definition

-- Drop table

-- DROP TABLE public.guild_application;

CREATE TABLE public.guild_application (
	application_id int8 DEFAULT nextval('guild_application_seq'::regclass) NOT NULL, -- 신청 ID
	guild_name varchar(100) NOT NULL, -- 신청한 길드명
	status varchar(20) DEFAULT 'PENDING'::character varying NOT NULL, -- 상태 (PENDING: 대기, APPROVED: 승인, REJECTED: 거절, CANCELLED: 취소)
	message varchar(500) NULL, -- 신청 메시지
	file_id int8 NULL, -- 첨부파일 그룹 번호
	process_date timestamp NULL, -- 처리일
	process_user_id varchar(50) NULL, -- 처리자 ID
	reject_reason varchar(500) NULL, -- 거절 사유
	crt_user_id varchar(50) NOT NULL, -- 신청자 ID (등록자)
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT pk_guild_application PRIMARY KEY (application_id),
	CONSTRAINT fk_guild_application_user FOREIGN KEY (crt_user_id) REFERENCES public.sys_user(user_id) ON DELETE CASCADE
);
CREATE INDEX idx_guild_application_crt_date ON public.guild_application USING btree (crt_date);
CREATE INDEX idx_guild_application_guild_name ON public.guild_application USING btree (guild_name);
CREATE INDEX idx_guild_application_status ON public.guild_application USING btree (status);
CREATE INDEX idx_guild_application_status_date ON public.guild_application USING btree (status, crt_date DESC);
CREATE INDEX idx_guild_application_user_id ON public.guild_application USING btree (crt_user_id);
COMMENT ON TABLE public.guild_application IS '길드 신청';

-- Column comments

COMMENT ON COLUMN public.guild_application.application_id IS '신청 ID';
COMMENT ON COLUMN public.guild_application.guild_name IS '신청한 길드명';
COMMENT ON COLUMN public.guild_application.status IS '상태 (PENDING: 대기, APPROVED: 승인, REJECTED: 거절, CANCELLED: 취소)';
COMMENT ON COLUMN public.guild_application.message IS '신청 메시지';
COMMENT ON COLUMN public.guild_application.file_id IS '첨부파일 그룹 번호';
COMMENT ON COLUMN public.guild_application.process_date IS '처리일';
COMMENT ON COLUMN public.guild_application.process_user_id IS '처리자 ID';
COMMENT ON COLUMN public.guild_application.reject_reason IS '거절 사유';
COMMENT ON COLUMN public.guild_application.crt_user_id IS '신청자 ID (등록자)';
COMMENT ON COLUMN public.guild_application.crt_date IS '등록일';
COMMENT ON COLUMN public.guild_application.upt_user_id IS '수정자';
COMMENT ON COLUMN public.guild_application.upt_date IS '수정일';


-- public.inquiry definition

-- Drop table

-- DROP TABLE public.inquiry;

CREATE TABLE public.inquiry (
	inquiry_id int8 DEFAULT nextval('inquiry_seq'::regclass) NOT NULL, -- 문의 ID
	user_id varchar(20) NOT NULL, -- 문의자 ID
	title varchar(500) NOT NULL, -- 제목
	"content" text NOT NULL, -- 내용
	answer text NULL, -- 답변 내용
	status varchar(20) DEFAULT 'PENDING'::character varying NOT NULL, -- 상태 (PENDING: 대기, ANSWERED: 답변완료)
	answer_user_id varchar(20) NULL, -- 답변자 ID
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	answer_date timestamp NULL, -- 답변일
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제 여부
	CONSTRAINT chk_inquiry_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ANSWERED'::character varying])::text[]))),
	CONSTRAINT inquiry_pkey PRIMARY KEY (inquiry_id),
	CONSTRAINT fk_inquiry_answer_user FOREIGN KEY (answer_user_id) REFERENCES public.sys_user(user_id),
	CONSTRAINT fk_inquiry_user FOREIGN KEY (user_id) REFERENCES public.sys_user(user_id)
);
CREATE INDEX idx_inquiry_answer_user_id ON public.inquiry USING btree (answer_user_id);
CREATE INDEX idx_inquiry_crt_date ON public.inquiry USING btree (crt_date DESC);
CREATE INDEX idx_inquiry_del_yn ON public.inquiry USING btree (del_yn);
CREATE INDEX idx_inquiry_list ON public.inquiry USING btree (del_yn, status, crt_date DESC);
CREATE INDEX idx_inquiry_status ON public.inquiry USING btree (status);
CREATE INDEX idx_inquiry_user_id ON public.inquiry USING btree (user_id);
CREATE INDEX idx_inquiry_user_list ON public.inquiry USING btree (user_id, del_yn, status, crt_date DESC);
COMMENT ON TABLE public.inquiry IS '1대1문의';

-- Column comments

COMMENT ON COLUMN public.inquiry.inquiry_id IS '문의 ID';
COMMENT ON COLUMN public.inquiry.user_id IS '문의자 ID';
COMMENT ON COLUMN public.inquiry.title IS '제목';
COMMENT ON COLUMN public.inquiry."content" IS '내용';
COMMENT ON COLUMN public.inquiry.answer IS '답변 내용';
COMMENT ON COLUMN public.inquiry.status IS '상태 (PENDING: 대기, ANSWERED: 답변완료)';
COMMENT ON COLUMN public.inquiry.answer_user_id IS '답변자 ID';
COMMENT ON COLUMN public.inquiry.crt_date IS '등록일';
COMMENT ON COLUMN public.inquiry.answer_date IS '답변일';
COMMENT ON COLUMN public.inquiry.upt_date IS '수정일';
COMMENT ON COLUMN public.inquiry.del_yn IS '삭제 여부';


-- public.level_waves definition

-- Drop table

-- DROP TABLE public.level_waves;

CREATE TABLE public.level_waves (
	level_id int4 NOT NULL, -- 레벨 ID
	wave_number int4 NOT NULL, -- 웨이브 번호
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_level_waves PRIMARY KEY (level_id, wave_number),
	CONSTRAINT fk_level_waves_level FOREIGN KEY (level_id) REFERENCES public.level_master(level_id) ON DELETE CASCADE
);
CREATE INDEX idx_level_waves_level_id ON public.level_waves USING btree (level_id);
COMMENT ON TABLE public.level_waves IS '레벨 웨이브 정보';

-- Column comments

COMMENT ON COLUMN public.level_waves.level_id IS '레벨 ID';
COMMENT ON COLUMN public.level_waves.wave_number IS '웨이브 번호';


-- public.monster_skills definition

-- Drop table

-- DROP TABLE public.monster_skills;

CREATE TABLE public.monster_skills (
	monster_id varchar(100) NOT NULL, -- 몬스터 ID
	skill_id int4 NOT NULL, -- 스킬 ID
	skill_order int4 NOT NULL, -- 스킬 순서
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_monster_skills PRIMARY KEY (monster_id, skill_id, skill_order),
	CONSTRAINT fk_monster_skills_monster FOREIGN KEY (monster_id) REFERENCES public.monster(monster_id) ON DELETE CASCADE
);
CREATE INDEX idx_monster_skills_monster_id ON public.monster_skills USING btree (monster_id);
CREATE INDEX idx_monster_skills_skill_id ON public.monster_skills USING btree (skill_id);
COMMENT ON TABLE public.monster_skills IS '몬스터 스킬 정보';

-- Column comments

COMMENT ON COLUMN public.monster_skills.monster_id IS '몬스터 ID';
COMMENT ON COLUMN public.monster_skills.skill_id IS '스킬 ID';
COMMENT ON COLUMN public.monster_skills.skill_order IS '스킬 순서';


-- public.monster_sources definition

-- Drop table

-- DROP TABLE public.monster_sources;

CREATE TABLE public.monster_sources (
	monster_id varchar(100) NOT NULL, -- 몬스터 ID
	source_id int4 NOT NULL, -- 획득 경로 ID
	source_name varchar(200) NULL, -- 획득 경로 명
	source_description text NULL,
	farmable_source bool DEFAULT false NULL, -- 농장 가능 여부
	source_order int4 NOT NULL,
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_monster_sources PRIMARY KEY (monster_id, source_id, source_order),
	CONSTRAINT fk_monster_sources_monster FOREIGN KEY (monster_id) REFERENCES public.monster(monster_id) ON DELETE CASCADE
);
CREATE INDEX idx_monster_sources_monster_id ON public.monster_sources USING btree (monster_id);
CREATE INDEX idx_monster_sources_source_id ON public.monster_sources USING btree (source_id);
COMMENT ON TABLE public.monster_sources IS '몬스터 획득 경로 정보';

-- Column comments

COMMENT ON COLUMN public.monster_sources.monster_id IS '몬스터 ID';
COMMENT ON COLUMN public.monster_sources.source_id IS '획득 경로 ID';
COMMENT ON COLUMN public.monster_sources.source_name IS '획득 경로 명';
COMMENT ON COLUMN public.monster_sources.farmable_source IS '농장 가능 여부';


-- public."notice" definition

-- Drop table

-- DROP TABLE public."notice";

CREATE TABLE public."notice" (
	notice_id int8 DEFAULT nextval('notice_seq'::regclass) NOT NULL, -- 공지사항 ID
	title varchar(500) NOT NULL, -- 제목
	"content" text NOT NULL, -- 내용
	is_important bool DEFAULT false NOT NULL, -- 중요 공지 여부
	is_popup bool DEFAULT false NOT NULL, -- 팝업 공지 여부
	view_count int4 DEFAULT 0 NOT NULL, -- 조회수
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제 여부
	crt_user_id varchar(20) NOT NULL, -- 등록자 ID
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(20) NOT NULL, -- 수정자 ID
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT notice_pkey PRIMARY KEY (notice_id),
	CONSTRAINT fk_notice_crt_user FOREIGN KEY (crt_user_id) REFERENCES public.sys_user(user_id),
	CONSTRAINT fk_notice_upt_user FOREIGN KEY (upt_user_id) REFERENCES public.sys_user(user_id)
);
CREATE INDEX idx_notice_crt_date ON public.notice USING btree (crt_date DESC);
CREATE INDEX idx_notice_del_yn ON public.notice USING btree (del_yn);
CREATE INDEX idx_notice_is_important ON public.notice USING btree (is_important) WHERE ((del_yn)::text = 'N'::text);
CREATE INDEX idx_notice_is_popup ON public.notice USING btree (is_popup) WHERE ((del_yn)::text = 'N'::text);
COMMENT ON TABLE public."notice" IS '공지사항';

-- Column comments

COMMENT ON COLUMN public."notice".notice_id IS '공지사항 ID';
COMMENT ON COLUMN public."notice".title IS '제목';
COMMENT ON COLUMN public."notice"."content" IS '내용';
COMMENT ON COLUMN public."notice".is_important IS '중요 공지 여부';
COMMENT ON COLUMN public."notice".is_popup IS '팝업 공지 여부';
COMMENT ON COLUMN public."notice".view_count IS '조회수';
COMMENT ON COLUMN public."notice".del_yn IS '삭제 여부';
COMMENT ON COLUMN public."notice".crt_user_id IS '등록자 ID';
COMMENT ON COLUMN public."notice".crt_date IS '등록일';
COMMENT ON COLUMN public."notice".upt_user_id IS '수정자 ID';
COMMENT ON COLUMN public."notice".upt_date IS '수정일';


-- public.notice_view definition

-- Drop table

-- DROP TABLE public.notice_view;

CREATE TABLE public.notice_view (
	notice_view_id bigserial NOT NULL, -- 공지사항 조회 기록 ID
	notice_id int8 NOT NULL, -- 공지사항 ID
	user_id varchar(20) NOT NULL, -- 사용자 ID
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제 여부
	crt_user_id varchar(20) NOT NULL, -- 등록자 ID
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(20) NOT NULL, -- 수정자 ID
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT notice_view_pkey PRIMARY KEY (notice_view_id),
	CONSTRAINT fk_notice_view_notice FOREIGN KEY (notice_id) REFERENCES public."notice"(notice_id),
	CONSTRAINT fk_notice_view_user FOREIGN KEY (user_id) REFERENCES public.sys_user(user_id)
);
CREATE INDEX idx_notice_view_del_yn ON public.notice_view USING btree (del_yn);
CREATE INDEX idx_notice_view_notice_id ON public.notice_view USING btree (notice_id);
CREATE INDEX idx_notice_view_user_id ON public.notice_view USING btree (user_id);
CREATE UNIQUE INDEX uk_notice_view_active ON public.notice_view USING btree (notice_id, user_id) WHERE ((del_yn)::text = 'N'::text);
COMMENT ON TABLE public.notice_view IS '공지사항 팝업 조회 기록';

-- Column comments

COMMENT ON COLUMN public.notice_view.notice_view_id IS '공지사항 조회 기록 ID';
COMMENT ON COLUMN public.notice_view.notice_id IS '공지사항 ID';
COMMENT ON COLUMN public.notice_view.user_id IS '사용자 ID';
COMMENT ON COLUMN public.notice_view.del_yn IS '삭제 여부';
COMMENT ON COLUMN public.notice_view.crt_user_id IS '등록자 ID';
COMMENT ON COLUMN public.notice_view.crt_date IS '등록일';
COMMENT ON COLUMN public.notice_view.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN public.notice_view.upt_date IS '수정일';


-- public.notification definition

-- Drop table

-- DROP TABLE public.notification;

CREATE TABLE public.notification (
	notification_id int8 DEFAULT nextval('notification_seq'::regclass) NOT NULL, -- 알림 ID
	user_id varchar(20) NOT NULL, -- 사용자 ID
	"type" varchar(50) NOT NULL, -- 알림 타입
	title varchar(200) NOT NULL, -- 알림 제목
	"content" text NOT NULL, -- 알림 내용
	related_id varchar(100) NULL, -- 관련 ID (예: inquiry_id, guild_application_id 등)
	related_url varchar(500) NULL, -- 관련 URL
	is_read varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 읽음 여부 (Y: 읽음, N: 읽지 않음)
	read_date timestamp NULL, -- 읽은 날짜
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제 여부
	crt_user_id varchar(20) NOT NULL, -- 등록자 ID
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(20) NULL, -- 수정자 ID
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT chk_notification_is_read CHECK (((is_read)::text = ANY ((ARRAY['Y'::character varying, 'N'::character varying])::text[]))),
	CONSTRAINT chk_notification_type CHECK (((type)::text = ANY ((ARRAY['GUILD_MEMBER_JOINED'::character varying, 'GUILD_MEMBER_LEFT'::character varying, 'GUILD_APPLICATION_PENDING'::character varying, 'INQUIRY_PENDING'::character varying, 'INQUIRY_ANSWERED'::character varying, 'NOTICE_NEW'::character varying, 'SYSTEM'::character varying])::text[]))),
	CONSTRAINT notification_pkey PRIMARY KEY (notification_id),
	CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES public.sys_user(user_id)
);
CREATE INDEX idx_notification_crt_date ON public.notification USING btree (crt_date DESC);
CREATE INDEX idx_notification_del_yn ON public.notification USING btree (del_yn);
CREATE INDEX idx_notification_is_read ON public.notification USING btree (is_read);
CREATE INDEX idx_notification_list ON public.notification USING btree (user_id, del_yn, is_read, crt_date DESC);
CREATE INDEX idx_notification_type ON public.notification USING btree (type);
CREATE INDEX idx_notification_unread ON public.notification USING btree (user_id, is_read, del_yn) WHERE (((is_read)::text = 'N'::text) AND ((del_yn)::text = 'N'::text));
CREATE INDEX idx_notification_user_id ON public.notification USING btree (user_id);
COMMENT ON TABLE public.notification IS '알림';

-- Column comments

COMMENT ON COLUMN public.notification.notification_id IS '알림 ID';
COMMENT ON COLUMN public.notification.user_id IS '사용자 ID';
COMMENT ON COLUMN public.notification."type" IS '알림 타입';
COMMENT ON COLUMN public.notification.title IS '알림 제목';
COMMENT ON COLUMN public.notification."content" IS '알림 내용';
COMMENT ON COLUMN public.notification.related_id IS '관련 ID (예: inquiry_id, guild_application_id 등)';
COMMENT ON COLUMN public.notification.related_url IS '관련 URL';
COMMENT ON COLUMN public.notification.is_read IS '읽음 여부 (Y: 읽음, N: 읽지 않음)';
COMMENT ON COLUMN public.notification.read_date IS '읽은 날짜';
COMMENT ON COLUMN public.notification.del_yn IS '삭제 여부';
COMMENT ON COLUMN public.notification.crt_user_id IS '등록자 ID';
COMMENT ON COLUMN public.notification.crt_date IS '등록일';
COMMENT ON COLUMN public.notification.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN public.notification.upt_date IS '수정일';


-- public.siege_recommended_attack_deck_stats definition

-- Drop table

-- DROP TABLE public.siege_recommended_attack_deck_stats;

CREATE TABLE public.siege_recommended_attack_deck_stats (
	deck_id int4 NOT NULL, -- 공덱 ID (FK, PK)
	monster_id varchar NOT NULL, -- 몬스터 ID (PK)
	hp int4 DEFAULT 0 NULL, -- 체력
	atk int4 DEFAULT 0 NULL, -- 공격력
	def int4 DEFAULT 0 NULL, -- 방어력
	spd int4 DEFAULT 0 NULL, -- 속도
	crit_rate int4 DEFAULT 0 NULL, -- 치명타 확률(%)
	crit_dmg int4 DEFAULT 0 NULL, -- 치명타 피해(%)
	resistance int4 DEFAULT 0 NULL, -- 효과 저항(%)
	accuracy int4 DEFAULT 0 NULL, -- 효과 적중(%)
	crt_user_id varchar(50) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT pk_siege_stats PRIMARY KEY (deck_id, monster_id),
	CONSTRAINT fk_deck FOREIGN KEY (deck_id) REFERENCES public.siege_recommended_attack_deck(id) ON DELETE CASCADE
);
CREATE INDEX idx_siege_stats_monster_id ON public.siege_recommended_attack_deck_stats USING btree (monster_id);
COMMENT ON TABLE public.siege_recommended_attack_deck_stats IS '점령전 추천 공덱 몬스터 스탯';

-- Column comments

COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.deck_id IS '공덱 ID (FK, PK)';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.monster_id IS '몬스터 ID (PK)';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.hp IS '체력';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.atk IS '공격력';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.def IS '방어력';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.spd IS '속도';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.crit_rate IS '치명타 확률(%)';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.crit_dmg IS '치명타 피해(%)';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.resistance IS '효과 저항(%)';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.accuracy IS '효과 적중(%)';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.crt_user_id IS '등록자';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.crt_date IS '등록일';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.upt_user_id IS '수정자';
COMMENT ON COLUMN public.siege_recommended_attack_deck_stats.upt_date IS '수정일';


-- public.sys_batch_run_his definition

-- Drop table

-- DROP TABLE public.sys_batch_run_his;

CREATE TABLE public.sys_batch_run_his (
	run_sn bigserial NOT NULL, -- 실행 일련번호 (PK)
	bat_id int8 NOT NULL, -- 배치 ID (FK: sys_batch_config.bat_id)
	start_dtm timestamp NULL, -- 실행 시작 시각
	end_dtm timestamp NULL, -- 실행 종료 시각
	rslt_cd varchar(20) NULL, -- 실행 결과 코드
	rslt_txt varchar(3000) NULL, -- 실행 결과 메시지
	crt_user_id varchar(32) NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NULL, -- 등록일시
	CONSTRAINT sys_batch_run_his_pkey PRIMARY KEY (run_sn),
	CONSTRAINT sys_batch_run_his_bat_id_fkey FOREIGN KEY (bat_id) REFERENCES public.sys_batch_config(bat_id)
);
CREATE INDEX idx_sys_batch_run_his_bat_id ON public.sys_batch_run_his USING btree (bat_id);
COMMENT ON TABLE public.sys_batch_run_his IS '배치 실행 이력';

-- Column comments

COMMENT ON COLUMN public.sys_batch_run_his.run_sn IS '실행 일련번호 (PK)';
COMMENT ON COLUMN public.sys_batch_run_his.bat_id IS '배치 ID (FK: sys_batch_config.bat_id)';
COMMENT ON COLUMN public.sys_batch_run_his.start_dtm IS '실행 시작 시각';
COMMENT ON COLUMN public.sys_batch_run_his.end_dtm IS '실행 종료 시각';
COMMENT ON COLUMN public.sys_batch_run_his.rslt_cd IS '실행 결과 코드';
COMMENT ON COLUMN public.sys_batch_run_his.rslt_txt IS '실행 결과 메시지';
COMMENT ON COLUMN public.sys_batch_run_his.crt_user_id IS '등록자';
COMMENT ON COLUMN public.sys_batch_run_his.crt_date IS '등록일시';


-- public.sys_page_item definition

-- Drop table

-- DROP TABLE public.sys_page_item;

CREATE TABLE public.sys_page_item (
	page_id varchar(50) NOT NULL, -- 페이지 번호 (FK)
	el_nm varchar(50) NOT NULL, -- 엘리먼트명
	vmodel_id varchar(100) NULL, -- Vue 모델 ID
	fromto_yn bpchar(1) DEFAULT 'N'::bpchar NULL, -- From/To 여부 (Y/N)
	vmodel_from_id varchar(100) NULL, -- Vue 모델 From ID
	vmodel_to_id varchar(100) NULL, -- Vue 모델 To ID
	calendar_sch_column_nm varchar(100) NULL, -- 캘린더 스케줄 컬럼명
	el_type varchar(20) NULL, -- 엘리먼트 타입
	el_width varchar(10) NULL, -- 엘리먼트 너비
	el_height varchar(10) NULL, -- 엘리먼트 높이
	srt_sn int4 NULL, -- 정렬순서
	usg_yn bpchar(1) DEFAULT 'Y'::bpchar NOT NULL, -- 사용여부 (Y/N)
	del_yn bpchar(1) DEFAULT 'N'::bpchar NOT NULL, -- 삭제여부 (Y/N)
	crt_user_id varchar(50) NOT NULL, -- 생성자 ID
	crt_date timestamp DEFAULT now() NULL, -- 생성일시
	upt_user_id varchar(50) NULL, -- 수정자 ID
	upt_date timestamp DEFAULT now() NULL, -- 수정일시
	CONSTRAINT sys_page_item_pkey PRIMARY KEY (page_id, el_nm),
	CONSTRAINT fk_page_item_page_no FOREIGN KEY (page_id) REFERENCES public.sys_page(page_id) ON DELETE CASCADE
);
CREATE INDEX idx_sys_page_item_del_yn ON public.sys_page_item USING btree (del_yn);
CREATE INDEX idx_sys_page_item_page_no ON public.sys_page_item USING btree (page_id);
CREATE INDEX idx_sys_page_item_srt_sn ON public.sys_page_item USING btree (srt_sn);
CREATE INDEX idx_sys_page_item_usg_yn ON public.sys_page_item USING btree (usg_yn);
COMMENT ON TABLE public.sys_page_item IS '시스템 페이지 검색 조건 아이템 테이블';

-- Column comments

COMMENT ON COLUMN public.sys_page_item.page_id IS '페이지 번호 (FK)';
COMMENT ON COLUMN public.sys_page_item.el_nm IS '엘리먼트명';
COMMENT ON COLUMN public.sys_page_item.vmodel_id IS 'Vue 모델 ID';
COMMENT ON COLUMN public.sys_page_item.fromto_yn IS 'From/To 여부 (Y/N)';
COMMENT ON COLUMN public.sys_page_item.vmodel_from_id IS 'Vue 모델 From ID';
COMMENT ON COLUMN public.sys_page_item.vmodel_to_id IS 'Vue 모델 To ID';
COMMENT ON COLUMN public.sys_page_item.calendar_sch_column_nm IS '캘린더 스케줄 컬럼명';
COMMENT ON COLUMN public.sys_page_item.el_type IS '엘리먼트 타입';
COMMENT ON COLUMN public.sys_page_item.el_width IS '엘리먼트 너비';
COMMENT ON COLUMN public.sys_page_item.el_height IS '엘리먼트 높이';
COMMENT ON COLUMN public.sys_page_item.srt_sn IS '정렬순서';
COMMENT ON COLUMN public.sys_page_item.usg_yn IS '사용여부 (Y/N)';
COMMENT ON COLUMN public.sys_page_item.del_yn IS '삭제여부 (Y/N)';
COMMENT ON COLUMN public.sys_page_item.crt_user_id IS '생성자 ID';
COMMENT ON COLUMN public.sys_page_item.crt_date IS '생성일시';
COMMENT ON COLUMN public.sys_page_item.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN public.sys_page_item.upt_date IS '수정일시';


-- public.user_guild definition

-- Drop table

-- DROP TABLE public.user_guild;

CREATE TABLE public.user_guild (
	user_id varchar(20) NOT NULL, -- 사용자 ID
	guild_id int8 NOT NULL, -- 길드 ID
	join_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 가입일
	"role" varchar(20) DEFAULT 'MEMBER'::character varying NOT NULL, -- 역할 (LEADER: 길드장, SUB_LEADER: 부길드장, MEMBER: 일반 멤버)
	usg_yn varchar(1) DEFAULT 'Y'::character varying NOT NULL, -- 사용여부
	del_yn varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 삭제여부
	crt_user_id varchar(50) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	CONSTRAINT pk_user_guild PRIMARY KEY (user_id),
	CONSTRAINT fk_user_guild_guild FOREIGN KEY (guild_id) REFERENCES public.guild(guild_id) ON DELETE CASCADE,
	CONSTRAINT fk_user_guild_user FOREIGN KEY (user_id) REFERENCES public.sys_user(user_id) ON DELETE CASCADE
);
CREATE INDEX idx_user_guild_guild_id ON public.user_guild USING btree (guild_id);
CREATE INDEX idx_user_guild_role ON public.user_guild USING btree (role);
CREATE INDEX idx_user_guild_usg_del ON public.user_guild USING btree (usg_yn, del_yn);
COMMENT ON TABLE public.user_guild IS '유저-길드 현재 소속 관계';

-- Column comments

COMMENT ON COLUMN public.user_guild.user_id IS '사용자 ID';
COMMENT ON COLUMN public.user_guild.guild_id IS '길드 ID';
COMMENT ON COLUMN public.user_guild.join_date IS '가입일';
COMMENT ON COLUMN public.user_guild."role" IS '역할 (LEADER: 길드장, SUB_LEADER: 부길드장, MEMBER: 일반 멤버)';
COMMENT ON COLUMN public.user_guild.usg_yn IS '사용여부';
COMMENT ON COLUMN public.user_guild.del_yn IS '삭제여부';
COMMENT ON COLUMN public.user_guild.crt_user_id IS '등록자';
COMMENT ON COLUMN public.user_guild.crt_date IS '등록일';
COMMENT ON COLUMN public.user_guild.upt_user_id IS '수정자';
COMMENT ON COLUMN public.user_guild.upt_date IS '수정일';


-- public.user_guild_history definition

-- Drop table

-- DROP TABLE public.user_guild_history;

CREATE TABLE public.user_guild_history (
	history_id int8 DEFAULT nextval('user_guild_history_seq'::regclass) NOT NULL, -- 이력 ID
	user_id varchar(20) NOT NULL, -- 사용자 ID
	guild_id int8 NOT NULL, -- 길드 ID
	join_date timestamp NOT NULL, -- 가입일
	leave_date timestamp NULL, -- 탈퇴일
	"role" varchar(20) NULL, -- 역할
	leave_reason varchar(500) NULL, -- 탈퇴 사유
	crt_user_id varchar(50) NOT NULL, -- 등록자
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 등록일
	upt_user_id varchar(50) NOT NULL, -- 수정자
	upt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 수정일
	join_by_invite varchar(1) DEFAULT 'N'::character varying NOT NULL, -- 초대 코드로 가입 여부 (Y: 초대 코드, N: 일반 가입)
	CONSTRAINT pk_user_guild_history PRIMARY KEY (history_id),
	CONSTRAINT fk_user_guild_history_guild FOREIGN KEY (guild_id) REFERENCES public.guild(guild_id) ON DELETE CASCADE,
	CONSTRAINT fk_user_guild_history_user FOREIGN KEY (user_id) REFERENCES public.sys_user(user_id) ON DELETE CASCADE
);
CREATE INDEX idx_user_guild_history_guild_id ON public.user_guild_history USING btree (guild_id);
CREATE INDEX idx_user_guild_history_join_date ON public.user_guild_history USING btree (join_date);
CREATE INDEX idx_user_guild_history_leave_date ON public.user_guild_history USING btree (leave_date);
CREATE INDEX idx_user_guild_history_user_id ON public.user_guild_history USING btree (user_id);
COMMENT ON TABLE public.user_guild_history IS '유저 길드 이력';

-- Column comments

COMMENT ON COLUMN public.user_guild_history.history_id IS '이력 ID';
COMMENT ON COLUMN public.user_guild_history.user_id IS '사용자 ID';
COMMENT ON COLUMN public.user_guild_history.guild_id IS '길드 ID';
COMMENT ON COLUMN public.user_guild_history.join_date IS '가입일';
COMMENT ON COLUMN public.user_guild_history.leave_date IS '탈퇴일';
COMMENT ON COLUMN public.user_guild_history."role" IS '역할';
COMMENT ON COLUMN public.user_guild_history.leave_reason IS '탈퇴 사유';
COMMENT ON COLUMN public.user_guild_history.crt_user_id IS '등록자';
COMMENT ON COLUMN public.user_guild_history.crt_date IS '등록일';
COMMENT ON COLUMN public.user_guild_history.upt_user_id IS '수정자';
COMMENT ON COLUMN public.user_guild_history.upt_date IS '수정일';
COMMENT ON COLUMN public.user_guild_history.join_by_invite IS '초대 코드로 가입 여부 (Y: 초대 코드, N: 일반 가입)';


-- public.level_enemies definition

-- Drop table

-- DROP TABLE public.level_enemies;

CREATE TABLE public.level_enemies (
	enemy_id int4 NOT NULL, -- 적 ID (Swarfarm API ID)
	level_id int4 NOT NULL, -- 레벨 ID
	wave_number int4 NOT NULL, -- 웨이브 번호
	monster_swarfarm_id int4 NULL, -- 몬스터 Swarfarm ID
	stars int4 NULL, -- 별 개수
	"level" int4 NULL, -- 몬스터 레벨
	hp int4 NULL, -- 체력
	attack int4 NULL, -- 공격력
	defense int4 NULL, -- 방어력
	speed int4 NULL, -- 속도
	resist int4 NULL, -- 저항
	crit_bonus int4 DEFAULT 0 NULL,
	crit_damage_reduction int4 DEFAULT 0 NULL,
	accuracy_bonus int4 DEFAULT 0 NULL,
	crt_date timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
	CONSTRAINT pk_level_enemies PRIMARY KEY (enemy_id),
	CONSTRAINT fk_level_enemies_wave FOREIGN KEY (level_id,wave_number) REFERENCES public.level_waves(level_id,wave_number) ON DELETE CASCADE
);
CREATE INDEX idx_level_enemies_level_id ON public.level_enemies USING btree (level_id);
CREATE INDEX idx_level_enemies_monster_id ON public.level_enemies USING btree (monster_swarfarm_id);
CREATE INDEX idx_level_enemies_wave ON public.level_enemies USING btree (level_id, wave_number);
COMMENT ON TABLE public.level_enemies IS '레벨 웨이브별 적 몬스터 정보';

-- Column comments

COMMENT ON COLUMN public.level_enemies.enemy_id IS '적 ID (Swarfarm API ID)';
COMMENT ON COLUMN public.level_enemies.level_id IS '레벨 ID';
COMMENT ON COLUMN public.level_enemies.wave_number IS '웨이브 번호';
COMMENT ON COLUMN public.level_enemies.monster_swarfarm_id IS '몬스터 Swarfarm ID';
COMMENT ON COLUMN public.level_enemies.stars IS '별 개수';
COMMENT ON COLUMN public.level_enemies."level" IS '몬스터 레벨';
COMMENT ON COLUMN public.level_enemies.hp IS '체력';
COMMENT ON COLUMN public.level_enemies.attack IS '공격력';
COMMENT ON COLUMN public.level_enemies.defense IS '방어력';
COMMENT ON COLUMN public.level_enemies.speed IS '속도';
COMMENT ON COLUMN public.level_enemies.resist IS '저항';