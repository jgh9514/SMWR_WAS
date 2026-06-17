package com.smw.guild.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

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
import lombok.extern.slf4j.Slf4j;

@Tag(name = "Guild Management", description = "길드 관리 API")
@RestController
@RequestMapping("/api/v1/smw/guild")
@Slf4j
public class GuildController {

	@Autowired
	private GuildService service;
	
	@Autowired
	private S3Service s3Service;

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
	
	@SuppressWarnings("unchecked")
	private String getSessGuildId(HttpServletRequest request) {
		Object attr = request.getAttribute("userInfo");
		if (attr instanceof Map) {
			Map<String, Object> userInfo = (Map<String, Object>) attr;
			Object v = userInfo.get("sess_guild_id");
			return v != null ? v.toString() : null;
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private String getSessGuildRole(HttpServletRequest request) {
		Object attr = request.getAttribute("userInfo");
		if (attr instanceof Map) {
			Map<String, Object> userInfo = (Map<String, Object>) attr;
			Object v = userInfo.get("sess_guild_role");
			return v != null ? v.toString() : null;
		}
		return null;
	}

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
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();

		if (safeParam.get("guild_id") == null || "".equals(safeParam.get("guild_id"))) {
			service.insertGuild(safeParam);
			result.put("result", "SUCCESS");
			result.put("guild_id", safeParam.get("guild_id"));
			return ResponseEntity.ok(result);
		}

		String sessUserId = getSessUserId(request);
		String sessGuildId = getSessGuildId(request);
		String sessGuildRole = getSessGuildRole(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		boolean isLeader = "LEADER".equals(sessGuildRole);
		boolean isManager = "MANAGER".equals(sessGuildRole);
		if (!isLeader && !isManager) {
			result.put("result", "FAIL");
			result.put("message", "권한이 없습니다.");
			return ResponseEntity.status(403).body(result);
		}
		if (sessGuildId == null || sessGuildId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "길드에 소속되어 있지 않습니다.");
			return ResponseEntity.ok(result);
		}
		if (!sessGuildId.equals(String.valueOf(safeParam.get("guild_id")).trim())) {
			result.put("result", "FAIL");
			result.put("message", "소속 길드만 수정할 수 있습니다.");
			return ResponseEntity.ok(result);
		}

		safeParam.put("guild_id", sessGuildId);
		safeParam.put("sess_user_id", sessUserId);
		if (safeParam.get("guild_description") == null && safeParam.get("description") != null) {
			safeParam.put("guild_description", safeParam.get("description"));
		}

		service.updateGuild(safeParam);
		result.put("result", "SUCCESS");
		result.put("guild_id", sessGuildId);
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
		String sessUserId = getSessUserId(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			Map<String, Object> result = new HashMap<>();
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		
		// 클라이언트 입력 user_id 대신 세션 사용자 기준으로 현재 길드를 조회한다.
		param.put("user_id", sessUserId);
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

		String sessUserId = getSessUserId(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		// 클라이언트 입력(user_id)에 의존하지 않고, 로그인 사용자로 가입 처리
		param.put("user_id", sessUserId);
		param.put("crt_user_id", sessUserId);
		param.put("sess_user_id", sessUserId);
		
		// 기본값 설정
		if (param.get("role") == null || "".equals(param.get("role"))) {
			param.put("role", "MEMBER");
		}
		
		int count = service.insertUserGuild(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
			result.put("message", "길드에 가입되었습니다.");
		} else {
			result.put("result", "FAIL");
			result.put("message", "길드 가입에 실패했습니다.");
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

		String sessUserId = getSessUserId(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		// 클라이언트 입력(user_id)에 의존하지 않고, 로그인 사용자로 탈퇴 처리
		param.put("user_id", sessUserId);
		param.put("upt_user_id", sessUserId);
		param.put("sess_user_id", sessUserId);
		
		int count = service.deleteUserGuild(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
			result.put("message", "길드에서 탈퇴했습니다.");
		} else {
			result.put("result", "FAIL");
			result.put("message", "탈퇴에 실패했습니다.");
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
			result.put("message", "길드 생성 신청이 접수되었습니다.");
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
		
		String sessUserId = getSessUserId(request);
		if (sessUserId != null && !sessUserId.isEmpty()) {
			// 처리자/수정자 보정 (클라이언트 입력에 의존하지 않음)
			param.put("process_user_id", sessUserId);
			param.put("upt_user_id", sessUserId);
			param.put("sess_user_id", sessUserId);
		}
		
		int count = service.processGuildApplication(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
			String status = param.get("status") != null ? param.get("status").toString() : "";
			if ("APPROVED".equals(status)) {
				result.put("message", "길드 신청을 승인했습니다.");
			} else if ("REJECTED".equals(status)) {
				result.put("message", "길드 신청을 반려했습니다.");
			} else {
				result.put("message", "처리되었습니다.");
			}
		} else {
			result.put("result", "FAIL");
			result.put("message", "처리할 수 없는 신청입니다.");
		}
		
		return ResponseEntity.ok(result);
	}
	
	// ---------------------- 길드 가입 신청 (승인 대기) ----------------------
	
	@Operation(summary = "내 길드 가입 신청 상태", description = "내 승인대기 길드 가입 신청을 조회합니다.")
	@PostMapping("/join-application/my-status")
	public ResponseEntity<?> myJoinApplicationStatus(HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		Map<String, Object> param = new HashMap<>();
		param.put("sess_user_id", sessUserId);
		Map<String, ?> app = service.selectMyPendingJoinApplication(param);
		result.put("result", "SUCCESS");
		result.put("hasPendingJoinApplication", app != null);
		if (app != null) {
			result.put("application", app);
		}
		return ResponseEntity.ok(result);
	}
	
	@Operation(summary = "길드 가입 신청", description = "길드에 가입 신청(PENDING)합니다.")
	@PostMapping("/join-application/save")
	@Transactional
	public ResponseEntity<?> applyJoinApplication(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		param.put("sess_user_id", sessUserId);
		try {
			int count = service.insertJoinApplication(param);
			if (count > 0) {
				result.put("result", "SUCCESS");
				result.put("message", "길드 가입 신청이 접수되었습니다.");
			} else {
				result.put("result", "FAIL");
				result.put("message", "길드 가입 신청에 실패했습니다.");
			}
		} catch (IllegalArgumentException | IllegalStateException e) {
			result.put("result", "FAIL");
			result.put("message", e.getMessage());
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			log.error("길드 가입 신청 처리 중 오류: user_id={}", sessUserId, e);
			result.put("result", "FAIL");
			result.put("message", "길드 가입 신청 처리 중 오류가 발생했습니다.");
			return ResponseEntity.ok(result);
		}
		return ResponseEntity.ok(result);
	}
	
	@Operation(summary = "길드 가입 신청 목록", description = "길드장/매니저가 길드 가입 신청 목록을 조회합니다.")
	@PostMapping("/join-application/list")
	public ResponseEntity<?> joinApplicationList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String sessGuildId = getSessGuildId(request);
		String sessGuildRole = getSessGuildRole(request);
		if (sessGuildId == null || sessGuildId.isEmpty()) {
			return ResponseEntity.ok(new java.util.ArrayList<>());
		}
		boolean isLeader = "LEADER".equals(sessGuildRole);
		boolean isManager = "MANAGER".equals(sessGuildRole);
		if (!isLeader && !isManager) {
			return ResponseEntity.status(403).body(new java.util.ArrayList<>());
		}
		// 서버측에서 guild_id 강제
		param.put("guild_id", sessGuildId);
		return ResponseEntity.ok(service.selectJoinApplicationList(param));
	}
	
	@Operation(summary = "길드 가입 신청 처리", description = "길드 가입 신청을 승인/반려합니다.")
	@PostMapping("/join-application/process")
	@Transactional
	public ResponseEntity<?> processJoinApplication(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		String sessUserId = getSessUserId(request);
		String sessGuildRole = getSessGuildRole(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		boolean isLeader = "LEADER".equals(sessGuildRole);
		boolean isManager = "MANAGER".equals(sessGuildRole);
		if (!isLeader && !isManager) {
			result.put("result", "FAIL");
			result.put("message", "권한이 없습니다.");
			return ResponseEntity.status(403).body(result);
		}
		param.put("process_user_id", sessUserId);
		try {
			int count = service.processJoinApplication(param);
			if (count > 0) {
				result.put("result", "SUCCESS");
				String status = param.get("status") != null ? param.get("status").toString() : "";
				if ("APPROVED".equals(status)) {
					result.put("message", "가입 신청을 승인했습니다.");
				} else if ("REJECTED".equals(status)) {
					result.put("message", "가입 신청을 반려했습니다.");
				}
			} else {
				result.put("result", "FAIL");
				result.put("message", "처리할 수 없는 신청입니다.");
			}
		} catch (IllegalArgumentException e) {
			result.put("result", "FAIL");
			result.put("message", e.getMessage());
			return ResponseEntity.ok(result);
		} catch (IllegalStateException e) {
			result.put("result", "FAIL");
			result.put("message", e.getMessage());
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			result.put("result", "FAIL");
			result.put("message", "가입 신청 처리 중 오류가 발생했습니다.");
			return ResponseEntity.ok(result);
		}
		return ResponseEntity.ok(result);
	}
	
	@Operation(summary = "길드 가입 신청 취소", description = "내 승인대기 길드 가입 신청을 취소합니다.")
	@PostMapping("/join-application/cancel")
	@Transactional
	public ResponseEntity<?> cancelJoinApplication(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		String sessUserId = getSessUserId(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		Map<String, Object> safeParam = param != null ? param : new HashMap<>();
		safeParam.put("sess_user_id", sessUserId);
		try {
			int count = service.cancelMyJoinApplication(safeParam);
			if (count > 0) {
				result.put("result", "SUCCESS");
				result.put("message", "가입 신청을 취소했습니다.");
			} else {
				result.put("result", "FAIL");
				result.put("message", "취소할 승인대기 신청이 없습니다.");
			}
		} catch (IllegalArgumentException e) {
			result.put("result", "FAIL");
			result.put("message", e.getMessage());
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
	 * 길드 멤버 추방 (길드장/매니저)
	 */
	@Operation(summary = "길드 멤버 추방", description = "길드장/매니저가 길드 멤버를 추방합니다.")
	@PostMapping("/member/kick")
	@Transactional
	public ResponseEntity<?> kickGuildMember(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();
		
		String sessUserId = getSessUserId(request);
		String sessGuildId = getSessGuildId(request);
		String sessGuildRole = getSessGuildRole(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		if (sessGuildId == null || sessGuildId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "길드에 소속되어 있지 않습니다.");
			return ResponseEntity.ok(result);
		}
		boolean isLeader = "LEADER".equals(sessGuildRole);
		boolean isManager = "MANAGER".equals(sessGuildRole);
		if (!isLeader && !isManager) {
			result.put("result", "FAIL");
			result.put("message", "권한이 없습니다.");
			return ResponseEntity.status(403).body(result);
		}
		
		Object targetObj = param.get("user_id");
		String targetUserId = targetObj != null ? targetObj.toString().trim() : "";
		if (targetUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "user_id가 필요합니다.");
			return ResponseEntity.ok(result);
		}
		if (sessUserId.equals(targetUserId)) {
			result.put("result", "FAIL");
			result.put("message", "본인은 추방할 수 없습니다.");
			return ResponseEntity.ok(result);
		}
		
		// 대상 유저의 현재 길드 확인
		Map<String, Object> targetGuildParam = new HashMap<>();
		targetGuildParam.put("user_id", targetUserId);
		Map<String, ?> targetGuild = service.selectUserGuild(targetGuildParam);
		if (targetGuild == null || targetGuild.get("guild_id") == null) {
			result.put("result", "SUCCESS");
			result.put("message", "이미 추방되었거나 길드에 소속되어 있지 않습니다.");
			return ResponseEntity.ok(result);
		}
		String targetGuildId = targetGuild.get("guild_id").toString();
		if (!sessGuildId.equals(targetGuildId)) {
			result.put("result", "FAIL");
			result.put("message", "같은 길드 멤버만 추방할 수 있습니다.");
			return ResponseEntity.ok(result);
		}

		String targetRole = targetGuild.get("role") != null ? targetGuild.get("role").toString() : null;
		// 매니저는 MEMBER만 추방 가능, 길드장은 LEADER를 제외하고 추방 가능
		if (isManager && !"MEMBER".equals(targetRole)) {
			result.put("result", "FAIL");
			result.put("message", "매니저는 일반 길드원만 추방할 수 있습니다.");
			return ResponseEntity.ok(result);
		}
		if (isLeader && "LEADER".equals(targetRole)) {
			result.put("result", "FAIL");
			result.put("message", "길드장은 추방할 수 없습니다.");
			return ResponseEntity.ok(result);
		}

		// leave_reason 기록 (옵션)
		Object reasonObj = param.get("leave_reason");
		String reason = reasonObj != null ? reasonObj.toString().trim() : "";

		Map<String, Object> kickParam = new HashMap<>();
		kickParam.put("user_id", targetUserId);
		kickParam.put("upt_user_id", sessUserId);
		kickParam.put("sess_user_id", sessUserId);
		if (!reason.isEmpty()) {
			kickParam.put("leave_reason", reason);
		} else {
			kickParam.put("leave_reason", "KICK");
		}

		int count = service.deleteUserGuild(kickParam);
		if (count > 0) {
			result.put("result", "SUCCESS");
			result.put("message", "추방 처리되었습니다.");
		} else {
			Map<String, ?> recheck = service.selectUserGuild(targetGuildParam);
			if (recheck == null) {
				result.put("result", "SUCCESS");
				result.put("message", "추방 처리되었습니다.");
			} else {
				result.put("result", "FAIL");
				result.put("message", "추방에 실패했습니다.");
			}
		}

		return ResponseEntity.ok(result);
	}

	/**
	 * 길드 멤버 표시명 수정 (길드장/매니저)
	 */
	@Operation(summary = "길드 멤버 이름 수정", description = "길드장/매니저가 같은 길드 멤버의 표시명(user_nm)을 수정합니다.")
	@PostMapping("/member/name/update")
	@Transactional
	public ResponseEntity<?> updateGuildMemberName(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = new HashMap<>();

		String sessUserId = getSessUserId(request);
		String sessGuildId = getSessGuildId(request);
		String sessGuildRole = getSessGuildRole(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		if (sessGuildId == null || sessGuildId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "길드에 소속되어 있지 않습니다.");
			return ResponseEntity.ok(result);
		}
		boolean isLeader = "LEADER".equals(sessGuildRole);
		boolean isManager = "MANAGER".equals(sessGuildRole);
		if (!isLeader && !isManager) {
			result.put("result", "FAIL");
			result.put("message", "권한이 없습니다.");
			return ResponseEntity.status(403).body(result);
		}

		Object targetObj = param.get("user_id");
		String targetUserId = targetObj != null ? targetObj.toString().trim() : "";
		if (targetUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "user_id가 필요합니다.");
			return ResponseEntity.ok(result);
		}

		Object nameObj = param.get("user_nm") != null ? param.get("user_nm") : param.get("user_name");
		String userNm = nameObj != null ? nameObj.toString().trim() : "";
		if (userNm.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "이름을 입력해주세요.");
			return ResponseEntity.ok(result);
		}
		if (userNm.length() > 100) {
			result.put("result", "FAIL");
			result.put("message", "이름은 100자 이내로 입력해주세요.");
			return ResponseEntity.ok(result);
		}

		Map<String, Object> targetGuildParam = new HashMap<>();
		targetGuildParam.put("user_id", targetUserId);
		Map<String, ?> targetGuild = service.selectUserGuild(targetGuildParam);
		if (targetGuild == null || targetGuild.get("guild_id") == null) {
			result.put("result", "FAIL");
			result.put("message", "대상 유저가 길드에 소속되어 있지 않습니다.");
			return ResponseEntity.ok(result);
		}
		if (!sessGuildId.equals(String.valueOf(targetGuild.get("guild_id")).trim())) {
			result.put("result", "FAIL");
			result.put("message", "같은 길드 멤버만 수정할 수 있습니다.");
			return ResponseEntity.ok(result);
		}

		String targetRole = targetGuild.get("role") != null ? targetGuild.get("role").toString() : null;
		if (isManager && !"MEMBER".equals(targetRole)) {
			result.put("result", "FAIL");
			result.put("message", "매니저는 일반 길드원의 이름만 변경할 수 있습니다.");
			return ResponseEntity.ok(result);
		}
		if (isLeader && "LEADER".equals(targetRole) && !sessUserId.equals(targetUserId)) {
			result.put("result", "FAIL");
			result.put("message", "다른 길드장의 이름은 변경할 수 없습니다.");
			return ResponseEntity.ok(result);
		}

		Map<String, Object> updateParam = new HashMap<>();
		updateParam.put("user_id", targetUserId);
		updateParam.put("user_nm", userNm);
		updateParam.put("sess_user_id", sessUserId);
		int count = service.updateGuildMemberDisplayName(updateParam);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
			result.put("message", "이름 변경에 실패했습니다.");
		}
		return ResponseEntity.ok(result);
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

		String sessUserId = getSessUserId(request);
		if (sessUserId == null || sessUserId.isEmpty()) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return ResponseEntity.status(401).body(result);
		}
		param.put("user_id", sessUserId);
		param.put("crt_user_id", sessUserId);
		param.put("sess_user_id", sessUserId);
		
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

