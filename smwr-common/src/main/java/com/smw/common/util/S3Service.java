package com.smw.common.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
public class S3Service {

	private static final String BUCKET_NAME = "summonerswar-community";
	private static final String CLOUDFRONT_URL = "https://dyjduzi8vf2k4.cloudfront.net";
	private static final String MONSTER_FOLDER = "monster";
	private static final String FILES_FOLDER = "files";

	private S3Client createS3Client() {
		return S3Client.builder()
				.region(Region.AP_SOUTHEAST_2)
				.credentialsProvider(DefaultCredentialsProvider.create())
				.build();
	}

	public String uploadImage(InputStream inputStream, String fileName, String contentType) {
		try (S3Client s3Client = createS3Client()) {
			String s3Key = MONSTER_FOLDER + "/" + fileName;
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
					.bucket(BUCKET_NAME)
					.key(s3Key)
					.contentType(contentType)
					.acl(ObjectCannedACL.PUBLIC_READ)
					.build();
			byte[] imageBytes = inputStreamToByteArray(inputStream);
			s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
			log.info("S3 업로드 완료: {}/{}", BUCKET_NAME, s3Key);
			return CLOUDFRONT_URL + "/" + s3Key;
		} catch (S3Exception e) {
			log.error("S3 업로드 실패: {}", e.getMessage(), e);
			throw new RuntimeException("S3 업로드 실패: " + e.getMessage(), e);
		} catch (Exception e) {
			log.error("이미지 업로드 중 오류 발생: {}", fileName, e);
			throw new RuntimeException("이미지 업로드 실패: " + e.getMessage(), e);
		}
	}

	public String uploadImage(byte[] imageBytes, String fileName, String contentType) {
		try (S3Client s3Client = createS3Client()) {
			String s3Key = MONSTER_FOLDER + "/" + fileName;
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
					.bucket(BUCKET_NAME)
					.key(s3Key)
					.contentType(contentType)
					.acl(ObjectCannedACL.PUBLIC_READ)
					.build();
			s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
			log.info("S3 업로드 완료: {}/{}", BUCKET_NAME, s3Key);
			return CLOUDFRONT_URL + "/" + s3Key;
		} catch (S3Exception e) {
			log.error("S3 업로드 실패: {}", e.getMessage(), e);
			throw new RuntimeException("S3 업로드 실패: " + e.getMessage(), e);
		} catch (Exception e) {
			log.error("이미지 업로드 중 오류 발생: {}", fileName, e);
			throw new RuntimeException("이미지 업로드 실패: " + e.getMessage(), e);
		}
	}

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

	private String getContentTypeFromFileName(String fileName) {
		String lower = fileName.toLowerCase();
		if (lower.endsWith(".png")) return "image/png";
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
		if (lower.endsWith(".gif")) return "image/gif";
		if (lower.endsWith(".webp")) return "image/webp";
		return "image/jpeg";
	}

	public String uploadImage(byte[] imageBytes, String fileName) {
		return uploadImage(imageBytes, fileName, getContentTypeFromFileName(fileName));
	}

	public String uploadImage(InputStream inputStream, String fileName) {
		return uploadImage(inputStream, fileName, getContentTypeFromFileName(fileName));
	}

	public String uploadFile(byte[] fileBytes, String fileName, String contentType, String folder) {
		try (S3Client s3Client = createS3Client()) {
			if (folder == null || folder.isEmpty()) folder = FILES_FOLDER;
			String s3Key = folder + "/" + fileName;
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
					.bucket(BUCKET_NAME)
					.key(s3Key)
					.contentType(contentType)
					.acl(ObjectCannedACL.PUBLIC_READ)
					.build();
			s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
			log.info("S3 파일 업로드 완료: {}/{}", BUCKET_NAME, s3Key);
			return CLOUDFRONT_URL + "/" + s3Key;
		} catch (S3Exception e) {
			log.error("S3 파일 업로드 실패: {}", e.getMessage(), e);
			throw new RuntimeException("S3 파일 업로드 실패: " + e.getMessage(), e);
		} catch (Exception e) {
			log.error("파일 업로드 중 오류 발생: {}", fileName, e);
			throw new RuntimeException("파일 업로드 실패: " + e.getMessage(), e);
		}
	}

	public String uploadFile(byte[] fileBytes, String fileName, String contentType) {
		return uploadFile(fileBytes, fileName, contentType, null);
	}

	public String getContentType(String fileName) {
		String lower = fileName.toLowerCase();
		if (lower.endsWith(".png")) return "image/png";
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
		if (lower.endsWith(".gif")) return "image/gif";
		if (lower.endsWith(".webp")) return "image/webp";
		if (lower.endsWith(".json")) return "application/json";
		if (lower.endsWith(".pdf")) return "application/pdf";
		return "application/octet-stream";
	}
}
