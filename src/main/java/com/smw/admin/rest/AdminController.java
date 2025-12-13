package com.smw.admin.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smw.admin.service.AdminMonsterService;
import com.smw.admin.service.DashboardService;

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
}

