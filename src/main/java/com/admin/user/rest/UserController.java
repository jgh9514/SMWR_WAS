package com.admin.user.rest;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.user.service.UserService;
import com.sysconf.annotation.RequireAdmin;
import com.sysconf.annotation.RequireLogin;
import com.sysconf.constants.Constant;
import com.sysconf.security.AuthCredentialsValidator;
import com.sysconf.security.SHA256;
import com.sysconf.util.CookieUtil;
import com.sysconf.util.StringUtil;
import com.sysconf.util.TokenUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Management", description = "사용자 관리 API")
@RestController
@RequestMapping("/api/v1/sm/user")
public class UserController {

	@Value("${smw.front-url}")
    private String frontUrl;

	@Value("${smw.security.auth.password-min-length:8}")
	private int passwordMinLength;

	@Value("${smw.security.auth.password-max-length:128}")
	private int passwordMaxLength;

	@Autowired
	private UserService service;

	@Autowired
	private CookieUtil cookieUtil;

	@Autowired
	private TokenUtil tokenUtil;

	@SuppressWarnings("unchecked")
	private static Map<String, Object> getSessUserInfo(HttpServletRequest request) {
		Object attr = request != null ? request.getAttribute("userInfo") : null;
		if (attr instanceof Map) {
			return (Map<String, Object>) attr;
		}
		return null;
	}

	private static String sessUserId(Map<String, Object> userInfo) {
		if (userInfo == null) {
			return null;
		}
		Object id = userInfo.get("sess_user_id");
		return id != null ? id.toString() : null;
	}

	/**
	 * 사용자 팝업 목록 조회
	 */
	@RequireAdmin
	@Operation(summary = "사용자 팝업 목록 조회", description = "팝업용 사용자 목록을 조회합니다.")
	@PostMapping("/popup")
	public ResponseEntity<?> getUserPopup(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectUserPopList(param);

		return ResponseEntity.ok(list);
	}

	/**
	 * 사용자 목록 조회 (검색, 필터링 포함)
	 */
	@RequireAdmin
	@Operation(summary = "사용자 목록 조회", description = "검색 및 필터링이 적용된 사용자 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getUserList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectUserList(param);

		return ResponseEntity.ok(list);
	}

	/**
	 * 사용자 상세 조회
	 */
	@RequireAdmin
	@Operation(summary = "사용자 상세 조회", description = "특정 사용자의 상세 정보를 조회합니다.")
	@PostMapping("/detail")
	public ResponseEntity<?> getUserDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, ?> map = service.selectUserDtl(param);

		return ResponseEntity.ok(map);
	}

	/**
	 * 사용자 상세 저장 (생성/수정)
	 */
	@RequireAdmin
	@Operation(summary = "사용자 저장", description = "사용자를 생성하거나 수정합니다.")
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<?> saveUser(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> sess = getSessUserInfo(request);
		if (sess != null) {
			param.put("sess_user_id", sessUserId(sess));
		}
		if("".equals(param.get("user_id")) || null == param.get("user_id")) {
			String key = String.valueOf(service.selectUserId().get("user_id"));
			param.put("user_id", key);
			service.insertUserDtl(param);
		}else {
			service.updateUserDtl(param);
		}

		String result = param.get("user_id").toString();
		return ResponseEntity.ok(result);
	}

	/**
	 * 비밀번호 재설정
	 */
	@RequireAdmin
	@Operation(summary = "비밀번호 재설정", description = "사용자의 비밀번호를 재설정합니다.")
	@PostMapping("/reset-password")
	@Transactional
	public ResponseEntity<?> resetPassword(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) throws NoSuchAlgorithmException {
		Map<String, String> result = new HashMap<>();

		String pwd1 = StringUtil.nvl(param.get("newPassword").toString());
        String pwd2 = StringUtil.nvl(param.get("confirmPassword").toString());
        
        if (!pwd1.equals(pwd2)) {
        	result.put("result", "PWDNOTMATCHED");
			return new ResponseEntity<>(result, HttpStatus.OK);
        }
		String passwordError = AuthCredentialsValidator.validatePassword(pwd1, passwordMinLength, passwordMaxLength);
		if (passwordError != null) {
			result.put("result", "FAIL");
			result.put("message", passwordError);
			return new ResponseEntity<>(result, HttpStatus.OK);
		}
        String password = SHA256.encrypt(pwd1);
		param.put("enc_pwd", password);
		Map<String, Object> sess = getSessUserInfo(request);
		if (sess != null) {
			param.put("sess_user_id", sessUserId(sess));
		}

		service.saveResetPassword(param);

		result.put("result", "SUCCESS");
		return ResponseEntity.ok(result);
	}

	/**
	 * 마이페이지 조회
	 */
	@RequireLogin
	@Operation(summary = "마이페이지 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
	@PostMapping("/mypage")
	public ResponseEntity<?> getMypage(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> forbidden = enforceSelfOnly(param, request);
		if (forbidden != null) {
			return forbidden;
		}
		Map<String, Object> map = new HashMap<>();
		map.put("user", service.selectUserDtl(param));
		return ResponseEntity.ok(map);
	}

	/**
	 * siege_view_scope 업데이트
	 */
	@RequireLogin
	@Operation(summary = "점령전 조회 범위 업데이트", description = "사용자의 점령전 조회 범위를 업데이트합니다.")
	@PostMapping("/update-siege-scope")
	@Transactional
	public ResponseEntity<?> updateSiegeViewScope(
			@RequestBody Map<String, Object> param,
			HttpSession session,
			HttpServletRequest request,
			HttpServletResponse response) {
		ResponseEntity<?> forbidden = enforceSelfOnly(param, request);
		if (forbidden != null) {
			return forbidden;
		}
		Map<String, String> result = new HashMap<>();

		String token = cookieUtil.getCookieValue(request, Constant.LOGIN_TOKEN_NAME);
		service.updateSiegeViewScope(param);
		tokenUtil.evictTokenUserInfoCache(token);

		try {
			Map<String, Object> userInfo = tokenUtil.getToken(token);
			if (userInfo != null) {
				cookieUtil.refreshtoken(request, response, userInfo, Constant.LOGIN_TOKEN_NAME);
			}
		} catch (Exception e) {
			// scope DB 반영은 완료 — JWT 갱신 실패 시 다음 요청에서 재조회
		}

		result.put("result", "SUCCESS");
		return ResponseEntity.ok(result);
	}

	/** 본인 계정만 조회·수정 가능 — user_id 파라미터 위조 방지 */
	private ResponseEntity<?> enforceSelfOnly(Map<String, Object> param, HttpServletRequest request) {
		Map<String, Object> sess = getSessUserInfo(request);
		String sessId = sessUserId(sess);
		if (sessId == null || sessId.isBlank()) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "로그인이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
		}
		Object requested = param.get("user_id");
		if (requested != null && !sessId.equals(String.valueOf(requested).trim())) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "본인 계정만 접근할 수 있습니다.");
			return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
		}
		param.put("user_id", sessId);
		param.put("sess_user_id", sessId);
		return null;
	}

}
