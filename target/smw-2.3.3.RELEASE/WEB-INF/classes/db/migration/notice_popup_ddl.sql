-- ============================================
-- 공지사항(NOTICE) 테이블 DDL
-- ============================================

-- 1. NOTICE 시퀀스 생성
CREATE SEQUENCE IF NOT EXISTS notice_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

COMMENT ON SEQUENCE notice_seq IS '공지사항 ID 시퀀스';

-- 2. NOTICE 테이블 생성
CREATE TABLE IF NOT EXISTS NOTICE (
    notice_id BIGINT PRIMARY KEY DEFAULT nextval('notice_seq'),
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    is_important BOOLEAN DEFAULT false NOT NULL,
    is_popup BOOLEAN DEFAULT false NOT NULL,
    view_count INTEGER DEFAULT 0 NOT NULL,
    del_yn VARCHAR(1) DEFAULT 'N' NOT NULL,
    crt_user_id VARCHAR(20) NOT NULL,
    crt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    upt_user_id VARCHAR(20) NOT NULL,
    upt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_notice_crt_user FOREIGN KEY (crt_user_id) REFERENCES SYS_USER(user_id),
    CONSTRAINT fk_notice_upt_user FOREIGN KEY (upt_user_id) REFERENCES SYS_USER(user_id)
);

COMMENT ON TABLE NOTICE IS '공지사항';
COMMENT ON COLUMN NOTICE.notice_id IS '공지사항 ID';
COMMENT ON COLUMN NOTICE.title IS '제목';
COMMENT ON COLUMN NOTICE.content IS '내용';
COMMENT ON COLUMN NOTICE.is_important IS '중요 공지 여부';
COMMENT ON COLUMN NOTICE.is_popup IS '팝업 공지 여부';
COMMENT ON COLUMN NOTICE.view_count IS '조회수';
COMMENT ON COLUMN NOTICE.del_yn IS '삭제 여부';
COMMENT ON COLUMN NOTICE.crt_user_id IS '등록자 ID';
COMMENT ON COLUMN NOTICE.crt_date IS '등록일';
COMMENT ON COLUMN NOTICE.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN NOTICE.upt_date IS '수정일';

-- 3. NOTICE 테이블 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_notice_del_yn ON NOTICE(del_yn);
CREATE INDEX IF NOT EXISTS idx_notice_crt_date ON NOTICE(crt_date DESC);
CREATE INDEX IF NOT EXISTS idx_notice_is_important ON NOTICE(is_important) WHERE del_yn = 'N';
CREATE INDEX IF NOT EXISTS idx_notice_is_popup ON NOTICE(is_popup) WHERE del_yn = 'N';

-- 4. NOTICE_VIEW 테이블 생성 (팝업 공지사항 조회 기록 저장용)
CREATE TABLE IF NOT EXISTS NOTICE_VIEW (
    notice_view_id BIGSERIAL PRIMARY KEY,
    notice_id BIGINT NOT NULL,
    user_id VARCHAR(20) NOT NULL,
    del_yn VARCHAR(1) DEFAULT 'N' NOT NULL,
    crt_user_id VARCHAR(20) NOT NULL,
    crt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    upt_user_id VARCHAR(20) NOT NULL,
    upt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_notice_view_notice FOREIGN KEY (notice_id) REFERENCES NOTICE(notice_id),
    CONSTRAINT fk_notice_view_user FOREIGN KEY (user_id) REFERENCES SYS_USER(user_id)
);

COMMENT ON TABLE NOTICE_VIEW IS '공지사항 팝업 조회 기록';
COMMENT ON COLUMN NOTICE_VIEW.notice_view_id IS '공지사항 조회 기록 ID';
COMMENT ON COLUMN NOTICE_VIEW.notice_id IS '공지사항 ID';
COMMENT ON COLUMN NOTICE_VIEW.user_id IS '사용자 ID';
COMMENT ON COLUMN NOTICE_VIEW.del_yn IS '삭제 여부';
COMMENT ON COLUMN NOTICE_VIEW.crt_user_id IS '등록자 ID';
COMMENT ON COLUMN NOTICE_VIEW.crt_date IS '등록일';
COMMENT ON COLUMN NOTICE_VIEW.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN NOTICE_VIEW.upt_date IS '수정일';

-- 5. NOTICE_VIEW 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_notice_view_notice_id ON NOTICE_VIEW(notice_id);
CREATE INDEX IF NOT EXISTS idx_notice_view_user_id ON NOTICE_VIEW(user_id);
CREATE INDEX IF NOT EXISTS idx_notice_view_del_yn ON NOTICE_VIEW(del_yn);

-- 6. 부분 유니크 인덱스 (del_yn = 'N'인 경우에만 중복 방지)
CREATE UNIQUE INDEX IF NOT EXISTS uk_notice_view_active 
ON NOTICE_VIEW(notice_id, user_id) 
WHERE del_yn = 'N';

