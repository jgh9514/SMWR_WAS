-- ============================================
-- 이메일 인증 기능 DDL
-- ============================================

-- SYS_USER 테이블에 email 컬럼 추가
ALTER TABLE SYS_USER 
ADD COLUMN IF NOT EXISTS email VARCHAR(255);

COMMENT ON COLUMN SYS_USER.email IS '이메일 주소';

-- 이메일 중복 방지를 위한 유니크 인덱스 (NULL 제외)
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_email 
ON SYS_USER(email) 
WHERE email IS NOT NULL AND del_yn = 'N';

