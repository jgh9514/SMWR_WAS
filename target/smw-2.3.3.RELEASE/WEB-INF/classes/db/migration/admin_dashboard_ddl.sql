-- ============================================
-- 관리자 대시보드 통계용 인덱스 추가
-- ============================================

-- SYS_USER 테이블의 crt_date 인덱스 (이미 있을 수 있음)
CREATE INDEX IF NOT EXISTS idx_sys_user_crt_date ON SYS_USER(crt_date DESC) WHERE del_yn = 'N';

-- SYS_API_EXE_LOG 테이블의 exe_dtm 인덱스 (이미 있을 수 있음)
CREATE INDEX IF NOT EXISTS idx_sys_api_exe_log_exe_dtm ON SYS_API_EXE_LOG(exe_dtm DESC);

-- NOTICE 테이블의 crt_date 인덱스 (이미 있을 수 있음)
-- idx_notice_crt_date 인덱스가 이미 notice_popup_ddl.sql에 정의되어 있음

-- INQUIRY 테이블의 crt_date 인덱스 (이미 있을 수 있음)
-- idx_inquiry_crt_date 인덱스가 이미 inquiry_ddl.sql에 정의되어 있음

-- GUILD_APPLICATION 테이블의 crt_date 인덱스
CREATE INDEX IF NOT EXISTS idx_guild_application_crt_date ON GUILD_APPLICATION(crt_date DESC);

-- GUILD_APPLICATION 테이블의 status 인덱스
CREATE INDEX IF NOT EXISTS idx_guild_application_status ON GUILD_APPLICATION(status) WHERE status = 'PENDING';

-- 복합 인덱스 (대시보드 통계 쿼리 성능 향상)
CREATE INDEX IF NOT EXISTS idx_guild_application_status_date ON GUILD_APPLICATION(status, crt_date DESC);

