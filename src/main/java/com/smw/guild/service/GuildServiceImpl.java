package com.smw.guild.service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smw.guild.mapper.GuildMapper;
import com.cf.notification.service.NotificationService;
import com.sysconf.util.DateUtil;
import com.sysconf.util.S3Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class GuildServiceImpl implements GuildService {

	@Autowired 
	DateUtil dateUtil;
	
	@Autowired 
	GuildMapper mapper;

	@Autowired
	private NotificationService notificationService;
	
	@Autowired
	private S3Service s3Service;

	@Override
	public List<Map<String, ?>> selectGuildList(Map<String, Object> param) {
		return mapper.selectGuildList(param);
	}

	@Override
	public List<Map<String, ?>> searchGuildList(Map<String, Object> param) {
		return mapper.searchGuildList(param);
	}

	@Override
	public Map<String, ?> selectGuildDtl(Map<String, Object> param) {
		return mapper.selectGuildDtl(param);
	}

	@Override
	@Transactional
	public int insertGuild(Map<String, Object> param) {
		// 기본값 설정
		if (param.get("max_members") == null) {
			param.put("max_members", 30);
		}
		if (param.get("current_members") == null) {
			param.put("current_members", 0);
		}
		if (param.get("join_type") == null || "".equals(param.get("join_type"))) {
			param.put("join_type", "APPROVAL");
		}
		if (param.get("usg_yn") == null || "".equals(param.get("usg_yn"))) {
			param.put("usg_yn", "Y");
		}
		if (param.get("del_yn") == null || "".equals(param.get("del_yn"))) {
			param.put("del_yn", "N");
		}
		
		// 초대 키 생성 (없으면 자동 생성)
		if (param.get("invite_key") == null || "".equals(param.get("invite_key"))) {
			String inviteKey = generateInviteKey();
			param.put("invite_key", inviteKey);
		}
		
		// 길드 ID는 시퀀스로 자동 생성됨
		int result = mapper.insertGuild(param);
		
		return result;
	}

	@Override
	@Transactional
	public int updateGuild(Map<String, Object> param) {
		return mapper.updateGuild(param);
	}

	@Override
	@Transactional
	public int deleteGuild(Map<String, Object> param) {
		return mapper.deleteGuild(param);
	}

	@Override
	public Map<String, ?> selectUserGuild(Map<String, Object> param) {
		return mapper.selectUserGuild(param);
	}

	@Override
	@Transactional
	public int insertUserGuild(Map<String, Object> param) {
		// 이력 등록 (user_guild_history에 가입 이력 추가)
		Map<String, Object> historyParam = new HashMap<>();
		historyParam.put("user_id", param.get("user_id"));
		historyParam.put("guild_id", param.get("guild_id"));
		historyParam.put("join_date", param.get("join_date"));
		historyParam.put("role", param.get("role"));
		historyParam.put("join_by_invite", param.get("join_by_invite")); // 초대 코드 가입 여부
		historyParam.put("crt_user_id", param.get("crt_user_id"));
		int result = mapper.insertUserGuildHistory(historyParam);
		
		// 길드 인원수 증가 및 sys_user 테이블 동기화
		if (result > 0) {
			Map<String, Object> countParam = new HashMap<>();
			countParam.put("guild_id", param.get("guild_id"));
			countParam.put("increment", 1);
			mapper.updateGuildMemberCount(countParam);
			
			// sys_user 테이블의 current_guild_id 업데이트
			Map<String, Object> userUpdateParam = new HashMap<>();
			userUpdateParam.put("user_id", param.get("user_id"));
			userUpdateParam.put("current_guild_id", param.get("guild_id"));
			userUpdateParam.put("sess_user_id", param.get("crt_user_id")); // crt_user_id를 upt_user_id로 사용
			mapper.updateUserCurrentGuildId(userUpdateParam);

			// 길드장/매니저에게 알림 생성
			Map<String, ?> guildInfo = mapper.selectGuildDtl(param);
			if (guildInfo != null) {
				String guildName = (String) guildInfo.get("guild_name");
				String newMemberId = (String) param.get("user_id");
				
				// 길드장에게 알림
				String leaderId = (String) guildInfo.get("guild_leader_id");
				if (leaderId != null && !leaderId.equals(newMemberId)) {
					notificationService.createNotification(
						leaderId,
						"GUILD_MEMBER_JOINED",
						"새로운 길드원이 가입했습니다",
						guildName + " 길드에 새로운 멤버가 가입했습니다.",
						(String) param.get("guild_id"),
						"/guild-management",
						(String) param.get("crt_user_id")
					);
				}

				// 매니저들에게 알림
				Map<String, Object> managerParam = new HashMap<>();
				managerParam.put("guild_id", param.get("guild_id"));
				managerParam.put("role", "MANAGER");
				List<Map<String, ?>> managers = mapper.selectGuildMemberList(managerParam);
				for (Map<String, ?> manager : managers) {
					String managerId = (String) manager.get("user_id");
					if (managerId != null && !managerId.equals(newMemberId)) {
						notificationService.createNotification(
							managerId,
							"GUILD_MEMBER_JOINED",
							"새로운 길드원이 가입했습니다",
							guildName + " 길드에 새로운 멤버가 가입했습니다.",
							(String) param.get("guild_id"),
							"/guild-management",
							(String) param.get("crt_user_id")
						);
					}
				}
			}
		}
		
		return result;
	}

	@Override
	@Transactional
	public int deleteUserGuild(Map<String, Object> param) {
		// 현재 길드 정보 조회 (user_guild_history에서 leave_date IS NULL인 것)
		Map<String, ?> userGuild = mapper.selectUserGuild(param);
		
		if (userGuild != null) {
			Long guildId = (Long) userGuild.get("guild_id");
			
			// 이력 업데이트 (탈퇴일 추가)
			Map<String, Object> historyParam = new HashMap<>();
			historyParam.put("user_id", param.get("user_id"));
			historyParam.put("guild_id", guildId);
			historyParam.put("leave_date", param.get("leave_date"));
			historyParam.put("leave_reason", param.get("leave_reason"));
			historyParam.put("upt_user_id", param.get("upt_user_id"));
			int result = mapper.updateUserGuildHistory(historyParam);
			
			// 길드 인원수 감소 및 sys_user 테이블 동기화
			if (result > 0) {
				Map<String, Object> countParam = new HashMap<>();
				countParam.put("guild_id", guildId);
				countParam.put("increment", -1);
				mapper.updateGuildMemberCount(countParam);
				
				// sys_user 테이블의 current_guild_id를 NULL로 업데이트
				Map<String, Object> userUpdateParam = new HashMap<>();
				userUpdateParam.put("user_id", param.get("user_id"));
				userUpdateParam.put("current_guild_id", null);
				userUpdateParam.put("sess_user_id", param.get("upt_user_id")); // upt_user_id 사용
				mapper.updateUserCurrentGuildId(userUpdateParam);
			}
			
			return result;
		}
		
		return 0;
	}

	@Override
	public List<Map<String, ?>> selectUserGuildHistory(Map<String, Object> param) {
		return mapper.selectUserGuildHistory(param);
	}

	@Override
	public List<Map<String, ?>> selectGuildApplicationList(Map<String, Object> param) {
		return mapper.selectGuildApplicationList(param);
	}

	@Override
	public Map<String, ?> selectGuildApplicationDtl(Map<String, Object> param) {
		return mapper.selectGuildApplicationDtl(param);
	}

	@Override
	public Map<String, ?> selectUserPendingApplication(Map<String, Object> param) {
		return mapper.selectUserPendingApplication(param);
	}

	@Override
	@Transactional
	public int insertGuildApplication(Map<String, Object> param) {
		// 기본값 설정
		if (param.get("status") == null || "".equals(param.get("status"))) {
			param.put("status", "PENDING");
		}
		
		// 파일 처리 (JSON 파일, 이미지 파일)
		Long fileId = null;
		int fileSeq = 0;
		
		// JSON 파일 처리
		if (param.get("json_file_content") != null && param.get("json_file_name") != null) {
			try {
				byte[] jsonBytes = (byte[]) param.get("json_file_content");
				String jsonFileName = (String) param.get("json_file_name");
				String jsonContentType = "application/json";
				
				// S3에 업로드
				String jsonFileUrl = s3Service.uploadFile(jsonBytes, jsonFileName, jsonContentType, "files");
				
				// 파일 ID 생성 (첫 번째 파일이면)
				if (fileId == null) {
					Map<String, ?> fileIdResult = mapper.selectFileId();
					fileId = ((Number) fileIdResult.get("key")).longValue();
				}
				
				// 파일 첨부 정보 저장
				Map<String, Object> fileParam = new HashMap<>();
				fileParam.put("file_id", fileId);
				fileParam.put("file_seq", ++fileSeq);
				fileParam.put("file_url", jsonFileUrl);
				fileParam.put("file_name", jsonFileName);
				fileParam.put("file_type", "JSON");
				fileParam.put("file_size", param.get("json_file_size"));
				fileParam.put("reference_type", "GUILD_APPLICATION");
				fileParam.put("reference_id", null); // 나중에 application_id로 업데이트
				mapper.insertFileAttachment(fileParam);
			} catch (Exception e) {
				log.error("JSON 파일 업로드 실패", e);
				// 파일 업로드 실패해도 신청은 진행
			}
		}
		
		// 이미지 파일 처리
		if (param.get("image_file_url") != null && param.get("image_file_name") != null) {
			try {
				// 파일 ID 생성 (첫 번째 파일이면)
				if (fileId == null) {
					Map<String, ?> fileIdResult = mapper.selectFileId();
					fileId = ((Number) fileIdResult.get("key")).longValue();
				}
				
				// 파일 첨부 정보 저장
				Map<String, Object> fileParam = new HashMap<>();
				fileParam.put("file_id", fileId);
				fileParam.put("file_seq", ++fileSeq);
				fileParam.put("file_url", param.get("image_file_url"));
				fileParam.put("file_name", param.get("image_file_name"));
				fileParam.put("file_type", "IMAGE");
				fileParam.put("file_size", param.get("image_file_size"));
				fileParam.put("reference_type", "GUILD_APPLICATION");
				fileParam.put("reference_id", null); // 나중에 application_id로 업데이트
				mapper.insertFileAttachment(fileParam);
			} catch (Exception e) {
				log.error("이미지 파일 정보 저장 실패", e);
				// 파일 정보 저장 실패해도 신청은 진행
			}
		}
		
		// file_id 설정
		if (fileId != null) {
			param.put("file_id", fileId);
		}
		
		int result = mapper.insertGuildApplication(param);
		
		// 파일의 reference_id 업데이트 (application_id로)
		if (result > 0 && fileId != null) {
			Object applicationIdObj = param.get("application_id");
			if (applicationIdObj != null) {
				String applicationId = applicationIdObj.toString();
				Map<String, Object> updateParam = new HashMap<>();
				updateParam.put("file_id", fileId);
				updateParam.put("reference_id", applicationId);
				// reference_id 업데이트는 별도 쿼리 필요 (현재는 매퍼에 없으므로 생략)
				// 필요시 updateFileAttachmentReference 메서드 추가
			}
		}
		
		// 관리자에게 알림 생성
		if (result > 0) {
			// 관리자 조회 (role_id가 'RL0001'인 사용자들)
			Map<String, Object> adminParam = new HashMap<>();
			adminParam.put("role_id", "RL0001");
			List<Map<String, ?>> admins = mapper.selectUsersByRole(adminParam);
			
			String guildName = (String) param.get("guild_name");
			String applicationId = param.get("application_id") != null ? param.get("application_id").toString() : null;
			
			for (Map<String, ?> admin : admins) {
				String adminId = (String) admin.get("user_id");
				if (adminId != null) {
					notificationService.createNotification(
						adminId,
						"GUILD_APPLICATION_PENDING",
						"새로운 길드 생성 신청이 있습니다",
						guildName + " 길드 생성 신청이 접수되었습니다.",
						applicationId,
						"/admin/guildapplication",
						(String) param.get("sess_user_id")
					);
				}
			}
		}
		
		return result;
	}

	@Override
	@Transactional
	public int processGuildApplication(Map<String, Object> param) {
		String status = (String) param.get("status");
		
		// 신청 정보 조회
		Map<String, ?> application = mapper.selectGuildApplicationDtl(param);
		if (application == null) {
			return 0;
		}
		
		// 신청 상태 업데이트
		int result = mapper.updateGuildApplication(param);
		
		// 승인인 경우 길드 생성 후 신청자를 길드장으로 가입 처리
		if ("APPROVED".equals(status) && result > 0) {
			String guildName = (String) application.get("guild_name");
			// 신청자 ID (crt_user_id를 user_id로 alias한 값)
			String applicantUserId = (String) application.get("user_id");
			
			// 길드 생성 (신청자를 길드장으로 설정)
			Map<String, Object> guildParam = new HashMap<>();
			guildParam.put("guild_name", guildName);
			guildParam.put("guild_leader_id", applicantUserId); // 신청자를 길드장으로 설정
			guildParam.put("join_type", "APPROVAL");
			guildParam.put("sess_user_id", param.get("process_user_id"));
			
			int guildInsertResult = insertGuild(guildParam);
			if (guildInsertResult > 0) {
				Long guildId = (Long) guildParam.get("guild_id");
				
				// 신청자를 길드장(LEADER)으로 가입 처리
				Map<String, Object> userGuildParam = new HashMap<>();
				userGuildParam.put("user_id", applicantUserId);
				userGuildParam.put("guild_id", guildId);
				userGuildParam.put("role", "LEADER"); // 신청자를 길드장으로 설정
				userGuildParam.put("crt_user_id", param.get("process_user_id"));
				insertUserGuild(userGuildParam);
			}
		}
		
		return result;
	}

	@Override
	public List<Map<String, ?>> selectGuildMemberList(Map<String, Object> param) {
		return mapper.selectGuildMemberList(param);
	}

	@Override
	public List<Map<String, ?>> selectFileAttachmentList(Map<String, Object> param) {
		return mapper.selectFileAttachmentList(param);
	}

	@Override
	@Transactional
	public int insertFileAttachment(Map<String, Object> param) {
		return mapper.insertFileAttachment(param);
	}

	@Override
	@Transactional
	public int deleteFileAttachment(Map<String, Object> param) {
		return mapper.deleteFileAttachment(param);
	}

	@Override
	public Map<String, ?> selectGuildByInviteKey(Map<String, Object> param) {
		return mapper.selectGuildByInviteKey(param);
	}

	@Override
	@Transactional
	public int joinGuildByInviteKey(Map<String, Object> param) {
		// 초대 키로 길드 조회
		Map<String, ?> guild = mapper.selectGuildByInviteKey(param);
		if (guild == null) {
			return 0;
		}
		
		// 이미 가입된 길드가 있는지 확인
		Map<String, Object> checkParam = new HashMap<>();
		checkParam.put("user_id", param.get("user_id"));
		Map<String, ?> existingGuild = mapper.selectUserGuild(checkParam);
		if (existingGuild != null) {
			return -1; // 이미 다른 길드에 가입되어 있음
		}
		
		// 길드 인원수 확인
		Integer maxMembers = (Integer) guild.get("max_members");
		Integer currentMembers = (Integer) guild.get("current_members");
		if (currentMembers >= maxMembers) {
			return -2; // 길드 인원이 가득 참
		}
		
		// 유저 길드 가입 (초대 키로 가입)
		Map<String, Object> userGuildParam = new HashMap<>();
		userGuildParam.put("user_id", param.get("user_id"));
		userGuildParam.put("guild_id", guild.get("guild_id"));
		userGuildParam.put("role", "MEMBER");
		userGuildParam.put("join_by_invite", "Y"); // 초대 키로 가입
		userGuildParam.put("crt_user_id", param.get("crt_user_id"));
		
		return insertUserGuild(userGuildParam);
	}

	@Override
	public String generateInviteKey() {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder(10);
		
		// 중복되지 않는 키 생성
		String inviteKey;
		int maxAttempts = 100;
		int attempts = 0;
		
		do {
			sb.setLength(0);
			for (int i = 0; i < 10; i++) {
				sb.append(chars.charAt(random.nextInt(chars.length())));
			}
			inviteKey = sb.toString();
			
			Map<String, Object> checkParam = new HashMap<>();
			checkParam.put("invite_key", inviteKey);
			int exists = mapper.checkInviteKeyExists(checkParam);
			if (exists == 0) {
				break;
			}
			attempts++;
		} while (attempts < maxAttempts);
		
		return inviteKey;
	}

	@Override
	public Map<String, ?> generateInviteCode(Map<String, Object> param) {
		// 길드 정보 조회 (길드 존재 여부 확인용)
		Map<String, ?> guild = mapper.selectGuildDtl(param);
		if (guild == null) {
			return null;
		}
		
		// 새로운 초대 코드 생성
		String newInviteKey = generateInviteKey();
		
		// 생성된 초대 코드만 반환 (DB 업데이트는 하지 않음)
		Map<String, Object> result = new HashMap<>();
		result.put("invite_key", newInviteKey);
		
		return result;
	}

	@Override
	public int updateUserCurrentGuildId(Map<String, Object> param) {
		return mapper.updateUserCurrentGuildId(param);
	}
}

