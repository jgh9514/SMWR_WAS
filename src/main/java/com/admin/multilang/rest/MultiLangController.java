package com.admin.multilang.rest;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.multilang.service.MultiLangService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Multi Language", description = "다국어 관리 API")
@RestController
@RequestMapping("/api/v1/sm/mlang")
public class MultiLangController {
	
	@Autowired
	private MultiLangService service;
	
	/**
	 * 다국어 목록 조회
	 */
	@Operation(summary = "다국어 목록 조회", description = "등록된 다국어 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getMlangList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectMlangList(param);
		
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	/**
	 * 다국어 저장 (생성/수정/삭제 통합)
	 */
	@Operation(summary = "다국어 저장", description = "다국어 데이터를 생성, 수정, 삭제합니다.")
	@SuppressWarnings("unchecked")
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<?> saveMlang(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, Object>> insertList = (List<Map<String, Object>>) param.get("insertRow");
		List<Map<String, Object>> updateList = (List<Map<String, Object>>) param.get("updateRow");
		List<Map<String, Object>> deleteList = (List<Map<String, Object>>) param.get("deleteRow");

		for (Map<String, Object> item : deleteList) {
			item.put("del_yn", "Y");

			service.deleteMlang(item);
		}

		for (Map<String, Object> item : insertList) {
			String mlangId = service.selectMlangSeq(item.get("mlang_tp_cd").toString());
			item.put("mlang_id", mlangId);
			service.insertMlang(item);
		}

		for (Map<String, Object> item : updateList) {
			service.updateMlang(item);
		}

		return ResponseEntity.ok(true);
	}
}

