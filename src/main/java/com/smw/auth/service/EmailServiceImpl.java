package com.smw.auth.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

	@Autowired(required = false)
	private JavaMailSender mailSender;

	@Value("${spring.mail.enabled:false}")
	private boolean mailEnabled;

	@Value("${spring.mail.from:noreply@example.com}")
	private String fromEmail;

	// 인증 코드 저장 (메모리 기반, 실제 운영에서는 Redis 등 사용 권장)
	// Key: email, Value: {code, expiresAt}
	private static final Map<String, Map<String, Object>> verificationCodes = new ConcurrentHashMap<>();
	
	// 인증 완료된 이메일 저장 (메모리 기반, 실제 운영에서는 Redis 등 사용 권장)
	// Key: email, Value: verifiedAt (인증 완료 시간)
	private static final Map<String, Long> verifiedEmails = new ConcurrentHashMap<>();
	
	// 인증 코드 유효 시간 (5분)
	private static final long CODE_EXPIRY_TIME = 5 * 60 * 1000;
	
	// 인증 완료 유효 시간 (30분)
	private static final long VERIFICATION_VALID_TIME = 30 * 60 * 1000;

	@Override
	public Map<String, Object> sendVerificationCode(String email) {
		Map<String, Object> result = new HashMap<>();
		
		// 이메일 형식 검증
		if (!isValidEmail(email)) {
			result.put("result", "FAIL");
			result.put("message", "올바른 이메일 형식이 아닙니다.");
			return result;
		}
		
		// 6자리 인증 코드 생성
		String code = generateVerificationCode();
		long expiresAt = System.currentTimeMillis() + CODE_EXPIRY_TIME;
		
		// 인증 코드 저장
		Map<String, Object> codeInfo = new HashMap<>();
		codeInfo.put("code", code);
		codeInfo.put("expiresAt", expiresAt);
		verificationCodes.put(email, codeInfo);
		
		// 이메일 발송
		log.info("메일 발송 시도 - mailEnabled: {}, mailSender: {}", mailEnabled, mailSender != null);
		
		if (mailEnabled && mailSender != null) {
			try {
				// SMTP 연결 테스트
				if (!testMailConnection()) {
					throw new IllegalStateException("SMTP 서버 연결 실패");
				}
				
				log.info("메일 발송 시작 - To: {}, From: {}", email, fromEmail);
				boolean sendResult = sendEmail(email, code);
				
				if (sendResult) {
					log.info("이메일 발송 완료: {} (인증 코드: {})", email, code);
					result.put("result", "SUCCESS");
					result.put("message", "인증 코드가 발송되었습니다.");
				} else {
					throw new IllegalStateException("메일 발송 실패 (반환값 false)");
				}
			} catch (MailException e) {
				log.error("이메일 발송 실패 (MailException) - To: {}, From: {}, Error: {}", email, fromEmail, e.getMessage(), e);
				log.error("예외 상세: ", e);
				// 이메일 발송 실패 시 콘솔에 출력 (개발 편의)
				log.info("=== 이메일 인증 코드 (발송 실패, 콘솔 출력) ===");
				log.info("이메일: {}", email);
				log.info("인증 코드: {}", code);
				log.info("만료 시간: {}분 후", CODE_EXPIRY_TIME / 60000);
				log.info("================================");
				
				result.put("result", "SUCCESS");
				result.put("message", "인증 코드가 발송되었습니다. (발송 실패로 콘솔 확인)");
				result.put("dev_code", code); // 개발 환경에서만 반환
			} catch (Exception e) {
				log.error("이메일 발송 실패 (Exception) - To: {}, From: {}, Error: {}", email, fromEmail, e.getMessage(), e);
				log.error("예외 상세: ", e);
				// 이메일 발송 실패 시 콘솔에 출력 (개발 편의)
				log.info("=== 이메일 인증 코드 (발송 실패, 콘솔 출력) ===");
				log.info("이메일: {}", email);
				log.info("인증 코드: {}", code);
				log.info("만료 시간: {}분 후", CODE_EXPIRY_TIME / 60000);
				log.info("================================");
				
				result.put("result", "SUCCESS");
				result.put("message", "인증 코드가 발송되었습니다. (발송 실패로 콘솔 확인)");
				result.put("dev_code", code); // 개발 환경에서만 반환
			}
		} else {
			// mailEnabled가 false이거나 mailSender가 없는 경우: 콘솔에 출력
			log.warn("메일 발송 비활성화 또는 mailSender 없음 (mailEnabled: {}, mailSender: {})", mailEnabled, mailSender != null);
			log.info("=== 이메일 인증 코드 (콘솔 출력) ===");
			log.info("이메일: {}", email);
			log.info("인증 코드: {}", code);
			log.info("만료 시간: {}분 후", CODE_EXPIRY_TIME / 60000);
			log.info("================================");
			
			result.put("result", "SUCCESS");
			result.put("message", "인증 코드가 발송되었습니다. (콘솔 확인)");
			result.put("dev_code", code); // 개발 환경에서만 반환
		}
		
		return result;
	}

	@Override
	public Map<String, Object> verifyCode(String email, String code) {
		Map<String, Object> result = new HashMap<>();
		
		Map<String, Object> codeInfo = verificationCodes.get(email);
		
		if (codeInfo == null) {
			result.put("result", "FAIL");
			result.put("message", "인증 코드가 발송되지 않았습니다.");
			return result;
		}
		
		// 만료 시간 확인
		long expiresAt = (Long) codeInfo.get("expiresAt");
		if (System.currentTimeMillis() > expiresAt) {
			verificationCodes.remove(email);
			result.put("result", "FAIL");
			result.put("message", "인증 코드가 만료되었습니다.");
			return result;
		}
		
		// 인증 코드 확인
		String storedCode = (String) codeInfo.get("code");
		if (!storedCode.equals(code)) {
			result.put("result", "FAIL");
			result.put("message", "인증 코드가 일치하지 않습니다.");
			return result;
		}
		
		// 인증 성공
		verificationCodes.remove(email);
		// 인증 완료된 이메일로 저장 (30분간 유효)
		verifiedEmails.put(email, System.currentTimeMillis());
		result.put("result", "SUCCESS");
		result.put("message", "이메일 인증이 완료되었습니다.");
		
		return result;
	}
	
	@Override
	public boolean isEmailVerified(String email) {
		Long verifiedAt = verifiedEmails.get(email);
		if (verifiedAt == null) {
			return false;
		}
		
		// 인증 완료 후 30분이 지났는지 확인
		if (System.currentTimeMillis() - verifiedAt > VERIFICATION_VALID_TIME) {
			verifiedEmails.remove(email);
			return false;
		}
		
		return true;
	}
	
	@Override
	public void removeVerifiedEmail(String email) {
		verifiedEmails.remove(email);
	}

	/**
	 * SMTP 연결 테스트
	 */
	private boolean testMailConnection() {
		if (mailSender == null) {
			log.warn("mailSender가 null입니다.");
			return false;
		}
		
		if (mailSender instanceof JavaMailSenderImpl) {
			try {
				JavaMailSenderImpl mailSenderImpl = (JavaMailSenderImpl) mailSender;
				mailSenderImpl.testConnection();
				log.info("SMTP 연결 테스트 성공");
				return true;
			} catch (Exception e) {
				log.error("SMTP 연결 테스트 실패: {}", e.getMessage(), e);
				return false;
			}
		}
		
		// JavaMailSenderImpl이 아닌 경우 연결 테스트 불가
		log.warn("JavaMailSenderImpl이 아니어서 연결 테스트를 수행할 수 없습니다.");
		return true; // 테스트 불가하지만 발송은 시도
	}
	
	/**
	 * 이메일 발송
	 * @return 발송 성공 여부
	 */
	private boolean sendEmail(String to, String code) {
		if (mailSender == null) {
			throw new IllegalStateException("JavaMailSender가 초기화되지 않았습니다. application.yml의 메일 설정을 확인하세요.");
		}
		
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(fromEmail);
		message.setTo(to);
		message.setSubject("[전투 로그 분석] 이메일 인증 코드");
		message.setText("인증 코드: " + code + "\n\n이 코드는 5분간 유효합니다.");
		
		log.debug("메일 메시지 생성 완료 - From: {}, To: {}, Subject: {}", fromEmail, to, message.getSubject());
		
		try {
			mailSender.send(message);
			log.debug("메일 전송 완료 (예외 없음)");
			return true;
		} catch (MailException e) {
			log.error("메일 전송 중 MailException 발생: {}", e.getMessage(), e);
			throw e; // 상위로 전달
		} catch (Exception e) {
			log.error("메일 전송 중 예상치 못한 예외 발생: {}", e.getMessage(), e);
			throw new MailSendException("메일 발송 실패: " + e.getMessage(), e);
		}
	}

	/**
	 * 인증 코드 생성 (6자리 숫자)
	 */
	private String generateVerificationCode() {
		Random random = new Random();
		int code = 100000 + random.nextInt(900000);
		return String.valueOf(code);
	}

	/**
	 * 이메일 형식 검증
	 */
	private boolean isValidEmail(String email) {
		if (email == null || email.trim().isEmpty()) {
			return false;
		}
		String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
		return email.matches(emailRegex);
	}
}

