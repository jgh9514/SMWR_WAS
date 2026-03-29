package com.sysconf.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
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
    /** Swarfarm 스킬 아이콘 등 스킬 이미지 S3 경로 접두어 */
    public static final String SKILLS_FOLDER = "skills";
    /** Swarfarm 스킬 이펙트(buff/debuff 등) 아이콘 */
    public static final String SKILL_EFFECTS_FOLDER = "skill-effects";
    /** 리더 스킬 이미지를 올릴 때 사용 (현재 동기화는 텍스트만 저장, 확장용) */
    public static final String LEADER_SKILLS_FOLDER = "leader-skills";
    private static final String FILES_FOLDER = "files"; // 일반 파일 저장 폴더

    @Value("${smw.aws.access-key-id:}")
    private String accessKeyId;

    @Value("${smw.aws.secret-access-key:}")
    private String secretAccessKey;

    @Value("${smw.aws.region:ap-southeast-2}")
    private String awsRegion;

    private S3Client s3Client;

    /**
     * smw.aws.access-key-id / secret-access-key(또는 환경변수 AWS_*)가 있으면 정적 키로 S3 접속.
     * 없으면 DefaultCredentialsProvider(환경변수, ~/.aws/credentials, EC2/ECS IAM 역할 순).
     * 로컬에서 SdkClientException: Unable to load credentials 이면 IAM 사용자 키를 환경변수로 넣거나 aws configure.
     */
    @PostConstruct
    public void initS3Client() {
        var builder = S3Client.builder().region(Region.of(awsRegion.trim()));
        if (StringUtils.hasText(accessKeyId) && StringUtils.hasText(secretAccessKey)) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim())));
            log.info("S3 클라이언트: 정적 자격 증명 사용 (smw.aws / AWS_ACCESS_KEY_ID), region={}", awsRegion);
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.info("S3 클라이언트: DefaultCredentialsProvider, region={}. 로컬 실패 시 AWS_ACCESS_KEY_ID·AWS_SECRET_ACCESS_KEY 또는 %s",
                    awsRegion, System.getProperty("user.home") + "/.aws/credentials");
        }
        this.s3Client = builder.build();
    }

    private S3Client getS3Client() {
        return s3Client;
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
        return uploadImage(inputStream, fileName, contentType, MONSTER_FOLDER);
    }

    /**
     * 이미지를 S3 지정 폴더에 업로드 (Swarfarm 스킬 아이콘은 {@link #SKILLS_FOLDER} 등)
     *
     * @param s3Folder S3 키 접두어 (예: {@code monster}, {@code skills}). null/빈 문자열이면 {@code monster}
     */
    public String uploadImage(InputStream inputStream, String fileName, String contentType, String s3Folder) {
        try {
            String folder = (s3Folder != null && !s3Folder.isEmpty()) ? s3Folder : MONSTER_FOLDER;
            String s3Key = folder + "/" + fileName;
            
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
            getS3Client().putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
            
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
        return uploadImage(imageBytes, fileName, contentType, MONSTER_FOLDER);
    }

    /**
     * @param s3Folder S3 키 접두어. null/빈 문자열이면 {@code monster}
     * @see #uploadImage(InputStream, String, String, String)
     */
    public String uploadImage(byte[] imageBytes, String fileName, String contentType, String s3Folder) {
        try {
            String folder = (s3Folder != null && !s3Folder.isEmpty()) ? s3Folder : MONSTER_FOLDER;
            String s3Key = folder + "/" + fileName;
            
            // PutObjectRequest 생성 (PublicRead ACL 설정으로 CloudFront에서 읽을 수 있도록)
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PUBLIC_READ) // CloudFront에서 읽을 수 있도록 PublicRead 설정
                    .build();
            
            // S3에 업로드
            getS3Client().putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
            
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
        try {
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
            getS3Client().putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
            
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
        }
    }

    @PreDestroy
    public void close() {
        if (s3Client != null) {
            s3Client.close();
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

