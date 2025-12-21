package com.sysconf.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
public class S3Service {
    
    private static final String BUCKET_NAME = "summonerswar-community";
    private static final String CLOUDFRONT_URL = "https://dyjduzi8vf2k4.cloudfront.net";
    private static final String MONSTER_FOLDER = "monster";
    private static final String FILES_FOLDER = "files"; // 일반 파일 저장 폴더
    
    /**
     * S3 클라이언트 생성
     * DefaultCredentialsProvider를 사용하여 EC2 IAM 역할(인스턴스 프로파일)을 자동으로 인식합니다.
     * 
     * AWS SDK v2의 DefaultCredentialsProvider는 다음 순서로 자격 증명을 찾습니다:
     * 1. 환경 변수 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY) - Kubernetes 환경에서는 설정하지 않음
     * 2. Java 시스템 속성
     * 3. 자격 증명 파일 (~/.aws/credentials)
     * 4. EC2 인스턴스 메타데이터 서비스 (IAM 역할) <- Kubernetes Pod에서 이 방법 사용
     * 
     * Kubernetes 환경에서는 EC2 인스턴스에 부여된 IAM 역할을 자동으로 감지합니다.
     * application.yml의 cloud.aws.credentials.instance-profile: true 설정과 함께 사용됩니다.
     */
    private S3Client createS3Client() {
        return S3Client.builder()
                // 명시적으로 서울 리전(ap-northeast-2) 설정 - 환경 변수 무시 방지
                .region(Region.AP_NORTHEAST_2)
                // DefaultCredentialsProvider를 명시적으로 설정하여 EC2 IAM 역할 자동 인식
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
    
    /**
     * 이미지를 S3에 업로드하고 CloudFront URL 반환
     * 
     * @param inputStream 이미지 파일의 InputStream
     * @param fileName 파일명 (확장자 포함)
     * @param contentType Content-Type (예: "image/png", "image/jpeg")
     * @return CloudFront URL
     */
    public String uploadImage(InputStream inputStream, String fileName, String contentType) {
        S3Client s3Client = null;
        try {
            s3Client = createS3Client();
            
            // S3 키 생성 (monster/ 폴더 아래에 저장)
            String s3Key = MONSTER_FOLDER + "/" + fileName;
            
            // PutObjectRequest 생성 (PublicRead ACL 설정으로 CloudFront에서 읽을 수 있도록)
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PUBLIC_READ) // CloudFront에서 읽을 수 있도록 PublicRead 설정
                    .build();
            
            // InputStream을 바이트 배열로 변환 (Java 8 호환)
            byte[] imageBytes = inputStreamToByteArray(inputStream);
            
            // S3에 업로드
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
            
            log.info("S3 업로드 완료: {}/{}", BUCKET_NAME, s3Key);
            
            // CloudFront URL 반환
            String cloudFrontUrl = CLOUDFRONT_URL + "/" + s3Key;
            return cloudFrontUrl;
            
        } catch (S3Exception e) {
            log.error("S3 업로드 실패: {}", e.getMessage(), e);
            throw new RuntimeException("S3 업로드 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("이미지 업로드 중 오류 발생: {}", fileName, e);
            throw new RuntimeException("이미지 업로드 실패: " + e.getMessage(), e);
        } finally {
            if (s3Client != null) {
                s3Client.close();
            }
        }
    }
    
    /**
     * 바이트 배열을 S3에 업로드하고 CloudFront URL 반환
     * 
     * @param imageBytes 이미지 파일의 바이트 배열
     * @param fileName 파일명 (확장자 포함)
     * @param contentType Content-Type (예: "image/png", "image/jpeg")
     * @return CloudFront URL
     */
    public String uploadImage(byte[] imageBytes, String fileName, String contentType) {
        S3Client s3Client = null;
        try {
            s3Client = createS3Client();
            
            // S3 키 생성 (monster/ 폴더 아래에 저장)
            String s3Key = MONSTER_FOLDER + "/" + fileName;
            
            // PutObjectRequest 생성 (PublicRead ACL 설정으로 CloudFront에서 읽을 수 있도록)
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PUBLIC_READ) // CloudFront에서 읽을 수 있도록 PublicRead 설정
                    .build();
            
            // S3에 업로드
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
            
            log.info("S3 업로드 완료: {}/{}", BUCKET_NAME, s3Key);
            
            // CloudFront URL 반환
            String cloudFrontUrl = CLOUDFRONT_URL + "/" + s3Key;
            return cloudFrontUrl;
            
        } catch (S3Exception e) {
            log.error("S3 업로드 실패: {}", e.getMessage(), e);
            throw new RuntimeException("S3 업로드 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("이미지 업로드 중 오류 발생: {}", fileName, e);
            throw new RuntimeException("이미지 업로드 실패: " + e.getMessage(), e);
        } finally {
            if (s3Client != null) {
                s3Client.close();
            }
        }
    }
    
    /**
     * InputStream을 바이트 배열로 변환 (Java 8 호환)
     */
    private byte[] inputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }
    
