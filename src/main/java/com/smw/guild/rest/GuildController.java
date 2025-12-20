package com.smw.guild.rest;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smw.guild.service.GuildService;
import com.sysconf.util.FileValidationUtil;
import com.sysconf.util.S3Service;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Guild Management", description = "길드 관리 API")
@RestController
@RequestMapping("/api/v1/smw/guild")
public class GuildController {

	@Autowired
	private GuildService service;
	
	@Autowired
	private S3Service s3Service;

	/**
	 * 길드 목록 조회
	 */
	@Operation(summary = "길드 목록 조회", description = "길드 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getGuildList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectGuildList(param);
		return ResponseEntity.ok(list);
	}

	/**
	 * 길드 검색 (회원가입용)
	 */
	@Operation(summary = "길드 검색", description = "회원가입 시 길드를 검색합니다.")
	@PostMapping("/search")
	public ResponseEntity<?> searchGuild(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.searchGuildList(param);
		return ResponseEntity.ok(list);
	}

	/**
	 * 길드 상세 조회
	 */
	@Operation(summary = "길드 상세 조회", description = "특정 길드의 상세 정보를 조회합니다.")
	@PostMapping("/detail")
	public ResponseEntity<?> getGuildDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, ?> map = service.selectGuildDtl(param);
		return ResponseEntity.ok(map);
	}

	/**
	 * 길드 등록
	 */
	@Operation(summary = "길드 등록", description = "새로운 길드를 등록합니다.")
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<?> saveGuild(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		
		if (param.get("guild_id") == null || "".equals(param.get("guild_id"))) {
			service.insertGuild(param);
			result.put("result", "SUCCESS");
			result.put("guild_id", param.get("guild_id"));
		} else {
			service.updateGuild(param);
			result.put("result", "SUCCESS");
			result.put("guild_id", param.get("guild_id"));
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 길드 삭제
	 */
	@Operation(summary = "길드 삭제", description = "길드를 삭제합니다.")
	@PostMapping("/delete")
	@Transactional
	public ResponseEntity<?> deleteGuild(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		
		int count = service.deleteGuild(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 유저의 현재 길드 조회
	 */
	@Operation(summary = "유저 길드 조회", description = "유저가 현재 소속된 길드를 조회합니다.")
	@PostMapping("/user-guild")
	public ResponseEntity<?> getUserGuild(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, ?> map = service.selectUserGuild(param);
		return ResponseEntity.ok(map);
	}

	/**
	 * 유저 길드 가입
	 */
	@Operation(summary = "유저 길드 가입", description = "유저를 길드에 가입시킵니다.")
	@PostMapping("/join")
	@Transactional
	public ResponseEntity<?> joinGuild(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		
		// 기본값 설정
		if (param.get("role") == null || "".equals(param.get("role"))) {
			param.put("role", "MEMBER");
		}
		
		int count = service.insertUserGuild(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 유저 길드 탈퇴
	 */
	@Operation(summary = "유저 길드 탈퇴", description = "유저를 길드에서 탈퇴시킵니다.")
	@PostMapping("/leave")
	@Transactional
	public ResponseEntity<?> leaveGuild(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		
		int count = service.deleteUserGuild(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 유저 길드 이력 조회
	 */
	@Operation(summary = "유저 길드 이력 조회", description = "유저의 길드 가입/탈퇴 이력을 조회합니다.")
	@PostMapping("/user-history")
	public ResponseEntity<?> getUserGuildHistory(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectUserGuildHistory(param);
		return ResponseEntity.ok(list);
	}

	/**
	 * 길드 신청 목록 조회
	 */
	@Operation(summary = "길드 신청 목록 조회", description = "길드 가입 신청 목록을 조회합니다.")
	@PostMapping("/application/list")
	public ResponseEntity<?> getGuildApplicationList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectGuildApplicationList(param);
		return ResponseEntity.ok(list);
	}

	/**
	 * 길드 신청 상세 조회
	 */
	@Operation(summary = "길드 신청 상세 조회", description = "길드 가입 신청 상세 정보를 조회합니다.")
	@PostMapping("/application/detail")
	public ResponseEntity<?> getGuildApplicationDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, ?> map = service.selectGuildApplicationDtl(param);
		return ResponseEntity.ok(map);
	}

	/**
	 * 사용자의 현재 대기 중인 길드 신청 조회
	 */
	@Operation(summary = "사용자의 현재 길드 신청 상태 조회", description = "설정창에서 사용자의 현재 대기 중인 길드 신청 상태를 조회합니다.")
	@PostMapping("/application/my-status")
	public ResponseEntity<?> getMyGuildApplicationStatus(HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> param = new HashMap<>();
		
		Map<String, ?> application = service.selectUserPendingApplication(param);
		if (application != null) {
			result.put("result", "SUCCESS");
			result.put("hasPendingApplication", true);
			result.put("application", application);
		} else {
			result.put("result", "SUCCESS");
			result.put("hasPendingApplication", false);
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 길드 신청 등록
	 */
	@Operation(summary = "길드 신청 등록", description = "길드 가입을 신청합니다.")
	@PostMapping(value = "/application/save", consumes = "multipart/form-data")
	@Transactional
	public ResponseEntity<?> saveGuildApplication(
			@RequestParam("guild_name") String guildName,
			@RequestParam(value = "json_file", required = false) MultipartFile jsonFile,
			@RequestParam(value = "image_file", required = false) MultipartFile imageFile,
			HttpSession session, 
			HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		Map<String, Object> param = new HashMap<>();
		
		// 파라미터 설정
		param.put("guild_name", guildName);
		
		// 파일 처리 및 검증
		try {
			if (jsonFile != null && !jsonFile.isEmpty()) {
				// JSON 파일 검증
				FileValidationUtil.ValidationResult jsonValidation = FileValidationUtil.validateJsonFile(jsonFile);
				if (!jsonValidation.isValid()) {
					result.put("result", "FAIL");
					result.put("message", jsonValidation.getErrorMessage());
					return ResponseEntity.ok(result);
				}

				// JSON 파일을 바이트 배열로 저장 (서비스 레이어에서 S3 업로드 처리)
				param.put("json_file_name", jsonFile.getOriginalFilename());
				param.put("json_file_size", jsonFile.getSize());
				param.put("json_file_content", jsonFile.getBytes());
			}
			
			if (imageFile != null && !imageFile.isEmpty()) {
				// 이미지 파일 검증
				FileValidationUtil.ValidationResult imageValidation = FileValidationUtil.validateImageFile(imageFile);
				if (!imageValidation.isValid()) {
					result.put("result", "FAIL");
					result.put("message", imageValidation.getErrorMessage());
					return ResponseEntity.ok(result);
				}

				// 이미지 파일을 S3에 업로드
				try {
					String fileName = imageFile.getOriginalFilename();
					String contentType = imageFile.getContentType();
					if (contentType == null || contentType.isEmpty()) {
						contentType = "image/jpeg"; // 기본값
					}
					
					// S3에 업로드하고 CloudFront URL 반환
					String cloudFrontUrl = s3Service.uploadImage(imageFile.getBytes(), fileName, contentType);
					
					// 파일 정보 저장
					param.put("image_file_name", fileName);
					param.put("image_file_size", imageFile.getSize());
					param.put("image_file_url", cloudFrontUrl); // S3 CloudFront URL 저장
					param.put("image_file_content_type", contentType);
				} catch (Exception e) {
					result.put("result", "FAIL");
					result.put("message", "이미지 업로드 중 오류가 발생했습니다: " + e.getMessage());
					return ResponseEntity.ok(result);
				}
			}
		} catch (Exception e) {
			result.put("result", "FAIL");
			result.put("message", "파일 처리 중 오류가 발생했습니다: " + e.getMessage());
			return ResponseEntity.ok(result);
		}
		
		int count = service.insertGuildApplication(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
			result.put("message", "길드 신청 등록에 실패했습니다.");
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 길드 신청 승인/거절 처리
	 */
	@Operation(summary = "길드 신청 처리", description = "길드 가입 신청을 승인하거나 거절합니다.")
	@PostMapping("/application/process")
	@Transactional
	public ResponseEntity<?> processGuildApplication(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		
		int count = service.processGuildApplication(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 길드 멤버 목록 조회
	 */
	@Operation(summary = "길드 멤버 목록 조회", description = "길드에 소속된 멤버 목록을 조회합니다.")
	@PostMapping("/member/list")
	public ResponseEntity<?> getGuildMemberList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		List<Map<String, ?>> list = service.selectGuildMemberList(param);
		return ResponseEntity.ok(list);
	}

	/**
	 * 초대 키로 길드 조회
	 */
	@Operation(summary = "초대 키로 길드 조회", description = "초대 키로 길드 정보를 조회합니다.")
	@PostMapping("/invite/check")
	public ResponseEntity<?> checkGuildByInviteKey(@RequestBody Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		Map<String, ?> guild = service.selectGuildByInviteKey(param);
		if (guild != null) {
			result.put("result", "SUCCESS");
			result.put("guild", guild);
		} else {
			result.put("result", "FAIL");
			result.put("message", "유효하지 않은 초대 키입니다.");
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 초대 키로 길드 가입
	 */
	@Operation(summary = "초대 키로 길드 가입", description = "초대 키를 사용하여 길드에 즉시 가입합니다.")
	@PostMapping("/invite/join")
	public ResponseEntity<?> joinGuildByInviteKey(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		
		int count = service.joinGuildByInviteKey(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
			result.put("message", "길드에 가입되었습니다.");
		} else if (count == -1) {
			result.put("result", "FAIL");
			result.put("message", "이미 다른 길드에 가입되어 있습니다.");
		} else if (count == -2) {
			result.put("result", "FAIL");
			result.put("message", "길드 인원이 가득 찼습니다.");
		} else {
			result.put("result", "FAIL");
			result.put("message", "가입에 실패했습니다.");
		}
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 초대 코드 채번
	 */
	@Operation(summary = "초대 코드 채번", description = "길드의 초대 코드를 새로 생성합니다. (DB 업데이트는 하지 않음)")
	@PostMapping("/invite/generate")
	public ResponseEntity<?> generateInviteCode(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		
		Map<String, ?> generatedCode = service.generateInviteCode(param);
		if (generatedCode != null && generatedCode.get("invite_key") != null) {
			result.put("result", "SUCCESS");
			result.put("invite_code", generatedCode.get("invite_key"));
		} else {
			result.put("result", "FAIL");
			result.put("message", "초대 코드 생성에 실패했습니다.");
		}
		
		return ResponseEntity.ok(result);
	}
}

