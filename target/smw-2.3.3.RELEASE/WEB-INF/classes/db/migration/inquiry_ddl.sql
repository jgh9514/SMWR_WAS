-- ============================================
-- 1대1문의(INQUIRY) 테이블 DDL
-- ============================================

-- 1. INQUIRY 시퀀스 생성
CREATE SEQUENCE IF NOT EXISTS inquiry_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

COMMENT ON SEQUENCE inquiry_seq IS '1대1문의 ID 시퀀스';

-- 2. INQUIRY 테이블 생성
CREATE TABLE IF NOT EXISTS INQUIRY (
    inquiry_id BIGINT PRIMARY KEY DEFAULT nextval('inquiry_seq'),
    user_id VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    answer TEXT,
    status VARCHAR(20) DEFAULT 'PENDING' NOT NULL,
    answer_user_id VARCHAR(20),
    crt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    answer_date TIMESTAMP,
    upt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    del_yn VARCHAR(1) DEFAULT 'N' NOT NULL,
    CONSTRAINT fk_inquiry_user FOREIGN KEY (user_id) REFERENCES SYS_USER(user_id),
    CONSTRAINT fk_inquiry_answer_user FOREIGN KEY (answer_user_id) REFERENCES SYS_USER(user_id),
    CONSTRAINT chk_inquiry_status CHECK (status IN ('PENDING', 'ANSWERED'))
);

COMMENT ON TABLE INQUIRY IS '1대1문의';
COMMENT ON COLUMN INQUIRY.inquiry_id IS '문의 ID';
COMMENT ON COLUMN INQUIRY.user_id IS '문의자 ID';
COMMENT ON COLUMN INQUIRY.title IS '제목';
COMMENT ON COLUMN INQUIRY.content IS '내용';
COMMENT ON COLUMN INQUIRY.answer IS '답변 내용';
COMMENT ON COLUMN INQUIRY.status IS '상태 (PENDING: 대기, ANSWERED: 답변완료)';
COMMENT ON COLUMN INQUIRY.answer_user_id IS '답변자 ID';
COMMENT ON COLUMN INQUIRY.crt_date IS '등록일';
COMMENT ON COLUMN INQUIRY.answer_date IS '답변일';
COMMENT ON COLUMN INQUIRY.upt_date IS '수정일';
COMMENT ON COLUMN INQUIRY.del_yn IS '삭제 여부';

-- 3. 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_inquiry_user_id ON INQUIRY(user_id);
CREATE INDEX IF NOT EXISTS idx_inquiry_status ON INQUIRY(status);
CREATE INDEX IF NOT EXISTS idx_inquiry_del_yn ON INQUIRY(del_yn);
CREATE INDEX IF NOT EXISTS idx_inquiry_crt_date ON INQUIRY(crt_date DESC);
CREATE INDEX IF NOT EXISTS idx_inquiry_answer_user_id ON INQUIRY(answer_user_id);

-- 4. 복합 인덱스 (목록 조회 성능 향상)
CREATE INDEX IF NOT EXISTS idx_inquiry_list ON INQUIRY(del_yn, status, crt_date DESC);
CREATE INDEX IF NOT EXISTS idx_inquiry_user_list ON INQUIRY(user_id, del_yn, status, crt_date DESC);