    /**
     * 파일명에서 Content-Type 추론
     */
    private String getContentTypeFromFileName(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFileName.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg"; // 기본값
    }
    
    /**
     * 파일명에서 Content-Type을 자동 추론하여 업로드
     */
    public String uploadImage(byte[] imageBytes, String fileName) {
        String contentType = getContentTypeFromFileName(fileName);
        return uploadImage(imageBytes, fileName, contentType);
    }
    
    /**
     * InputStream에서 Content-Type을 자동 추론하여 업로드
     */
    public String uploadImage(InputStream inputStream, String fileName) {
        String contentType = getContentTypeFromFileName(fileName);
        return uploadImage(inputStream, fileName, contentType);
    }
    
    /**
     * 일반 파일을 S3에 업로드하고 CloudFront URL 반환
     * 
     * @param fileBytes 파일의 바이트 배열
     * @param fileName 파일명 (확장자 포함)
     * @param contentType Content-Type (예: "application/json", "application/pdf")
     * @param folder 저장할 폴더 (기본값: "files")
     * @return CloudFront URL
     */
    public String uploadFile(byte[] fileBytes, String fileName, String contentType, String folder) {
        S3Client s3Client = null;
        try {
            s3Client = createS3Client();
            
            // 폴더가 없으면 기본값 사용
            if (folder == null || folder.isEmpty()) {
                folder = FILES_FOLDER;
            }
            
            // S3 키 생성
            String s3Key = folder + "/" + fileName;
            
            // PutObjectRequest 생성 (PublicRead ACL 설정으로 CloudFront에서 읽을 수 있도록)
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PUBLIC_READ) // CloudFront에서 읽을 수 있도록 PublicRead 설정
                    .build();
            
            // S3에 업로드
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
            
            log.info("S3 파일 업로드 완료: {}/{}", BUCKET_NAME, s3Key);
            
            // CloudFront URL 반환
            String cloudFrontUrl = CLOUDFRONT_URL + "/" + s3Key;
            return cloudFrontUrl;
            
        } catch (S3Exception e) {
            log.error("S3 파일 업로드 실패: {}", e.getMessage(), e);
            throw new RuntimeException("S3 파일 업로드 실패: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("파일 업로드 중 오류 발생: {}", fileName, e);
            throw new RuntimeException("파일 업로드 실패: " + e.getMessage(), e);
        } finally {
            if (s3Client != null) {
                s3Client.close();
            }
        }
    }
    
    /**
     * 일반 파일을 S3에 업로드 (기본 폴더 사용)
     */
    public String uploadFile(byte[] fileBytes, String fileName, String contentType) {
        return uploadFile(fileBytes, fileName, contentType, null);
    }
    
    /**
     * 파일명에서 Content-Type 추론 (일반 파일 포함)
     */
    public String getContentType(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        // 이미지
        if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFileName.endsWith(".webp")) {
            return "image/webp";
        }
        // JSON
        else if (lowerFileName.endsWith(".json")) {
            return "application/json";
        }
        // PDF
        else if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        // 기타
        return "application/octet-stream"; // 기본값
    }
}

