package com.smw.auth.service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import jakarta.mail.AuthenticationFailedException;
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

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	@Autowired(required = false)
	private JavaMailSender mailSender;

	@Autowired(required = false)
	private TemplateEngine templateEngine;

	@Autowired
	private EmailVerificationStore verificationStore;

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

	// 인증 코드 유효 시간 (5분)
	private static final long CODE_EXPIRY_TIME = 5 * 60 * 1000;

	// 인증 완료 유효 시간 (30분)
	private static final long VERIFICATION_VALID_TIME = 30 * 60 * 1000;

	@Override
	public Map<String, Object> sendVerificationCode(String email, String clientIp) {
		Map<String, Object> result = new HashMap<>();
		String normalizedEmail = EmailVerificationStore.normalizeEmail(email);

		if (!isValidEmail(normalizedEmail)) {
			result.put("result", "FAIL");
			result.put("message", "올바른 이메일 형식이 아닙니다.");
			return result;
		}

		try {
			Map<String, Object> p = new HashMap<>();
			p.put("email", normalizedEmail);
			int cnt = userService.countUserByEmail(p);
			if (cnt > 0) {
				result.put("result", "FAIL");
				result.put("message", "이미 등록된 이메일입니다.");
				return result;
			}
		} catch (Exception e) {
			log.warn("이메일 중복 체크 실패(무시하고 진행): {}", e.getMessage());
		}

		long now = System.currentTimeMillis();
		if (!verificationStore.tryConsumeSendRate(normalizedEmail, clientIp, now,
				minIntervalSeconds, maxPerHourPerEmail, maxPerHourPerIp)) {
			result.put("result", "FAIL");
			result.put("message", "인증 코드는 잠시 후 다시 요청해주세요.");
			return result;
		}

		String code = generateVerificationCode();
		long expiresAt = now + CODE_EXPIRY_TIME;
		verificationStore.saveCode(normalizedEmail, code, expiresAt);
		verificationStore.clearFailCount(normalizedEmail);

		log.info("메일 발송 시도 - mailEnabled: {}, mailSender: {}", mailEnabled, mailSender != null);

		if (mailEnabled && mailSender != null) {
			try {
				if (!testMailConnection()) {
					throw new IllegalStateException("SMTP 서버 연결 실패");
				}

				log.info("메일 발송 시작 - To: {}, From: {}", normalizedEmail, fromEmail);
				boolean sendResult = sendEmail(normalizedEmail, code);

				if (sendResult) {
					log.info("이메일 발송 완료: {}", normalizedEmail);
					result.put("result", "SUCCESS");
					result.put("message", "인증 코드가 발송되었습니다.");
				} else {
					throw new IllegalStateException("메일 발송 실패 (반환값 false)");
				}
			} catch (MailException e) {
				log.error("이메일 발송 실패 (MailException) - To: {}, From: {}", normalizedEmail, fromEmail, e);
				logFailureCodeHint(normalizedEmail, code);
				// 발송 실패해도 코드는 TTL까지 유지(늦게 도착한 메일·재시도 대비). 재발송 시 덮어씀.
				result.put("result", "FAIL");
				result.put("message", "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
			} catch (Exception e) {
				log.error("이메일 발송 실패 (Exception) - To: {}, From: {}", normalizedEmail, fromEmail, e);
				logFailureCodeHint(normalizedEmail, code);
				result.put("result", "FAIL");
				result.put("message", "메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.");
			}
		} else {
			log.warn("메일 발송 비활성화 또는 mailSender 없음 (mailEnabled: {}, mailSender: {})",
				mailEnabled, mailSender != null);
			log.info("=== 이메일 인증 코드 (콘솔 출력) ===");
			log.info("이메일: {}", normalizedEmail);
			log.info("인증 코드: {}", code);
			log.info("만료 시간: {}분 후", CODE_EXPIRY_TIME / 60000);
			log.info("================================");

			result.put("result", "SUCCESS");
			result.put("message", "인증 코드가 발송되었습니다. (콘솔 확인)");
			result.put("dev_code", code);
		}

		return result;
	}

	private void logFailureCodeHint(String email, String code) {
		log.warn("=== 이메일 인증 코드 (발송 실패, 서버 로그 전용) ===");
		log.warn("이메일: {}", email);
		log.warn("인증 코드: {}", code);
		log.warn("만료 시간: {}분 후", CODE_EXPIRY_TIME / 60000);
		log.warn("================================");
	}

	@Override
	public Map<String, Object> verifyCode(String email, String code) {
		Map<String, Object> result = new HashMap<>();
		String normalizedEmail = EmailVerificationStore.normalizeEmail(email);
		String normalizedCode = code != null ? code.trim() : "";

		if (normalizedEmail.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "이메일을 입력해주세요.");
			return result;
		}
		if (normalizedCode.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "인증 코드를 입력해주세요.");
			return result;
		}

		int failCnt = verificationStore.getFailCount(normalizedEmail);
		if (failCnt >= maxVerifyFailures) {
			result.put("result", "FAIL");
			result.put("message", "인증 시도 횟수가 초과되었습니다. 인증 코드를 재발송 후 다시 시도해주세요.");
			return result;
		}

		String storedCode = verificationStore.getCodeIfValid(normalizedEmail);
		if (storedCode == null) {
			result.put("result", "FAIL");
			result.put("message", "인증 코드가 발송되지 않았거나 만료되었습니다. 재발송 후 다시 시도해주세요.");
			return result;
		}

		if (!storedCode.equals(normalizedCode)) {
			verificationStore.incrementFailCount(normalizedEmail, CODE_EXPIRY_TIME);
			result.put("result", "FAIL");
			result.put("message", "인증 코드가 일치하지 않습니다.");
			return result;
		}

		verificationStore.removeCode(normalizedEmail);
		verificationStore.clearFailCount(normalizedEmail);
		verificationStore.markVerified(normalizedEmail, VERIFICATION_VALID_TIME);
		result.put("result", "SUCCESS");
		result.put("message", "이메일 인증이 완료되었습니다.");
		return result;
	}

	@Override
	public boolean isEmailVerified(String email) {
		return verificationStore.isVerified(EmailVerificationStore.normalizeEmail(email), VERIFICATION_VALID_TIME);
	}

	@Override
	public void removeVerifiedEmail(String email) {
		verificationStore.removeVerified(EmailVerificationStore.normalizeEmail(email));
	}

	private boolean testMailConnection() {
		if (mailSender == null) {
			log.warn("mailSender가 null입니다.");
			return false;
		}

		if (mailSender instanceof JavaMailSenderImpl mailSenderImpl) {
			try {
				mailSenderImpl.testConnection();
				log.info("SMTP 연결 테스트 성공");
				return true;
			} catch (Exception e) {
				log.error("SMTP 연결 테스트 실패: {}", e.getMessage(), e);
				logSmtpAuthFailureHint(e);
				return false;
			}
		}

		log.warn("JavaMailSenderImpl이 아니어서 연결 테스트를 수행할 수 없습니다.");
		return true;
	}

	private void logSmtpAuthFailureHint(Throwable e) {
		StringBuilder acc = new StringBuilder();
		for (Throwable t = e; t != null; t = t.getCause()) {
			String m = t.getMessage();
			if (m != null) {
				acc.append(m).append(' ');
			}
		}
		String blob = acc.toString();
		boolean authRelated = e instanceof AuthenticationFailedException
				|| blob.contains("535")
				|| blob.contains("BadCredentials")
				|| blob.contains("Authentication failed");
		if (!authRelated) {
			return;
		}
		log.warn(
				"SMTP 인증 실패로 보입니다. Gmail(smtp.gmail.com)인 경우: 일반 비밀번호가 아니라 "
						+ "Google 계정 → 보안 → 2단계 인증 활성화 후 발급한 '앱 비밀번호'(16자, 공백 없이)를 사용하세요. "
						+ "spring.mail.username은 발급한 Gmail 전체 주소, spring.mail.password는 앱 비밀번호, "
						+ "spring.mail.from은 가능하면 동일 계정(또는 Gmail에서 허용한 발신 주소)으로 맞추세요.");
	}

	private boolean sendEmail(String to, String code) {
		if (mailSender == null) {
			throw new IllegalStateException("JavaMailSender가 초기화되지 않았습니다. application.yml의 메일 설정을 확인하세요.");
		}
		if (templateEngine == null) {
			throw new IllegalStateException("TemplateEngine이 초기화되지 않았습니다. Thymeleaf 설정을 확인하세요.");
		}

		try {
			Context context = new Context();
			context.setVariable("code", code);

			String htmlContent = templateEngine.process("email/verification-code", context);

			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

			helper.setFrom(fromEmail);
			helper.setTo(to);
			helper.setSubject("[전투 로그 분석] 이메일 인증 코드");
			helper.setText(htmlContent, true);

			mailSender.send(message);
			return true;
		} catch (MessagingException e) {
			log.error("메일 메시지 생성 중 MessagingException 발생: {}", e.getMessage(), e);
			throw new MailSendException("메일 메시지 생성 실패: " + e.getMessage(), e);
		} catch (MailException e) {
			log.error("메일 전송 중 MailException 발생: {}", e.getMessage(), e);
			throw e;
		} catch (Exception e) {
			log.error("메일 전송 중 예상치 못한 예외 발생: {}", e.getMessage(), e);
			throw new MailSendException("메일 발송 실패: " + e.getMessage(), e);
		}
	}

	private String generateVerificationCode() {
		int code = 100000 + SECURE_RANDOM.nextInt(900000);
		return String.valueOf(code);
	}

	private boolean isValidEmail(String email) {
		if (email == null || email.isEmpty()) {
			return false;
		}
		String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
		return email.matches(emailRegex);
	}
}
