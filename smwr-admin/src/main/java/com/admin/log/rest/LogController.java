package com.admin.log.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.log.service.LogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Log Management", description = "로그 관리 API")
@RestController
@RequestMapping("/api/v1/common/sm")
public class LogController {
	
	@Autowired
	private LogService service;

	@Operation(summary = "로그인 이력 조회", description = "사용자 로그인 이력을 조회합니다.")
	@PostMapping("/login-his")
	public ResponseEntity<?> selectLoginHisList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectLoginHisList(param);
		
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "API 이력 조회", description = "API 호출 이력을 조회합니다.")
	@PostMapping("/api-his")
	public ResponseEntity<?> selectApiHisList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectApiHisList(param);
		
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "배치 이력 조회", description = "배치 작업 이력을 조회합니다.")
	@PostMapping("/bat-his")
	public ResponseEntity<?> selectBatHisList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectBatHisList(param);
		
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "배치 목록 조회", description = "등록된 배치 작업 목록을 조회합니다.")
	@PostMapping("/bat-list")
	public ResponseEntity<?> selectBatchList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectBatchList(param);
		
		return new ResponseEntity<>(list, HttpStatus.OK);
	}
	
	@Operation(summary = "배치 이력 상세 조회", description = "특정 배치 작업의 상세 이력을 조회합니다.")
	@PostMapping("/bat-his/{id}")
	public ResponseEntity<?> selectDetailBatHis(@PathVariable String id) {
		Map<String, String> resultMap = new HashMap<>();
		String result =  service.selectDetailBatHis(id);
		resultMap.put("result", result);

		return new ResponseEntity<>(resultMap, HttpStatus.OK);
	}
	
}
