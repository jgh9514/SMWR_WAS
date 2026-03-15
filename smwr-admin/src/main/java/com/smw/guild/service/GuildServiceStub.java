package com.smw.guild.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Admin 모듈용 GuildService 스텁.
 * TokenUtil 등에서 길드 정보가 필요할 때 null/빈 값 반환.
 * 실제 길드 기능은 smwr-api에서만 사용.
 * WebConfig에서 @Bean으로 등록됨.
 */
public class GuildServiceStub implements GuildService {

	@Override
	public List<Map<String, ?>> selectGuildList(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public List<Map<String, ?>> searchGuildList(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public Map<String, ?> selectGuildDtl(Map<String, Object> param) {
		return null;
	}

	@Override
	public int insertGuild(Map<String, Object> param) {
		return 0;
	}

	@Override
	public int updateGuild(Map<String, Object> param) {
		return 0;
	}

	@Override
	public int deleteGuild(Map<String, Object> param) {
		return 0;
	}

	@Override
	public Map<String, ?> selectUserGuild(Map<String, Object> param) {
		return null;
	}

	@Override
	public int insertUserGuild(Map<String, Object> param) {
		return 0;
	}

	@Override
	public int deleteUserGuild(Map<String, Object> param) {
		return 0;
	}

	@Override
	public List<Map<String, ?>> selectUserGuildHistory(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public List<Map<String, ?>> selectGuildApplicationList(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public Map<String, ?> selectGuildApplicationDtl(Map<String, Object> param) {
		return null;
	}

	@Override
	public Map<String, ?> selectUserPendingApplication(Map<String, Object> param) {
		return null;
	}

	@Override
	public int insertGuildApplication(Map<String, Object> param) {
		return 0;
	}

	@Override
	public int processGuildApplication(Map<String, Object> param) {
		return 0;
	}

	@Override
	public List<Map<String, ?>> selectJoinApplicationList(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public Map<String, ?> selectMyPendingJoinApplication(Map<String, Object> param) {
		return null;
	}

	@Override
	public int insertJoinApplication(Map<String, Object> param) {
		return 0;
	}

	@Override
	public int processJoinApplication(Map<String, Object> param) {
		return 0;
	}

	@Override
	public int cancelMyJoinApplication(Map<String, Object> param) {
		return 0;
	}

	@Override
	public List<Map<String, ?>> selectGuildMemberList(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public List<Map<String, ?>> selectFileAttachmentList(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public int insertFileAttachment(Map<String, Object> param) {
		return 0;
	}

	@Override
	public int deleteFileAttachment(Map<String, Object> param) {
		return 0;
	}

	@Override
	public Map<String, ?> selectGuildByInviteKey(Map<String, Object> param) {
		return null;
	}

	@Override
	public int joinGuildByInviteKey(Map<String, Object> param) {
		return 0;
	}

	@Override
	public String generateInviteKey() {
		return "";
	}

	@Override
	public Map<String, ?> generateInviteCode(Map<String, Object> param) {
		return null;
	}

	@Override
	public int updateUserCurrentGuildId(Map<String, Object> param) {
		return 0;
	}
}
