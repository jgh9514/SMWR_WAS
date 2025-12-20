-- S3 파일 저장을 위한 sys_file_attachment 테이블 구조 변경
-- file_path 컬럼을 file_url로 변경 (CloudFront URL 저장용)

-- 1. 기존 데이터 백업 (필요시)
-- CREATE TABLE sys_file_attachment_backup AS SELECT * FROM sys_file_attachment;

-- 2. file_path 컬럼을 file_url로 변경
ALTER TABLE sys_file_attachment RENAME COLUMN file_path TO file_url;

-- 3. 컬럼 코멘트 업데이트
COMMENT ON COLUMN sys_file_attachment.file_url IS '파일 URL (S3 CloudFront URL)';

-- 4. 기존 데이터가 있다면 file_path 값을 file_url로 마이그레이션 (필요시)
-- UPDATE sys_file_attachment SET file_url = 'https://dyjduzi8vf2k4.cloudfront.net/' || file_url WHERE file_url NOT LIKE 'http%';

