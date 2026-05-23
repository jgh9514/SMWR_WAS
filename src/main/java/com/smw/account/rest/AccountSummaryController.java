package com.smw.account.rest;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smw.account.service.AccountSummaryService;
import com.sysconf.annotation.RequireLogin;
import com.sysconf.util.FileValidationUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Summoners War Account Summary", description = "SWEX 계정 요약(룬/몬스터) 업로드 및 조회 API")
@RequireLogin
@RestController
@RequestMapping("/api/v1/summonerswar/account-summary")
public class AccountSummaryController {

	@Autowired
	private AccountSummaryService service;
	
	@SuppressWarnings("unchecked")
	private String getSessUserId(HttpServletRequest request) {
		Object attr = request.getAttribute("userInfo");
		if (attr instanceof Map) {
			Map<String, Object> userInfo = (Map<String, Object>) attr;
			Object v = userInfo.get("sess_user_id");
			return v != null ? v.toString() : null;
		}
		return null;
	}

	/**
	 * SWEX JSON 업로드 & 저장
	 */
	@Operation(summary = "SWEX JSON 업로드", description = "SWEX로 추출한 계정 JSON을 업로드하여 DB에 저장하고 요약 정보를 반환합니다.")
	@PostMapping(value = "/upload", consumes = "multipart/form-data")
	public ResponseEntity<?> upload(@RequestParam("json_file") MultipartFile jsonFile, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}

		// 파일 검증
		FileValidationUtil.ValidationResult jsonValidation = FileValidationUtil.validateJsonFile(jsonFile);
		if (!jsonValidation.isValid()) {
			result.put("result", "FAIL");
			result.put("message", jsonValidation.getErrorMessage());
			return ResponseEntity.ok(result);
		}

		try {
			Map<String, Object> data = service.uploadAndSave(jsonFile, sessUserId);
			result.put("result", "SUCCESS");
			result.put("data", data);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			log.error("[AccountSummary] 업로드 처리 실패 - fileName={}, size={}", 
					jsonFile != null ? jsonFile.getOriginalFilename() : null,
					jsonFile != null ? jsonFile.getSize() : null,
					e);
			result.put("result", "FAIL");
			result.put("message", "업로드 처리 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.ok(result);
		}
	}

	/**
	 * 내 최신 임포트 요약
	 */
	@Operation(summary = "최신 임포트 요약 조회", description = "로그인 사용자의 최신 SWEX 임포트 요약을 조회합니다.")
	@PostMapping("/latest")
	public ResponseEntity<?> latest(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId != null && !sessUserId.isEmpty()) {
			safeParam.put("sess_user_id", sessUserId);
		}
		Map<String, Object> data = service.selectLatestImport(safeParam);
		return ResponseEntity.ok(data);
	}

	/**
	 * 내 임포트 목록
	 */
	@Operation(summary = "임포트 목록 조회", description = "로그인 사용자의 SWEX 임포트 목록을 조회합니다.")
	@PostMapping("/import-list")
	public ResponseEntity<?> importList(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId != null && !sessUserId.isEmpty()) {
			safeParam.put("sess_user_id", sessUserId);
		}
		return ResponseEntity.ok(service.selectImportList(safeParam));
	}

	/**
	 * 임포트 상세(요약)
	 */
	@Operation(summary = "임포트 상세 조회", description = "특정 임포트(import_id)의 요약 정보를 조회합니다.")
	@PostMapping("/import-detail")
	public ResponseEntity<?> importDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId != null && !sessUserId.isEmpty()) {
			safeParam.put("sess_user_id", sessUserId);
		}
		return ResponseEntity.ok(service.selectImportDetail(safeParam));
	}

	/**
	 * 몬스터 목록 조회 (임포트 기준)
	 */
	@Operation(summary = "몬스터 목록 조회", description = "특정 임포트의 몬스터 목록을 조회합니다. import_id가 없으면 최신 임포트 기준으로 조회합니다.")
	@PostMapping("/monster-list")
	public ResponseEntity<?> monsterList(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId != null && !sessUserId.isEmpty()) {
			safeParam.put("sess_user_id", sessUserId);
		}
		return ResponseEntity.ok(service.selectMonsterList(safeParam));
	}

	/**
	 * 몬스터 도감(전체) + 보유 카운트 (임포트 기준)
	 */
	@Operation(summary = "몬스터 도감 조회", description = "전체 몬스터 목록을 보유 카운트와 함께 조회합니다. owned_count=0인 몬스터는 미보유입니다.")
	@PostMapping("/monster-catalog")
	public ResponseEntity<?> monsterCatalog(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId != null && !sessUserId.isEmpty()) {
			safeParam.put("sess_user_id", sessUserId);
		}
		return ResponseEntity.ok(service.selectMonsterCatalog(safeParam));
	}

	/**
	 * 룬 목록 조회 (임포트 기준)
	 */
	@Operation(summary = "룬 목록 조회", description = "특정 임포트의 룬 목록을 조회합니다. import_id가 없으면 최신 임포트 기준으로 조회합니다.")
	@PostMapping("/rune-list")
	public ResponseEntity<?> runeList(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId != null && !sessUserId.isEmpty()) {
			safeParam.put("sess_user_id", sessUserId);
		}
		return ResponseEntity.ok(service.selectRuneList(safeParam));
	}

	/**
	 * 룬 속도 요약 (신속+잡룬 / 신속+의지 최고 속도)
	 */
	@Operation(summary = "룬 속도 요약", description = "임포트(import_id) 기준 신속 4 + 잡룬 2 / 신속 4 + 의지 2 조합의 최고 공격속도를 계산합니다.")
	@PostMapping("/rune-score-summary")
	public ResponseEntity<?> runeScoreSummary(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId != null && !sessUserId.isEmpty()) {
			safeParam.put("sess_user_id", sessUserId);
		}
		return ResponseEntity.ok(service.selectRuneScoreSummary(safeParam));
	}
}


