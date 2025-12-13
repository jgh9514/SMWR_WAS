package com.admin.code.rest;

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

import com.admin.code.service.CdService;
import com.sysconf.interceptor.SessionThread;
import com.sysconf.util.StringUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Code Management", description = "코드 관리 API")
@RestController
@RequestMapping("/api/v1/sm/cd")
public class CdController {

	@Autowired
	private CdService service;

	/**
	 * 코드 그룹 목록 조회
	 */
	@Operation(summary = "코드 그룹 목록 조회", description = "코드 그룹 목록을 조회합니다.")
	@PostMapping("/group")
	public ResponseEntity<?> getCdGroupList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectCdGrpList(param);

		return ResponseEntity.ok(list);
	}

	/**
	 * 코드 목록 조회
	 */
	@Operation(summary = "코드 목록 조회", description = "특정 그룹의 코드 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getCdList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectCdList(param);

		return ResponseEntity.ok(list);
	}

	/**
	 * 코드 유틸 목록 조회
	 */
	@Operation(summary = "코드 유틸 목록 조회", description = "유틸리티용 코드 목록을 조회합니다.")
	@PostMapping("/util")
	public ResponseEntity<?> getCdUtilList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectCdUtilList(param);

		return ResponseEntity.ok(list);
	}

	/**
	 * 코드 저장 (생성/수정/삭제 통합)
	 */
	@Operation(summary = "코드 저장", description = "코드 그룹과 코드를 생성, 수정, 삭제합니다.")
	@SuppressWarnings("unchecked")
	@PostMapping("/save")
	public ResponseEntity<?> saveCd(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> userInfo = SessionThread.SESSION_USER_INFO.get();

		// 코드 그룹
		List<Map<String, Object>> insertCdGrpList = (List<Map<String, Object>>) param.get("insertGrpRow");
		List<Map<String, Object>> updateCdGrpList = (List<Map<String, Object>>) param.get("updateGrpRow");
		List<Map<String, Object>> deleteCdGrpList = (List<Map<String, Object>>) param.get("deleteGrpRow");

		// 코드
		List<Map<String, Object>> insertCdList = (List<Map<String, Object>>) param.get("insertRow");
		List<Map<String, Object>> updateCdList = (List<Map<String, Object>>) param.get("updateRow");
		List<Map<String, Object>> deleteCdList = (List<Map<String, Object>>) param.get("deleteRow");

		String key = "";
		for (Map<String, Object> item : deleteCdGrpList) {
			service.deleteCdGrp(item);
		}

		for (Map<String, Object> item : insertCdGrpList) {
			key = service.selectBsnsCdKey(item);
			item.put("cd_grp_no", key);
			item.put("user_id", StringUtil.getParam(userInfo, "sess_user_id"));

			service.insertCdGrp(item);


		}

		for (Map<String, Object> item : updateCdGrpList) {
			item.put("user_id", StringUtil.getParam(userInfo, "sess_user_id"));
			service.updateCdGrp(item);
		}

		for (Map<String, Object> item : deleteCdList) {
			item.put("cd_grp_no", StringUtil.getParam(param, "cd_grp_no"));
			service.deleteCd(item);
		}

		for (Map<String, Object> item : insertCdList) {
			item.put("cd_grp_no", StringUtil.getParam(param, "cd_grp_no"));
			item.put("user_id", StringUtil.getParam(userInfo, "sess_user_id"));

			service.insertCd(item);
		}

		for (Map<String, Object> item : updateCdList) {
			item.put("cd_grp_no", StringUtil.getParam(param, "cd_grp_no"));
			item.put("user_id", StringUtil.getParam(userInfo, "sess_user_id"));

			service.updateCd(item);
		}

		return ResponseEntity.ok(true);
	}
}
