package com.cf.community.rest;

import java.util.HashMap;
import java.util.List;
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

import com.cf.community.service.NoticeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notice Management", description = "공지사항 관리 API")
@RestController
@RequestMapping("/api/v1/community/notice")
public class NoticeController {

	@Autowired
	private NoticeService service;

	/**
	 * 공지사항 목록 조회
	 */
	@Operation(summary = "공지사항 목록 조회", description = "공지사항 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getNoticeList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.getNoticeList(param));
	}

	/**
	 * 공지사항 상세 조회
	 */
	@Operation(summary = "공지사항 상세 조회", description = "공지사항 상세 정보를 조회합니다.")
	@PostMapping("/detail")
	public ResponseEntity<?> getNoticeDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.getNoticeDetail(param));
	}

	/**
	 * 공지사항 등록/수정
	 */
	@Operation(summary = "공지사항 등록/수정", description = "공지사항을 등록하거나 수정합니다.")
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<?> saveNotice(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.saveNotice(param));
	}

	/**
	 * 공지사항 삭제
	 */
	@Operation(summary = "공지사항 삭제", description = "공지사항을 삭제합니다.")
	@PostMapping("/delete")
	@Transactional
	public ResponseEntity<?> deleteNotice(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.deleteNotice(param));
	}

	/**
	 * 공지사항 조회수 증가
	 */
	@Operation(summary = "공지사항 조회수 증가", description = "공지사항 조회수를 증가시킵니다.")
	@PostMapping("/view")
	@Transactional
	public ResponseEntity<?> increaseNoticeView(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.increaseNoticeView(param));
	}

	/**
	 * 팝업 공지사항 목록 조회
	 */
	@Operation(summary = "팝업 공지사항 목록 조회", description = "사용자가 아직 보지 않은 팝업 공지사항 목록을 조회합니다.")
	@PostMapping("/popup/list")
	public ResponseEntity<?> getPopupNoticeList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.getPopupNoticeList(param);
		Map<String, Object> result = new HashMap<>();
		result.put("list", list);
		return ResponseEntity.ok(result);
	}

	/**
	 * 팝업 공지사항 조회 기록 저장
	 */
	@Operation(summary = "팝업 공지사항 조회 기록 저장", description = "팝업 공지사항을 본 기록을 저장합니다.")
	@PostMapping("/popup/view")
	@Transactional
	public ResponseEntity<?> savePopupNoticeView(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.savePopupNoticeView(param));
	}
}

