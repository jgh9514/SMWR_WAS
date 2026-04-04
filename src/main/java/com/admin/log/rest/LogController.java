package com.admin.log.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.log.service.LogService;
import com.sysconf.constants.Constant;
import com.sysconf.security.AdminPrivilegeResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Log Management", description = "로그 관리 API")
@RestController
@RequestMapping("/api/v1/common/sm")
public class LogController {
	
	@Autowired
	private LogService service;

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;

	@SuppressWarnings("unchecked")
	private Map<String, Object> getSessUserInfo(HttpServletRequest request) {
		Object attr = request != null ? request.getAttribute("userInfo") : null;
		if (attr instanceof Map) {
			return (Map<String, Object>) attr;
		}
		return null;
	}

	private boolean isAdminUser(HttpServletRequest request) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		return userInfo != null && adminPrivilegeResolver.isAdminUser(userInfo);
	}

	private ResponseEntity<?> requireAdmin(HttpServletRequest request) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		if (userInfo == null || userInfo.get("sess_user_id") == null) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", Constant.FAIL);
			body.put("message", "로그인이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
		}
		if (!isAdminUser(request)) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", Constant.FAIL);
			body.put("message", "관리자 권한이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
		}
		return null;
	}

	@Operation(summary = "로그인 이력 조회", description = "사용자 로그인 이력을 조회합니다.")
	@PostMapping("/login-his")
	public ResponseEntity<?> selectLoginHisList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		List<Map<String, ?>> list = service.selectLoginHisList(param);
		
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "API 이력 조회", description = "API 호출 이력을 조회합니다.")
	@PostMapping("/api-his")
	public ResponseEntity<?> selectApiHisList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		List<Map<String, ?>> list = service.selectApiHisList(param);
		int totalCount = service.selectApiHisCount(param);
		Map<String, Object> result = new HashMap<>();
		result.put("items", list);
		result.put("list", list);
		result.put("totalCount", totalCount);
		result.put("limit", resolveInt(param, "limit", 200));
		result.put("offset", resolveInt(param, "offset", 0));
		return new ResponseEntity<>(result, HttpStatus.OK);
	}
	
	@Operation(summary = "배치 이력 조회", description = "배치 작업 이력을 조회합니다.")
	@PostMapping("/bat-his")
	public ResponseEntity<?> selectBatHisList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		List<Map<String, ?>> list = service.selectBatHisList(param);
		
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "배치 목록 조회", description = "등록된 배치 작업 목록을 조회합니다.")
	@PostMapping("/bat-list")
	public ResponseEntity<?> selectBatchList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		List<Map<String, ?>> list = service.selectBatchList(param);
		
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "배치 이력 상세 조회", description = "특정 배치 작업의 상세 이력을 조회합니다.")
	@PostMapping("/bat-his/{id}")
	public ResponseEntity<?> selectDetailBatHis(@PathVariable String id, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		Map<String, String> resultMap = new HashMap<>();
		String result =  service.selectDetailBatHis(id);
		resultMap.put("result", result);

		return new ResponseEntity<>(resultMap, HttpStatus.OK);
	}

	private int resolveInt(Map<String, Object> param, String key, int defaultValue) {
		if (param == null) {
			return defaultValue;
		}
		Object value = param.get(key);
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		if (value == null) {
			return defaultValue;
		}
		String text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(text);
		} catch (NumberFormatException ignore) {
			return defaultValue;
		}
	}
	
}
