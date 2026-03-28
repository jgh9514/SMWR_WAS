package com.smw.auth.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.admin.user.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class EmailServiceImpl implements EmailService {

	@Autowired(required = false)
	private JavaMailSender mailSender;

	@Autowired(required = false)
	private TemplateEngine templateEngine;

	@Value("${spring.mail.enabled:false}")
	private boolean mailEnabled;

	@Value("${spring.mail.from:noreply@example.com}")
	private String fromEmail;

	@Value("${smw.email.verification.min-interval-seconds:30}")
	private int minIntervalSeconds;

	@Value("${smw.email.verification.max-per-hour-email:5}")
	private int maxPerHourPerEmail;

	@Value("${smw.email.verification.max-per-hour-ip:20}")
	private int maxPerHourPerIp;

	@Value("${smw.email.verification.max-verify-failures:10}")
	private int maxVerifyFailures;

	@Autowired
	private UserService userService;

	// 인증 코드 저장 (메모리 기반)
	// Key: email, Value: {code, expiresAt}
	private static final Map<String, Map<String, Object>> verificationCodes = new ConcurrentHashMap<>();

	// 발송 rate limit 상태 (메모리 기반)
	private static final Map<String, RateState> rateByEmail = new ConcurrentHashMap<>();
	private static final Map<String, RateState> rateByIp = new ConcurrentHashMap<>();

	// 인증 코드 검증 실패 횟수
	private static final Map<String, Integer> verifyFailCounts = new ConcurrentHashMap<>();

	private static class RateState {
		volatile long windowStartMs = 0;
		volatile int windowCount = 0;
		volatile long lastSentAtMs = 0;
	}
	
	// 인증 완료된 이메일 저장 (메모리 기반)
	// Key: email, Value: verifiedAt (인증 완료 시간)
	private static final Map<String, Long> verifiedEmails = new ConcurrentHashMap<>();
	
	// 인증 코드 유효 시간 (5분)
	private static final long CODE_EXPIRY_TIME = 5 * 60 * 1000;
	
	// 인증 완료 유효 시간 (30분)
	private static final long VERIFICATION_VALID_TIME = 30 * 60 * 1000;

	@Override
	public Map<String, Object> sendVerificationCode(String email, String clientIp) {
		Map<String, Object> result = new HashMap<>();
		
		// 이메일 형식 검증
		if (!isValidEmail(email)) {
			result.put("result", "FAIL");
			result.put("message", "올바른 이메일 형식이 아닙니다.");
			return result;
		}

		// 이미 등록된 이메일이면 발송 차단 (중복 가입 방지)
		try {
			Map<String, Object> p = new HashMap<>();
			p.put("email", email);
			int cnt = userService.countUserByEmail(p);
			if (cnt > 0) {
				result.put("result", "FAIL");
				result.put("message", "이미 등록된 이메일입니다.");
				return result;
			}
		} catch (Exception e) {
			// 이메일 컬럼/쿼리 환경에 따라 실패할 수 있어, 실패 시에는 발송만 계속 진행 (로그만 남김)
			log.warn("이메일 중복 체크 실패(무시하고 진행): {}", e.getMessage());
		}

		// 발송 제한(이메일/아이피)
		String emailKey = email.toLowerCase();
		long now = System.currentTimeMillis();

		if (!consumeRate(rateByEmail, emailKey, now, minIntervalSeconds, maxPerHourPerEmail)) {
			result.put("result", "FAIL");
			result.put("message", "인증 코드는 잠시 후 다시 요청해주세요.");
			return result;
		}
		if (clientIp != null && !clientIp.trim().isEmpty()) {
			if (!consumeRate(rateByIp, clientIp.trim(), now, 0, maxPerHourPerIp)) {
				result.put("result", "FAIL");
				result.put("message", "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
				return result;
			}
		}
		
		// 6자리 인증 코드 생성
		String code = generateVerificationCode();
		long expiresAt = System.currentTimeMillis() + CODE_EXPIRY_TIME;
		
		// 인증 코드 저장
		Map<String, Object> codeInfo = new HashMap<>();
		codeInfo.put("code", code);
		codeInfo.put("expiresAt", expiresAt);
		verificationCodes.put(email, codeInfo);
		// 발송 성공/실패와 무관하게 실패 카운터는 초기화 (새 코드 발급 기준)
		verifyFailCounts.remove(emailKey);
		
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
				logFailureCodeHint(email, code);
				verificationCodes.remove(email);
				result.put("result", "FAIL");
				result.put("message", "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
			} catch (Exception e) {
				log.error("이메일 발송 실패 (Exception) - To: {}, From: {}, Error: {}", email, fromEmail, e.getMessage(), e);
				log.error("예외 상세: ", e);
				logFailureCodeHint(email, code);
				verificationCodes.remove(email);
				result.put("result", "FAIL");
				result.put("message", "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
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

	/** 로컬 개발 시 WAS 로그에서만 코드 확인용 (응답 본문에는 포함하지 않음) */
	private void logFailureCodeHint(String email, String code) {
		log.warn("=== 이메일 인증 코드 (발송 실패, 서버 로그 전용) ===");
		log.warn("이메일: {}", email);
		log.warn("인증 코드: {}", code);
		log.warn("만료 시간: {}분 후", CODE_EXPIRY_TIME / 60000);
		log.warn("================================");
	}

	/**
	 * 이메일/아이피 기준 간단 rate limit
	 * - minIntervalSeconds: 마지막 발송 이후 최소 대기(초). 0이면 미적용
	 * - maxPerHour: 1시간 윈도우 내 최대 요청 수
	 */
	private boolean consumeRate(Map<String, RateState> store, String key, long now, int minIntervalSeconds, int maxPerHour) {
		final RateState state = store.computeIfAbsent(key, (k) -> new RateState());
		synchronized (state) {
			if (minIntervalSeconds > 0 && state.lastSentAtMs > 0) {
				long minIntervalMs = minIntervalSeconds * 1000L;
				if (now - state.lastSentAtMs < minIntervalMs) {
					return false;
				}
			}

			// 1시간 윈도우
			if (state.windowStartMs == 0 || now - state.windowStartMs >= 60 * 60 * 1000L) {
				state.windowStartMs = now;
				state.windowCount = 0;
			}

			if (maxPerHour > 0 && state.windowCount >= maxPerHour) {
				return false;
			}

			state.windowCount += 1;
			state.lastSentAtMs = now;
			return true;
		}
	}

	@Override
	public Map<String, Object> verifyCode(String email, String code) {
		Map<String, Object> result = new HashMap<>();
		String emailKey = email != null ? email.toLowerCase() : "";

		Integer failCnt = verifyFailCounts.get(emailKey);
		if (failCnt != null && failCnt >= maxVerifyFailures) {
			result.put("result", "FAIL");
			result.put("message", "인증 시도 횟수가 초과되었습니다. 인증 코드를 재발송 후 다시 시도해주세요.");
			return result;
		}
		
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
			verifyFailCounts.put(emailKey, (failCnt == null ? 1 : failCnt + 1));
			result.put("result", "FAIL");
			result.put("message", "인증 코드가 일치하지 않습니다.");
			return result;
		}
		
		// 인증 성공
		verificationCodes.remove(email);
		verifyFailCounts.remove(emailKey);
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
	 * 이메일 발송 (Thymeleaf 템플릿 사용)
	 * @return 발송 성공 여부
	 */
	private boolean sendEmail(String to, String code) {
		if (mailSender == null) {
			throw new IllegalStateException("JavaMailSender가 초기화되지 않았습니다. application.yml의 메일 설정을 확인하세요.");
		}
		
		if (templateEngine == null) {
			throw new IllegalStateException("TemplateEngine이 초기화되지 않았습니다. Thymeleaf 설정을 확인하세요.");
		}
		
		try {
			// Thymeleaf 컨텍스트 생성 및 변수 설정
			Context context = new Context();
			context.setVariable("code", code);
			
			// HTML 템플릿 처리
			String htmlContent = templateEngine.process("email/verification-code", context);
			
			// MimeMessage 생성
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			
			helper.setFrom(fromEmail);
			helper.setTo(to);
			helper.setSubject("[전투 로그 분석] 이메일 인증 코드");
			helper.setText(htmlContent, true); // true = HTML 형식
			
			log.debug("메일 메시지 생성 완료 - From: {}, To: {}, Subject: {}", fromEmail, to, helper.getMimeMessage().getSubject());
			
			// 메일 발송
			mailSender.send(message);
			log.debug("메일 전송 완료 (예외 없음)");
			return true;
		} catch (MessagingException e) {
			log.error("메일 메시지 생성 중 MessagingException 발생: {}", e.getMessage(), e);
			throw new MailSendException("메일 메시지 생성 실패: " + e.getMessage(), e);
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

