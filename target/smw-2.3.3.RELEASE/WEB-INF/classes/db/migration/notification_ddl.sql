-- ============================================
-- 알림(NOTIFICATION) 테이블 DDL
-- ============================================

-- 1. NOTIFICATION 시퀀스 생성
CREATE SEQUENCE IF NOT EXISTS notification_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

COMMENT ON SEQUENCE notification_seq IS '알림 ID 시퀀스';

-- 2. NOTIFICATION 테이블 생성
CREATE TABLE IF NOT EXISTS NOTIFICATION (
    notification_id BIGINT PRIMARY KEY DEFAULT nextval('notification_seq'),
    user_id VARCHAR(20) NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    related_id VARCHAR(100),
    related_url VARCHAR(500),
    is_read VARCHAR(1) DEFAULT 'N' NOT NULL,
    read_date TIMESTAMP,
    del_yn VARCHAR(1) DEFAULT 'N' NOT NULL,
    crt_user_id VARCHAR(20) NOT NULL,
    crt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    upt_user_id VARCHAR(20),
    upt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES SYS_USER(user_id),
    CONSTRAINT chk_notification_type CHECK (type IN (
        'GUILD_MEMBER_JOINED',
        'GUILD_MEMBER_LEFT',
        'GUILD_APPLICATION_PENDING',
        'INQUIRY_PENDING',
        'INQUIRY_ANSWERED',
        'NOTICE_NEW',
        'SYSTEM'
    )),
    CONSTRAINT chk_notification_is_read CHECK (is_read IN ('Y', 'N'))
);

COMMENT ON TABLE NOTIFICATION IS '알림';
COMMENT ON COLUMN NOTIFICATION.notification_id IS '알림 ID';
COMMENT ON COLUMN NOTIFICATION.user_id IS '사용자 ID';
COMMENT ON COLUMN NOTIFICATION.type IS '알림 타입';
COMMENT ON COLUMN NOTIFICATION.title IS '알림 제목';
COMMENT ON COLUMN NOTIFICATION.content IS '알림 내용';
COMMENT ON COLUMN NOTIFICATION.related_id IS '관련 ID (예: inquiry_id, guild_application_id 등)';
COMMENT ON COLUMN NOTIFICATION.related_url IS '관련 URL';
COMMENT ON COLUMN NOTIFICATION.is_read IS '읽음 여부 (Y: 읽음, N: 읽지 않음)';
COMMENT ON COLUMN NOTIFICATION.read_date IS '읽은 날짜';
COMMENT ON COLUMN NOTIFICATION.del_yn IS '삭제 여부';
COMMENT ON COLUMN NOTIFICATION.crt_user_id IS '등록자 ID';
COMMENT ON COLUMN NOTIFICATION.crt_date IS '등록일';
COMMENT ON COLUMN NOTIFICATION.upt_user_id IS '수정자 ID';
COMMENT ON COLUMN NOTIFICATION.upt_date IS '수정일';

-- 3. 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_notification_user_id ON NOTIFICATION(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_is_read ON NOTIFICATION(is_read);
CREATE INDEX IF NOT EXISTS idx_notification_type ON NOTIFICATION(type);
CREATE INDEX IF NOT EXISTS idx_notification_del_yn ON NOTIFICATION(del_yn);
CREATE INDEX IF NOT EXISTS idx_notification_crt_date ON NOTIFICATION(crt_date DESC);

-- 4. 복합 인덱스 (목록 조회 성능 향상)
CREATE INDEX IF NOT EXISTS idx_notification_list ON NOTIFICATION(user_id, del_yn, is_read, crt_date DESC);
CREATE INDEX IF NOT EXISTS idx_notification_unread ON NOTIFICATION(user_id, is_read, del_yn) WHERE is_read = 'N' AND del_yn = 'N';

