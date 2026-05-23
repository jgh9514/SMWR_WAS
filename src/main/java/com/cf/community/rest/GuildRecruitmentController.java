package com.cf.community.rest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cf.community.service.GuildRecruitmentService;
import com.sysconf.annotation.RequireLogin;
import com.sysconf.util.S3Service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Guild Recruitment", description = "길드원 모집 게시판")
@RestController
@RequestMapping("/api/v1/community/guild-recruitment")
public class GuildRecruitmentController {

	@Autowired
	private GuildRecruitmentService service;

	@Autowired
	private S3Service s3Service;

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

	@Operation(summary = "길드원 모집 이미지 업로드")
	@RequireLogin
	@PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
		Map<String, Object> result = new HashMap<>();
		try {
			String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "image";
			String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : ".jpg";
			String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
			String url = s3Service.uploadImage(file.getBytes(), fileName, file.getContentType(), "guild-recruitment");
			result.put("result", "SUCCESS");
			result.put("url", url);
		} catch (Exception e) {
			result.put("result", "FAIL");
			result.put("message", "이미지 업로드에 실패했습니다.");
		}
		return ResponseEntity.ok(result);
	}
}
