package com.smw.admin.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smw.admin.service.AdminMonsterService;
import com.smw.admin.service.DashboardService;
import com.smw.admin.service.AdminPerfService;
import com.sysconf.constants.Constant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private AdminMonsterService adminMonsterService;
	
	@Autowired
	private AdminPerfService adminPerfService;

	@SuppressWarnings("unchecked")
	private Map<String, Object> getSessUserInfo(HttpServletRequest request) {
		Object attr = request != null ? request.getAttribute("userInfo") : null;
		if (attr instanceof Map) {
			return (Map<String, Object>) attr;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private boolean isAdminUser(HttpServletRequest request) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		if (userInfo == null) return false;
		Object rolesObj = userInfo.get("sess_role");
		if (rolesObj == null) rolesObj = userInfo.get("roles");
		if (!(rolesObj instanceof List)) return false;
		List<?> roles = (List<?>) rolesObj;
		for (Object r : roles) {
			if (!(r instanceof Map)) continue;
			Map<String, ?> role = (Map<String, ?>) r;
			Object roleId = role.get("role_id");
			Object usgYn = role.get("usg_yn");
			boolean enabled = (usgYn == null) || "Y".equalsIgnoreCase(String.valueOf(usgYn));
			if (enabled && Constant.ROLE_ADMIN.equals(String.valueOf(roleId))) return true;
		}
		return false;
	}
	
	private ResponseEntity<?> requireAdmin(HttpServletRequest request) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		if (userInfo == null || userInfo.get("sess_user_id") == null) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "로그인이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
		}
		if (!isAdminUser(request)) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "관리자 권한이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
		}
		return null;
	}

	/**
	 * 대시보드 통계 조회
	 */
	@Operation(summary = "대시보드 통계 조회", description = "관리자 대시보드의 통계 데이터를 조회합니다.")
	@PostMapping("/dashboard/stats")
	public ResponseEntity<?> getDashboardStats(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = dashboardService.getDashboardStats(param);
		return ResponseEntity.ok(result);
	}

	/**
	 * 몬스터 목록 조회 (관리자용)
	 */
	@Operation(summary = "몬스터 목록 조회", description = "관리자 페이지에서 몬스터 목록을 조회합니다.")
	@PostMapping("/monster/list")
	public ResponseEntity<?> getMonsterList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		// 페이지네이션 파라미터 설정
		int page = param.get("page") != null ? Integer.parseInt(param.get("page").toString()) : 1;
		int limit = param.get("limit") != null ? Integer.parseInt(param.get("limit").toString()) : 20;
		int offset = (page - 1) * limit;
		
		param.put("limit", limit);
		param.put("offset", offset);
		
		List<Map<String, Object>> list = adminMonsterService.getMonsterList(param);
		int totalCount = adminMonsterService.getMonsterCount(param);
		
		Map<String, Object> result = new HashMap<>();
		result.put("list", list);
		result.put("totalCount", totalCount);
		result.put("page", page);
		result.put("limit", limit);
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 몬스터 상세 정보 조회
	 */
	@Operation(summary = "몬스터 상세 정보 조회", description = "특정 몬스터의 상세 정보를 조회합니다.")
	@PostMapping("/monster/detail")
	public ResponseEntity<?> getMonsterDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String monsterId = param.get("monster_id") != null ? param.get("monster_id").toString() : null;
		
		if (monsterId == null || monsterId.isEmpty()) {
			Map<String, Object> error = new HashMap<>();
			error.put("error", "monster_id는 필수입니다.");
			return ResponseEntity.badRequest().body(error);
		}
		
		Map<String, Object> result = adminMonsterService.getMonsterDetail(monsterId);
		return ResponseEntity.ok(result);
	}

	/**
	 * 몬스터 정보 수정
	 */
	@Operation(summary = "몬스터 정보 수정", description = "몬스터 정보를 수정합니다.")
	@PostMapping("/monster/update")
	public ResponseEntity<?> updateMonster(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String monsterId = param.get("monster_id") != null ? param.get("monster_id").toString() : null;
		
		if (monsterId == null || monsterId.isEmpty()) {
			Map<String, Object> error = new HashMap<>();
			error.put("error", "monster_id는 필수입니다.");
			return ResponseEntity.badRequest().body(error);
		}
		
		int result = adminMonsterService.updateMonster(param);
		
		Map<String, Object> response = new HashMap<>();
		if (result > 0) {
			response.put("success", true);
			response.put("message", "수정되었습니다.");
		} else {
			response.put("success", false);
			response.put("message", "수정에 실패했습니다.");
		}
		
		return ResponseEntity.ok(response);
	}
	
	/**
	 * DB 성능(쿼리) - 느린 쿼리 TOP 조회
	 */
	@Operation(summary = "느린 쿼리 TOP 조회", description = "pg_stat_statements 기반 느린 쿼리 TOP을 조회합니다.")
	@PostMapping("/perf/slow-queries")
	public ResponseEntity<?> getSlowQueries(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			List<Map<String, Object>> list = adminPerfService.getSlowQueries(param);
			Map<String, Object> result = new HashMap<>();
			result.put("list", list);
			result.put("source", "pg_stat_statements");
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> diag = null;
			try {
				diag = adminPerfService.getDiagnostics(new HashMap<>());
			} catch (Exception ignore) {
				// ignore
			}
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			body.put("message", "슬로우 쿼리 통계를 조회할 수 없습니다. (pg_stat_statements 미설치/권한/버전 확인 필요)\n원인: " + detail);
			body.put("diagnostics", diag);
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * DB 성능(쿼리) - 현재 실행중 쿼리 조회
	 */
	@Operation(summary = "현재 실행중 쿼리 조회", description = "pg_stat_activity 기반 현재 실행중(장시간) 쿼리를 조회합니다.")
	@PostMapping("/perf/running-queries")
	public ResponseEntity<?> getRunningQueries(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			List<Map<String, Object>> list = adminPerfService.getRunningQueries(param);
			Map<String, Object> result = new HashMap<>();
			result.put("list", list);
			result.put("source", "pg_stat_activity");
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "실행중 쿼리 목록을 조회할 수 없습니다.");
			body.put("error", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * DB 성능(쿼리) - pg_stat_statements 진단
	 */
	@Operation(summary = "쿼리 성능 진단", description = "pg_stat_statements 설치/설정 상태를 진단합니다.")
	@PostMapping("/perf/diagnostics")
	public ResponseEntity<?> getPerfDiagnostics(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			Map<String, Object> diag = adminPerfService.getDiagnostics(param);
			Map<String, Object> result = new HashMap<>();
			result.put("result", "SUCCESS");
			result.put("diagnostics", diag);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "진단 정보를 조회할 수 없습니다.");
			body.put("error", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * DB 성능(쿼리) - 누적 통계 리셋
	 */
	@Operation(summary = "쿼리 통계 리셋", description = "pg_stat_statements 누적 통계를 초기화합니다.")
	@PostMapping("/perf/reset")
	public ResponseEntity<?> resetQueryStats(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			adminPerfService.resetQueryStats(param);
			Map<String, Object> result = new HashMap<>();
			result.put("result", "SUCCESS");
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "쿼리 통계를 리셋할 수 없습니다. (권한 확인 필요)");
			body.put("error", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}

