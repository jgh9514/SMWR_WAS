package com.cf.community.rest;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cf.community.service.InquiryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Inquiry Management", description = "1대1 문의 관리 API")
@RestController
@RequestMapping("/api/v1/community/inquiry")
public class InquiryController {

	@Autowired
	private InquiryService service;

	/**
	 * 1대1문의 목록 조회
	 */
	@Operation(summary = "1대1문의 목록 조회", description = "1대1문의 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getInquiryList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.getInquiryList(param));
	}

	/**
	 * 1대1문의 상세 조회
	 */
	@Operation(summary = "1대1문의 상세 조회", description = "1대1문의 상세 정보를 조회합니다.")
	@PostMapping("/detail")
	public ResponseEntity<?> getInquiryDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.getInquiryDetail(param));
	}

	/**
	 * 1대1문의 등록
	 */
	@Operation(summary = "1대1문의 등록", description = "1대1문의를 등록합니다.")
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<?> saveInquiry(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.saveInquiry(param));
	}

	/**
	 * 1대1문의 답변
	 */
	@Operation(summary = "1대1문의 답변", description = "1대1문의에 답변을 등록합니다.")
	@PostMapping("/answer")
	@Transactional
	public ResponseEntity<?> answerInquiry(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.answerInquiry(param));
	}

	/**
	 * 1대1문의 삭제
	 */
	@Operation(summary = "1대1문의 삭제", description = "1대1문의를 삭제합니다.")
	@PostMapping("/delete")
	@Transactional
	public ResponseEntity<?> deleteInquiry(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.deleteInquiry(param));
	}
}

