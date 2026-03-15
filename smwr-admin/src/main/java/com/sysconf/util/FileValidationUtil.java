package com.sysconf.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 검증 유틸리티
 * Magic Number를 사용한 실제 파일 타입 검증
 */
public class FileValidationUtil {

	/**
	 * JSON 파일 Magic Number: { (0x7B) 또는 [ (0x5B)로 시작
	 */
	private static final byte[] JSON_MAGIC_NUMBERS = { 0x7B, 0x5B };

	/**
	 * 이미지 파일 Magic Numbers
	 */
	private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] PNG_MAGIC = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
	private static final byte[] GIF_MAGIC_87A = { 0x47, 0x49, 0x46, 0x38, 0x37, 0x61 }; // GIF87a
	private static final byte[] GIF_MAGIC_89A = { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 }; // GIF89a
	private static final byte[] WEBP_MAGIC = { 0x52, 0x49, 0x46, 0x46 }; // RIFF (WebP는 RIFF로 시작)

	/**
	 * 허용된 파일 확장자 목록
	 */
	private static final List<String> ALLOWED_JSON_EXTENSIONS = Arrays.asList("json");
	private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");

	/**
	 * 최대 파일 크기 (바이트)
	 */
	private static final long MAX_JSON_FILE_SIZE = 10 * 1024 * 1024; // 10MB
	private static final long MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024; // 5MB

	/**
	 * 파일 확장자 검증
	 */
	public static boolean isValidExtension(String filename, List<String> allowedExtensions) {
		if (filename == null || filename.isEmpty()) {
			return false;
		}
		String extension = filename.toLowerCase();
		int lastDot = extension.lastIndexOf('.');
		if (lastDot == -1 || lastDot == extension.length() - 1) {
			return false;
		}
		extension = extension.substring(lastDot + 1);
		return allowedExtensions.contains(extension);
	}

	/**
	 * 파일 크기 검증
	 */
	public static boolean isValidFileSize(long fileSize, long maxSize) {
		return fileSize > 0 && fileSize <= maxSize;
	}

	/**
	 * 파일명 위험 문자 검증
	 */
	public static boolean isValidFilename(String filename) {
		if (filename == null || filename.isEmpty()) {
			return false;
		}
		// 위험한 문자: < > : " / \ | ? * 및 제어 문자
		return !filename.matches(".*[<>:\"/\\\\|?*\\x00-\\x1f].*");
	}

	/**
	 * Magic Number로 파일 타입 검증
	 */
	private static boolean matchesMagicNumber(byte[] fileBytes, byte[] magicNumber) {
		if (fileBytes == null || fileBytes.length < magicNumber.length) {
			return false;
		}
		for (int i = 0; i < magicNumber.length; i++) {
			if (fileBytes[i] != magicNumber[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * JSON 파일 검증 (Magic Number 사용)
	 */
	public static boolean isValidJsonFile(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			return false;
		}

		// 파일명 검증
		String filename = file.getOriginalFilename();
		if (!isValidFilename(filename)) {
			return false;
		}
		if (!isValidExtension(filename, ALLOWED_JSON_EXTENSIONS)) {
			return false;
		}

		// 파일 크기 검증
		if (!isValidFileSize(file.getSize(), MAX_JSON_FILE_SIZE)) {
			return false;
		}

		// Magic Number 검증
		byte[] fileBytes = file.getBytes();
		if (fileBytes.length == 0) {
			return false;
		}

		// JSON은 { 또는 [로 시작해야 함
		byte firstByte = fileBytes[0];
		return firstByte == JSON_MAGIC_NUMBERS[0] || firstByte == JSON_MAGIC_NUMBERS[1];
	}

	/**
	 * 이미지 파일 검증 (Magic Number 사용)
	 */
	public static boolean isValidImageFile(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			return false;
		}

		// 파일명 검증
		String filename = file.getOriginalFilename();
		if (!isValidFilename(filename)) {
			return false;
		}
		if (!isValidExtension(filename, ALLOWED_IMAGE_EXTENSIONS)) {
			return false;
		}

		// 파일 크기 검증
		if (!isValidFileSize(file.getSize(), MAX_IMAGE_FILE_SIZE)) {
			return false;
		}

		// Magic Number 검증
		byte[] fileBytes = file.getBytes();
		if (fileBytes.length < 4) {
			return false;
		}

		// JPEG, PNG, GIF, WebP 검증
		return matchesMagicNumber(fileBytes, JPEG_MAGIC) 
			|| matchesMagicNumber(fileBytes, PNG_MAGIC)
			|| matchesMagicNumber(fileBytes, GIF_MAGIC_87A)
			|| matchesMagicNumber(fileBytes, GIF_MAGIC_89A)
			|| matchesMagicNumber(fileBytes, WEBP_MAGIC);
	}

	/**
	 * 파일 검증 결과 클래스
	 */
	public static class ValidationResult {
		private boolean valid;
		private String errorMessage;

		public ValidationResult(boolean valid, String errorMessage) {
			this.valid = valid;
			this.errorMessage = errorMessage;
		}

		public boolean isValid() {
			return valid;
		}

		public String getErrorMessage() {
			return errorMessage;
		}
	}

	/**
	 * JSON 파일 종합 검증
	 */
	public static ValidationResult validateJsonFile(MultipartFile file) {
		try {
			if (file == null || file.isEmpty()) {
				return new ValidationResult(false, "파일이 없습니다.");
			}

			if (!isValidJsonFile(file)) {
				return new ValidationResult(false, "유효하지 않은 JSON 파일입니다.");
			}

			return new ValidationResult(true, null);
		} catch (IOException e) {
			return new ValidationResult(false, "파일 읽기 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	/**
	 * 이미지 파일 종합 검증
	 */
	public static ValidationResult validateImageFile(MultipartFile file) {
		try {
			if (file == null || file.isEmpty()) {
				return new ValidationResult(false, "파일이 없습니다.");
			}

			if (!isValidImageFile(file)) {
				return new ValidationResult(false, "유효하지 않은 이미지 파일입니다.");
			}

			return new ValidationResult(true, null);
		} catch (IOException e) {
			return new ValidationResult(false, "파일 읽기 중 오류가 발생했습니다: " + e.getMessage());
		}
	}
}

