package com.cf.community.rest;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cf.community.service.GuildRecruitmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Guild Recruitment", description = "길드원 모집 게시판")
@RestController
@RequestMapping("/api/v1/community/guild-recruitment")
public class GuildRecruitmentController {

	@Autowired
	private GuildRecruitmentService service;

	@Operation(summary = "길드원 모집 목록")
	@PostMapping("/list")
	public ResponseEntity<?> list(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.getList(param));
	}

	@Operation(summary = "길드원 모집 상세")
	@PostMapping("/detail")
	public ResponseEntity<?> detail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.getDetail(param));
	}

	@Operation(summary = "길드원 모집 등록/수정")
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<?> save(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.save(param));
	}

	@Operation(summary = "길드원 모집 삭제")
	@PostMapping("/delete")
	@Transactional
	public ResponseEntity<?> delete(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		return ResponseEntity.ok(service.delete(param));
	}
}
