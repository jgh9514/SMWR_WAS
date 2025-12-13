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

import com.cf.community.service.CommentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Comment Management", description = "댓글 관리 API")
@RestController
@RequestMapping("/api/v1/community/comment")
public class CommentController {

	@Autowired
	private CommentService service;

	/**
	 * 댓글 목록 조회
	 */
	@Operation(summary = "댓글 목록 조회", description = "게시글의 댓글 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getCommentList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.getCommentList(param);
		Map<String, Object> result = new HashMap<>();
		result.put("list", list);
		return ResponseEntity.ok(result);
	}

	/**
	 * 댓글 등록
	 */
	@Operation(summary = "댓글 등록", description = "댓글을 등록합니다.")
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<?> saveComment(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.saveComment(param));
	}

	/**
	 * 댓글 수정
	 */
	@Operation(summary = "댓글 수정", description = "댓글을 수정합니다.")
	@PostMapping("/update")
	@Transactional
	public ResponseEntity<?> updateComment(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.updateComment(param));
	}

	/**
	 * 댓글 삭제
	 */
	@Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다.")
	@PostMapping("/delete")
	@Transactional
	public ResponseEntity<?> deleteComment(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.deleteComment(param));
	}
}

