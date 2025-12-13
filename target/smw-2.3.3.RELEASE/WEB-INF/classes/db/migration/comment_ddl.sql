-- 댓글 테이블 생성
CREATE TABLE IF NOT EXISTS COMMENT (
    comment_id VARCHAR(50) PRIMARY KEY,
    board_type VARCHAR(20) NOT NULL, -- 'NOTICE' 또는 'INQUIRY'
    board_id VARCHAR(50) NOT NULL, -- notice_id 또는 inquiry_id
    parent_comment_id VARCHAR(50), -- 대댓글인 경우 부모 댓글 ID
    user_id VARCHAR(50) NOT NULL,
    user_name VARCHAR(100),
    content TEXT NOT NULL,
    del_yn CHAR(1) DEFAULT 'N',
    crt_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    mdf_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    crt_user_id VARCHAR(50),
    mdf_user_id VARCHAR(50)
);

-- 인덱스 생성
CREATE INDEX idx_comment_board ON COMMENT(board_type, board_id, del_yn);
CREATE INDEX idx_comment_parent ON COMMENT(parent_comment_id, del_yn);
CREATE INDEX idx_comment_user ON COMMENT(user_id, del_yn);
CREATE INDEX idx_comment_crt_date ON COMMENT(crt_date DESC);

-- 코멘트
COMMENT ON TABLE COMMENT IS '댓글 테이블';
COMMENT ON COLUMN COMMENT.comment_id IS '댓글 ID';
COMMENT ON COLUMN COMMENT.board_type IS '게시판 타입 (NOTICE, INQUIRY)';
COMMENT ON COLUMN COMMENT.board_id IS '게시글 ID';
COMMENT ON COLUMN COMMENT.parent_comment_id IS '부모 댓글 ID (대댓글인 경우)';
COMMENT ON COLUMN COMMENT.user_id IS '작성자 ID';
COMMENT ON COLUMN COMMENT.user_name IS '작성자 이름';
COMMENT ON COLUMN COMMENT.content IS '댓글 내용';
COMMENT ON COLUMN COMMENT.del_yn IS '삭제 여부';
COMMENT ON COLUMN COMMENT.crt_date IS '생성 일시';
COMMENT ON COLUMN COMMENT.mdf_date IS '수정 일시';
COMMENT ON COLUMN COMMENT.crt_user_id IS '생성자 ID';
COMMENT ON COLUMN COMMENT.mdf_user_id IS '수정자 ID';

