package com.admin.user.rest;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.role.service.RoleService;
import com.admin.user.service.UserService;
import com.sysconf.constants.Constant;
import com.sysconf.security.SHA256;
import com.sysconf.util.StringUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Management", description = "사용자 관리 API")
@RestController
@RequestMapping("/api/v1/sm/user")
public class UserController {

	@Value("${smw.front-url}")
    private String frontUrl;

	@Autowired
	private UserService service;

	@Autowired
	private RoleService roleservice;

	/**
	 * 사용자 팝업 목록 조회
	 */
	@Operation(summary = "사용자 팝업 목록 조회", description = "팝업용 사용자 목록을 조회합니다.")
	@PostMapping("/popup")
	public ResponseEntity<?> getUserPopup(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectUserPopList(param);

		return ResponseEntity.ok(list);
	}

	/**
	 * 사용자 목록 조회 (검색, 필터링 포함)
	 */
	@Operation(summary = "사용자 목록 조회", description = "검색 및 필터링이 적용된 사용자 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getUserList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectUserList(param);

		return ResponseEntity.ok(list);
	}

	/**
	 * 사용자 상세 조회
	 */
	@Operation(summary = "사용자 상세 조회", description = "특정 사용자의 상세 정보를 조회합니다.")
	@PostMapping("/detail")
	public ResponseEntity<?> getUserDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, ?> map = service.selectUserDtl(param);

		return ResponseEntity.ok(map);
	}

	/**
	 * 사용자 상세 저장 (생성/수정)
	 */
	@Operation(summary = "사용자 저장", description = "사용자를 생성하거나 수정합니다.")
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<?> saveUser(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		if("".equals(param.get("user_id")) || null == param.get("user_id")) {
			String key = String.valueOf(service.selectUserId().get("user_id"));
			param.put("user_id", key);
			param.put("role_id", Constant.ROLE_GENERAL);
			service.insertUserDtl(param);
			roleservice.insertUserRole(param);
		}else {
			service.updateUserDtl(param);
		}

		String result = param.get("user_id").toString();
		return ResponseEntity.ok(result);
	}

	/**
	 * 비밀번호 재설정
	 */
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
        } else {
            String password = SHA256.encrypt(pwd1);
			param.put("enc_pwd", password);
        }

		service.saveResetPassword(param);

		result.put("result", "SUCCESS");
		return ResponseEntity.ok(result);
	}

	/**
	 * 마이페이지 조회
	 */
	@Operation(summary = "마이페이지 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
	@PostMapping("/mypage")
	public ResponseEntity<?> getMypage(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> map = new HashMap<>();
		map.put("user", service.selectUserDtl(param));
		return ResponseEntity.ok(map);
	}

	/**
	 * siege_view_scope 업데이트
	 */
	@Operation(summary = "점령전 조회 범위 업데이트", description = "사용자의 점령전 조회 범위를 업데이트합니다.")
	@PostMapping("/update-siege-scope")
	@Transactional
	public ResponseEntity<?> updateSiegeViewScope(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, String> result = new HashMap<>();

		service.updateSiegeViewScope(param);
		
		result.put("result", "SUCCESS");
		return ResponseEntity.ok(result);
	}

}
