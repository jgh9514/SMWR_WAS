package com.smw.guild.service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smw.guild.mapper.GuildMapper;
import com.smw.guild.mapper.GuildJoinApplicationMapper;
import com.smw.guild.mapper.GuildMemberActivityLogMapper;
import com.cf.notification.service.NotificationService;
import com.sysconf.security.AdminPrivilegeResolver;
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
	GuildJoinApplicationMapper joinApplicationMapper;

	@Autowired
	GuildMemberActivityLogMapper guildMemberActivityLogMapper;

	@Autowired
	private GuildMemberActivityLogSupport guildMemberActivityLogSupport;

	@Autowired
	private NotificationService notificationService;
	
	@Autowired
	private S3Service s3Service;

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;

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
		
		if (result == 0) {
			Map<String, ?> existing = mapper.selectUserGuild(param);
			if (existing != null) {
				Object existingGuildId = existing.get("guild_id");
				Object requestGuildId = param.get("guild_id");
				if (existingGuildId != null && requestGuildId != null
					&& String.valueOf(existingGuildId).equals(String.valueOf(requestGuildId))) {
					return 1;
				}
			}
		}
		
		// 길드 인원수 증가 및 sys_user 테이블 동기화
		if (result > 0) {
			Object uptUserId = param.get("sess_user_id") != null ? param.get("sess_user_id") : param.get("crt_user_id");
			Map<String, Object> countParam = new HashMap<>();
			countParam.put("guild_id", param.get("guild_id"));
			countParam.put("increment", 1);
			countParam.put("sess_user_id", uptUserId);
			mapper.updateGuildMemberCount(countParam);
			
			// sys_user 테이블의 current_guild_id 업데이트
			Map<String, Object> userUpdateParam = new HashMap<>();
			userUpdateParam.put("user_id", param.get("user_id"));
			userUpdateParam.put("current_guild_id", param.get("guild_id"));
			userUpdateParam.put("sess_user_id", param.get("crt_user_id")); // crt_user_id를 upt_user_id로 사용
			mapper.updateUserCurrentGuildId(userUpdateParam);

			// 길드장/매니저에게 알림 생성 (실패해도 가입 처리는 유지)
			try {
				Map<String, ?> guildInfo = mapper.selectGuildDtl(param);
				if (guildInfo != null) {
					String guildName = (String) guildInfo.get("guild_name");
					String newMemberId = param.get("user_id") != null ? param.get("user_id").toString() : null;
					String guildIdStr = param.get("guild_id") != null ? param.get("guild_id").toString() : null;
					String crtUserIdStr = param.get("crt_user_id") != null ? param.get("crt_user_id").toString() : null;
					
					// 길드장에게 알림
					String leaderId = (String) guildInfo.get("guild_leader_id");
					if (leaderId != null && !leaderId.equals(newMemberId)) {
						notificationService.createNotification(
							leaderId,
							"GUILD_MEMBER_JOINED",
							"새로운 길드원이 가입했습니다",
							guildName + " 길드에 새로운 멤버가 가입했습니다.",
							guildIdStr,
							"/guild-management",
							crtUserIdStr
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
								guildIdStr,
								"/guild-management",
								crtUserIdStr
							);
						}
					}
				}
			} catch (Exception e) {
				log.warn("길드원 가입 알림 생성 실패 (가입 처리는 유지): user_id={}, guild_id={}",
					param.get("user_id"), param.get("guild_id"), e);
			}
		}
		
		return result;
	}

	@Override
	@Transactional
	public int deleteUserGuild(Map<String, Object> param) {
		// 현재 길드 정보 조회 (user_guild_history에서 leave_date IS NULL인 것)
		Map<String, ?> userGuild = mapper.selectUserGuild(param);

		if (userGuild == null) {
			// 이미 탈퇴/추방된 경우 멱등 성공
			return 1;
		}

		Object gidObj = userGuild.get("guild_id");
		Long guildId = null;
		if (gidObj instanceof Number) {
			guildId = ((Number) gidObj).longValue();
		} else if (gidObj != null) {
			guildId = Long.valueOf(gidObj.toString());
		}

		String actingUserId = resolveActingUserId(param);

		// 이력 업데이트 (탈퇴일 추가)
		Map<String, Object> historyParam = new HashMap<>();
		historyParam.put("user_id", param.get("user_id"));
		historyParam.put("guild_id", guildId);
		historyParam.put("leave_date", param.get("leave_date"));
		historyParam.put("leave_reason", param.get("leave_reason"));
		historyParam.put("upt_user_id", actingUserId);
		int result = mapper.updateUserGuildHistory(historyParam);

		if (result == 0) {
			Map<String, ?> recheck = mapper.selectUserGuild(param);
			if (recheck == null) {
				return 1;
			}
			return 0;
		}

		// 길드 인원수 감소 및 sys_user 테이블 동기화
		Map<String, Object> countParam = new HashMap<>();
		countParam.put("guild_id", guildId);
		countParam.put("increment", -1);
		countParam.put("sess_user_id", actingUserId);
		mapper.updateGuildMemberCount(countParam);

		Map<String, Object> userUpdateParam = new HashMap<>();
		userUpdateParam.put("user_id", param.get("user_id"));
		userUpdateParam.put("current_guild_id", null);
		userUpdateParam.put("sess_user_id", actingUserId);
		mapper.updateUserCurrentGuildId(userUpdateParam);

		return result;
	}

	private String resolveActingUserId(Map<String, Object> param) {
		if (param.get("upt_user_id") != null) {
			return param.get("upt_user_id").toString();
		}
		if (param.get("sess_user_id") != null) {
			return param.get("sess_user_id").toString();
		}
		if (param.get("crt_user_id") != null) {
			return param.get("crt_user_id").toString();
		}
		return null;
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
		
		if (result > 0) {
			String guildName = (String) param.get("guild_name");
			String applicationId = param.get("application_id") != null ? param.get("application_id").toString() : null;
			
			try {
				for (String adminId : adminPrivilegeResolver.listConfiguredAdminUserIds()) {
					notificationService.createNotification(
						adminId,
						"GUILD_APPLICATION_PENDING",
						"새로운 길드 생성 신청이 있습니다",
						guildName + " 길드 생성 신청이 접수되었습니다.",
						applicationId,
						"/admin/guildapplication",
						param.get("sess_user_id") != null ? param.get("sess_user_id").toString() : null
					);
				}
			} catch (Exception e) {
				log.warn("길드 생성 신청 알림 생성 실패 (신청은 유지): application_id={}", applicationId, e);
			}
		} else {
			Map<String, ?> pending = mapper.selectUserPendingApplication(param);
			if (pending != null) {
				return 1;
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
		if (result <= 0 && application.get("status") != null && status != null
			&& status.equals(String.valueOf(application.get("status")))) {
			result = 1;
		}
		
		// 승인인 경우: (1) 길드 가입 신청이면 해당 길드에 멤버로 가입, (2) 길드 생성 신청이면 길드 생성
		if ("APPROVED".equals(status) && result > 0) {
			Object appGuildIdObj = application.get("guild_id");
			String appGuildIdStr = appGuildIdObj != null ? appGuildIdObj.toString() : null;
			boolean isJoinApplication = appGuildIdStr != null && !"".equals(appGuildIdStr);
			if (isJoinApplication) {
				// 가입 신청 승인 -> 유저를 기존 길드에 가입 처리
				String applicantUserId = application.get("user_id") != null ? application.get("user_id").toString() : null;
				Long guildId = null;
				if (appGuildIdObj instanceof Number) {
					guildId = ((Number) appGuildIdObj).longValue();
				} else if (appGuildIdStr != null) {
					guildId = Long.valueOf(appGuildIdStr);
				}
				if (guildId == null) {
					return result;
				}

				// 이미 길드가 있는지 확인
				Map<String, Object> checkParam = new HashMap<>();
				checkParam.put("user_id", applicantUserId);
				Map<String, ?> existingGuild = mapper.selectUserGuild(checkParam);
				if (existingGuild != null) {
					// 이미 다른 길드에 가입된 상태면 승인 처리만 하고 종료
					return result;
				}

				Map<String, Object> userGuildParam = new HashMap<>();
				userGuildParam.put("user_id", applicantUserId);
				userGuildParam.put("guild_id", guildId);
				userGuildParam.put("role", "MEMBER");
				userGuildParam.put("join_by_invite", "N");
				userGuildParam.put("crt_user_id", param.get("process_user_id"));
				insertUserGuild(userGuildParam);
				return result;
			}

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
				Object gidObj = guildParam.get("guild_id");
				Long guildId = null;
				if (gidObj instanceof Number) {
					guildId = ((Number) gidObj).longValue();
				} else if (gidObj != null) {
					guildId = Long.valueOf(gidObj.toString());
				}
				
				// 신청자를 길드장(LEADER)으로 가입 처리
				Map<String, Object> userGuildParam = new HashMap<>();
				userGuildParam.put("user_id", applicantUserId);
				userGuildParam.put("guild_id", guildId);
				userGuildParam.put("role", "LEADER"); // 신청자를 길드장으로 설정
				userGuildParam.put("crt_user_id", param.get("process_user_id"));
				insertUserGuild(userGuildParam);
			}
		}
		
		// 반려(또는 승인) 알림은 추후 필요 시 추가
		
		return result;
	}
	
	// ---------------------- 길드 가입 신청 (승인 대기) ----------------------
	@Override
	public List<Map<String, ?>> selectJoinApplicationList(Map<String, Object> param) {
		return joinApplicationMapper.selectJoinApplicationList(param);
	}

	@Override
	public Map<String, ?> selectJoinApplicationDetail(Map<String, Object> param) {
		return joinApplicationMapper.selectJoinApplicationDetail(param);
	}

	@Override
	public Map<String, ?> selectMyPendingJoinApplication(Map<String, Object> param) {
		return joinApplicationMapper.selectMyPendingJoinApplication(param);
	}

	@Override
	@Transactional
	public int insertJoinApplication(Map<String, Object> param) {
		if (param.get("guild_id") == null || "".equals(param.get("guild_id").toString().trim())) {
			throw new IllegalArgumentException("guild_id가 필요합니다.");
		}
		if (param.get("sess_user_id") == null || "".equals(param.get("sess_user_id").toString().trim())) {
			throw new IllegalArgumentException("로그인이 필요합니다. (sess_user_id 없음)");
		}

		// 이미 길드가 있는지 확인
		Map<String, Object> checkParam = new HashMap<>();
		checkParam.put("user_id", param.get("sess_user_id").toString());
		Map<String, ?> existingGuild = mapper.selectUserGuild(checkParam);
		if (existingGuild != null) {
			throw new IllegalStateException("이미 다른 길드에 가입되어 있습니다.");
		}

		// 이미 PENDING 신청이 있으면 동일 길드는 성공으로 간주(중복 클릭 멱등)
		Map<String, ?> pending = joinApplicationMapper.selectMyPendingJoinApplication(param);
		if (pending != null) {
			String pendingGuildId = pending.get("guild_id") != null ? pending.get("guild_id").toString() : "";
			String requestGuildId = param.get("guild_id") != null ? param.get("guild_id").toString() : "";
			if (pendingGuildId.equals(requestGuildId)) {
				return 1;
			}
			throw new IllegalStateException("이미 승인 대기 중인 길드 가입 신청이 있습니다.");
		}

		// 길드 존재 확인
		Map<String, Object> guildParam = new HashMap<>();
		guildParam.put("guild_id", param.get("guild_id"));
		Map<String, ?> guildInfo = mapper.selectGuildDtl(guildParam);
		if (guildInfo == null) {
			throw new IllegalArgumentException("존재하지 않는 길드입니다.");
		}

		int result;
		try {
			result = joinApplicationMapper.insertJoinApplication(param);
		} catch (DataIntegrityViolationException e) {
			Map<String, ?> again = joinApplicationMapper.selectMyPendingJoinApplication(param);
			if (again != null) {
				String pendingGuildId = again.get("guild_id") != null ? again.get("guild_id").toString() : "";
				String requestGuildId = param.get("guild_id") != null ? param.get("guild_id").toString() : "";
				if (pendingGuildId.equals(requestGuildId)) {
					return 1;
				}
			}
			throw e;
		}

		if (result <= 0) {
			Map<String, ?> saved = joinApplicationMapper.selectMyPendingJoinApplication(param);
			if (saved != null) {
				String pendingGuildId = saved.get("guild_id") != null ? saved.get("guild_id").toString() : "";
				String requestGuildId = param.get("guild_id") != null ? param.get("guild_id").toString() : "";
				if (pendingGuildId.equals(requestGuildId)) {
					result = 1;
				}
			}
		}

		// 알림: 길드장/매니저 (신규 신청 시에만, 실패해도 가입 신청은 유지)
		if (result > 0) {
			Map<String, ?> application = joinApplicationMapper.selectMyPendingJoinApplication(param);
			notifyGuildJoinApplicationPending(guildInfo, application, param.get("sess_user_id").toString());
		} else {
			log.warn("길드 가입 신청 insert 반환값이 0입니다. guild_id={}, user_id={}", param.get("guild_id"), param.get("sess_user_id"));
		}

		return result;
	}

	private void notifyGuildJoinApplicationPending(Map<String, ?> guildInfo, Map<String, ?> application, String applicantUserId) {
		if (guildInfo == null || application == null || applicantUserId == null || applicantUserId.isBlank()) {
			return;
		}

		try {
			String guildName = guildInfo.get("guild_name") != null ? guildInfo.get("guild_name").toString() : "길드";
			String userName = application.get("user_name") != null && !application.get("user_name").toString().isBlank()
				? application.get("user_name").toString()
				: applicantUserId;
			String applicationId = application.get("application_id") != null ? application.get("application_id").toString() : null;
			String content = userName + "님이 " + guildName + " 길드 가입을 신청했습니다.";
			String leaderId = guildInfo.get("guild_leader_id") != null ? guildInfo.get("guild_leader_id").toString() : null;

			if (leaderId != null && !leaderId.equals(applicantUserId)) {
				notificationService.createNotification(
					leaderId,
					"GUILD_JOIN_APPLICATION_PENDING",
					"새로운 길드 가입 신청",
					content,
					applicationId,
					"/guild-management",
					applicantUserId
				);
			}

			Map<String, Object> managerParam = new HashMap<>();
			managerParam.put("guild_id", guildInfo.get("guild_id") != null ? guildInfo.get("guild_id") : application.get("guild_id"));
			managerParam.put("role", "MANAGER");
			List<Map<String, ?>> managers = mapper.selectGuildMemberList(managerParam);
			for (Map<String, ?> manager : managers) {
				String managerId = manager.get("user_id") != null ? manager.get("user_id").toString() : null;
				if (managerId != null && !managerId.equals(applicantUserId) && !managerId.equals(leaderId)) {
					notificationService.createNotification(
						managerId,
						"GUILD_JOIN_APPLICATION_PENDING",
						"새로운 길드 가입 신청",
						content,
						applicationId,
						"/guild-management",
						applicantUserId
					);
				}
			}
		} catch (Exception e) {
			log.warn("길드 가입 신청 알림 생성 실패 (신청은 정상 처리됨): guild_id={}, applicant={}",
				guildInfo.get("guild_id"), applicantUserId, e);
		}
	}

	@Override
	@Transactional
	public int processJoinApplication(Map<String, Object> param) {
		if (param.get("application_id") == null || "".equals(param.get("application_id").toString().trim())) {
			throw new IllegalArgumentException("application_id가 필요합니다.");
		}
		if (param.get("status") == null || "".equals(param.get("status").toString().trim())) {
			throw new IllegalArgumentException("status가 필요합니다.");
		}
		if (param.get("process_user_id") == null || "".equals(param.get("process_user_id").toString().trim())) {
			throw new IllegalArgumentException("process_user_id가 필요합니다.");
		}

		Map<String, ?> app = joinApplicationMapper.selectJoinApplicationDetail(param);
		if (app == null) {
			return 0;
		}

		String status = normalizeJoinApplicationStatus(param.get("status").toString());
		param.put("status", status);
		String currentStatus = normalizeJoinApplicationStatus(
			app.get("status") != null ? app.get("status").toString() : null);
		if (!"PENDING".equals(currentStatus)) {
			return status.equals(currentStatus) ? 1 : 0;
		}

		if ("APPROVED".equals(status)) {
			String applicantUserId = app.get("user_id") != null ? app.get("user_id").toString() : null;
			Object gidObj = app.get("guild_id");
			Long guildId = null;
			if (gidObj instanceof Number) {
				guildId = ((Number) gidObj).longValue();
			} else if (gidObj != null) {
				guildId = Long.valueOf(gidObj.toString());
			}
			if (applicantUserId != null && guildId != null) {
				Map<String, Object> checkParam = new HashMap<>();
				checkParam.put("user_id", applicantUserId);
				Map<String, ?> existingGuild = mapper.selectUserGuild(checkParam);
				if (existingGuild != null) {
					Object existingGuildId = existingGuild.get("guild_id");
					if (existingGuildId != null && !String.valueOf(existingGuildId).equals(String.valueOf(guildId))) {
						throw new IllegalStateException("신청자가 이미 다른 길드에 소속되어 있어 승인할 수 없습니다.");
					}
				} else {
					Map<String, Object> userGuildParam = new HashMap<>();
					userGuildParam.put("user_id", applicantUserId);
					userGuildParam.put("guild_id", guildId);
					userGuildParam.put("role", "MEMBER");
					userGuildParam.put("join_by_invite", "N");
					userGuildParam.put("crt_user_id", param.get("process_user_id"));
					insertUserGuild(userGuildParam);
				}
			}
		}

		int updated = joinApplicationMapper.updateJoinApplicationStatus(param);
		if (updated <= 0) {
			return resolveJoinApplicationProcessedCount(param, status);
		}

		if ("APPROVED".equals(status)) {
			String applicantUserId = app.get("user_id") != null ? app.get("user_id").toString() : null;
			Object gidObj = app.get("guild_id");
			// 승인된 신청자에게 알림 (실패해도 승인 처리는 유지)
			if (applicantUserId != null) {
				try {
					String guildName = app.get("guild_name") != null ? app.get("guild_name").toString() : "길드";
					String relatedId = gidObj != null ? gidObj.toString() : null;
					String processorId = param.get("process_user_id").toString();
					notificationService.createNotification(
						applicantUserId,
						"GUILD_JOIN_APPLICATION_APPROVED",
						"길드 가입이 승인되었습니다",
						guildName + " 길드 가입이 승인되었습니다. 설정에서 길드 정보를 확인할 수 있습니다.",
						relatedId,
						"/settings",
						processorId
					);
				} catch (Exception e) {
					log.warn("길드 가입 승인 알림(신청자) 생성 실패: applicant={}, application_id={}", applicantUserId, param.get("application_id"), e);
				}
			}
		}

		return updated;
	}

	private static String normalizeJoinApplicationStatus(String status) {
		if (status == null) {
			return "";
		}
		return status.trim().toUpperCase(Locale.ROOT);
	}

	/** 동시 승인/반려 등으로 UPDATE 0건이어도 이미 목표 상태면 성공(멱등) */
	private int resolveJoinApplicationProcessedCount(Map<String, Object> param, String targetStatus) {
		Map<String, ?> again = joinApplicationMapper.selectJoinApplicationDetail(param);
		if (again == null) {
			return 0;
		}
		String againStatus = normalizeJoinApplicationStatus(
			again.get("status") != null ? again.get("status").toString() : null);
		if (targetStatus.equals(againStatus)) {
			return 1;
		}
		// 승인: 길드원 등록은 됐는데 신청 상태 UPDATE만 실패한 경우(동시 처리·부분 커밋)도 성공 처리
		if ("APPROVED".equals(targetStatus)) {
			String applicantUserId = again.get("user_id") != null ? again.get("user_id").toString() : null;
			Object gidObj = again.get("guild_id");
			if (applicantUserId != null && gidObj != null) {
				Map<String, Object> checkParam = new HashMap<>();
				checkParam.put("user_id", applicantUserId);
				Map<String, ?> existingGuild = mapper.selectUserGuild(checkParam);
				if (existingGuild != null
					&& String.valueOf(existingGuild.get("guild_id")).equals(String.valueOf(gidObj))) {
					log.warn(
						"가입 신청 승인 멱등: user_id={} 는 guild_id={} 소속이나 application_id={} 상태={}",
						applicantUserId, gidObj, param.get("application_id"), againStatus);
					return 1;
				}
			}
		}
		return 0;
	}
	
	@Override
	@Transactional
	public int cancelMyJoinApplication(Map<String, Object> param) {
		if (param.get("sess_user_id") == null || "".equals(param.get("sess_user_id").toString().trim())) {
			throw new IllegalArgumentException("로그인이 필요합니다. (sess_user_id 없음)");
		}
		int updated = joinApplicationMapper.cancelMyPendingJoinApplication(param);
		if (updated > 0) {
			return updated;
		}
		// 이미 취소됐거나 승인대기 건이 없으면 멱등 성공
		Map<String, ?> pending = joinApplicationMapper.selectMyPendingJoinApplication(param);
		if (pending == null) {
			return 1;
		}
		return 0;
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
			Object existingId = existingGuild.get("guild_id");
			Object targetId = guild.get("guild_id");
			if (existingId != null && targetId != null
				&& String.valueOf(existingId).equals(String.valueOf(targetId))) {
				return 1;
			}
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
		userGuildParam.put("sess_user_id", param.get("sess_user_id") != null ? param.get("sess_user_id") : param.get("crt_user_id"));
		
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

	@Override
	@Transactional
	public int updateGuildMemberDisplayName(Map<String, Object> param) {
		return mapper.updateGuildMemberDisplayName(param);
	}

	@Override
	@Transactional
	public int updateGuildMemberRole(Map<String, Object> param) {
		Object roleObj = param.get("role") != null ? param.get("role") : param.get("guild_role");
		String newRole = roleObj != null ? roleObj.toString().trim().toUpperCase() : "";
		if (!"MANAGER".equals(newRole) && !"MEMBER".equals(newRole)) {
			if ("LEADER".equals(newRole)) {
				return transferGuildLeadership(param);
			}
			throw new IllegalArgumentException("변경할 권한이 올바르지 않습니다.");
		}

		String targetUserId = param.get("user_id") != null ? param.get("user_id").toString().trim() : "";
		String actingUserId = param.get("sess_user_id") != null ? param.get("sess_user_id").toString().trim() : "";
		Object guildIdObj = param.get("guild_id");
		if (targetUserId.isEmpty() || actingUserId.isEmpty() || guildIdObj == null) {
			throw new IllegalArgumentException("필수 정보가 누락되었습니다.");
		}
		if (actingUserId.equals(targetUserId)) {
			throw new IllegalStateException("본인의 길드 권한은 여기서 변경할 수 없습니다.");
		}

		Map<String, Object> targetParam = new HashMap<>();
		targetParam.put("user_id", targetUserId);
		Map<String, ?> targetGuild = mapper.selectUserGuild(targetParam);
		if (targetGuild == null || targetGuild.get("guild_id") == null) {
			return 0;
		}
		if (!String.valueOf(guildIdObj).equals(String.valueOf(targetGuild.get("guild_id")))) {
			throw new IllegalStateException("같은 길드 멤버만 변경할 수 있습니다.");
		}

		String currentRole = targetGuild.get("role") != null ? targetGuild.get("role").toString() : "MEMBER";
		if ("LEADER".equals(currentRole)) {
			throw new IllegalStateException("길드장의 권한은 변경할 수 없습니다. 권한 위임을 이용하세요.");
		}
		if (newRole.equals(currentRole)) {
			return 1;
		}

		Map<String, Object> updateParam = new HashMap<>();
		updateParam.put("user_id", targetUserId);
		updateParam.put("guild_id", guildIdObj);
		updateParam.put("role", newRole);
		updateParam.put("upt_user_id", actingUserId);
		int updated = mapper.updateGuildMemberRole(updateParam);
		if (updated == 0) {
			Map<String, ?> recheck = mapper.selectUserGuild(targetParam);
			if (recheck != null && newRole.equals(String.valueOf(recheck.get("role")))) {
				return 1;
			}
		}
		return updated;
	}

	@Override
	@Transactional
	public int transferGuildLeadership(Map<String, Object> param) {
		Object newLeaderObj = param.get("new_leader_user_id") != null
			? param.get("new_leader_user_id")
			: param.get("user_id");
		String newLeaderUserId = newLeaderObj != null ? newLeaderObj.toString().trim() : "";
		String actingUserId = param.get("sess_user_id") != null ? param.get("sess_user_id").toString().trim() : "";
		Object guildIdObj = param.get("guild_id");
		if (newLeaderUserId.isEmpty() || actingUserId.isEmpty() || guildIdObj == null) {
			throw new IllegalArgumentException("필수 정보가 누락되었습니다.");
		}
		if (actingUserId.equals(newLeaderUserId)) {
			return 1;
		}

		Map<String, Object> guildParam = new HashMap<>();
		guildParam.put("guild_id", guildIdObj);
		Map<String, ?> guildInfo = mapper.selectGuildDtl(guildParam);
		if (guildInfo == null || guildInfo.get("guild_leader_id") == null) {
			return 0;
		}
		String currentLeaderId = guildInfo.get("guild_leader_id").toString();
		if (!actingUserId.equals(currentLeaderId)) {
			throw new IllegalStateException("길드장만 권한을 위임할 수 있습니다.");
		}

		Map<String, Object> targetParam = new HashMap<>();
		targetParam.put("user_id", newLeaderUserId);
		Map<String, ?> targetGuild = mapper.selectUserGuild(targetParam);
		if (targetGuild == null || targetGuild.get("guild_id") == null) {
			throw new IllegalStateException("대상 유저가 길드에 소속되어 있지 않습니다.");
		}
		if (!String.valueOf(guildIdObj).equals(String.valueOf(targetGuild.get("guild_id")))) {
			throw new IllegalStateException("같은 길드 멤버에게만 위임할 수 있습니다.");
		}
		String targetRole = targetGuild.get("role") != null ? targetGuild.get("role").toString() : "MEMBER";
		if ("LEADER".equals(targetRole)) {
			return 1;
		}

		Map<String, Object> demoteParam = new HashMap<>();
		demoteParam.put("user_id", currentLeaderId);
		demoteParam.put("guild_id", guildIdObj);
		demoteParam.put("role", "MEMBER");
		demoteParam.put("upt_user_id", actingUserId);
		mapper.updateGuildMemberRole(demoteParam);

		Map<String, Object> promoteParam = new HashMap<>();
		promoteParam.put("user_id", newLeaderUserId);
		promoteParam.put("guild_id", guildIdObj);
		promoteParam.put("role", "LEADER");
		promoteParam.put("upt_user_id", actingUserId);
		int promoted = mapper.updateGuildMemberRole(promoteParam);

		Map<String, Object> guildUpdateParam = new HashMap<>();
		guildUpdateParam.put("guild_id", guildIdObj);
		guildUpdateParam.put("guild_leader_id", newLeaderUserId);
		guildUpdateParam.put("guild_name", guildInfo.get("guild_name"));
		guildUpdateParam.put("guild_description", guildInfo.get("guild_description"));
		guildUpdateParam.put("sess_user_id", actingUserId);
		mapper.updateGuild(guildUpdateParam);

		return promoted > 0 ? promoted : 1;
	}

	@Override
	@Transactional(readOnly = true)
	public Map<String, Object> selectGuildMemberActivityPage(Map<String, Object> param) {
		Map<String, Object> safe = param != null ? param : new HashMap<>();
		int limit = parsePositiveInt(safe.get("limit"), 30, 100);
		int offset = Math.max(0, parsePositiveInt(safe.get("offset"), 0, Integer.MAX_VALUE));
		safe.put("limit", limit);
		safe.put("offset", offset);

		List<Map<String, ?>> list = guildMemberActivityLogMapper.selectGuildMemberActivityLogList(safe);
		if (list != null && !list.isEmpty()) {
			guildMemberActivityLogSupport.enrichActivityLogRows(list);
		}
		int total = guildMemberActivityLogMapper.countGuildMemberActivityLog(safe);

		Map<String, Object> page = new HashMap<>();
		page.put("list", list != null ? list : List.of());
		page.put("total", total);
		page.put("limit", limit);
		page.put("offset", offset);
		return page;
	}

	private static int parsePositiveInt(Object raw, int defaultValue, int max) {
		if (raw == null) {
			return defaultValue;
		}
		try {
			int n = Integer.parseInt(String.valueOf(raw).trim());
			if (n <= 0) {
				return defaultValue;
			}
			return Math.min(n, max);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}

