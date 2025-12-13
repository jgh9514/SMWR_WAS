package com.smw.guild.service;

import java.util.List;
import java.util.Map;

public interface GuildService {
	
	/**
	 * 길드 목록 조회
	 */
	public List<Map<String, ?>> selectGuildList(Map<String, Object> param);
	
	/**
	 * 길드 검색 (회원가입용)
	 */
	public List<Map<String, ?>> searchGuildList(Map<String, Object> param);
	
	/**
	 * 길드 상세 조회
	 */
	public Map<String, ?> selectGuildDtl(Map<String, Object> param);
	
	/**
	 * 길드 등록
	 */
	public int insertGuild(Map<String, Object> param);
	
	/**
	 * 길드 수정
	 */
	public int updateGuild(Map<String, Object> param);
	
	/**
	 * 길드 삭제
	 */
	public int deleteGuild(Map<String, Object> param);
	
	/**
	 * 유저의 현재 길드 조회
	 */
	public Map<String, ?> selectUserGuild(Map<String, Object> param);
	
	/**
	 * 유저 길드 가입
	 */
	public int insertUserGuild(Map<String, Object> param);
	
	/**
	 * 유저 길드 탈퇴
	 */
	public int deleteUserGuild(Map<String, Object> param);
	
	/**
	 * 유저 길드 이력 조회
	 */
	public List<Map<String, ?>> selectUserGuildHistory(Map<String, Object> param);
	
	/**
	 * 길드 신청 목록 조회
	 */
	public List<Map<String, ?>> selectGuildApplicationList(Map<String, Object> param);
	
	/**
	 * 길드 신청 상세 조회
	 */
	public Map<String, ?> selectGuildApplicationDtl(Map<String, Object> param);
	
	/**
	 * 사용자의 현재 대기 중인 길드 신청 조회
	 */
	public Map<String, ?> selectUserPendingApplication(Map<String, Object> param);
	
	/**
	 * 길드 신청 등록
	 */
	public int insertGuildApplication(Map<String, Object> param);
	
	/**
	 * 길드 신청 승인/거절 처리
	 */
	public int processGuildApplication(Map<String, Object> param);
	
	/**
	 * 길드 멤버 목록 조회
	 */
	public List<Map<String, ?>> selectGuildMemberList(Map<String, Object> param);
	
	/**
	 * 첨부파일 목록 조회
	 */
	public List<Map<String, ?>> selectFileAttachmentList(Map<String, Object> param);
	
	/**
	 * 첨부파일 등록
	 */
	public int insertFileAttachment(Map<String, Object> param);
	
	/**
	 * 첨부파일 삭제
	 */
	public int deleteFileAttachment(Map<String, Object> param);
	
	/**
	 * 초대 키로 길드 조회
	 */
	public Map<String, ?> selectGuildByInviteKey(Map<String, Object> param);
	
	/**
	 * 초대 키로 길드 가입
	 */
	public int joinGuildByInviteKey(Map<String, Object> param);
	
	/**
	 * 초대 키 생성
	 */
	public String generateInviteKey();
	
	/**
	 * 초대 코드 채번 (길드의 초대 코드를 새로 생성하고 업데이트)
	 */
	public Map<String, ?> generateInviteCode(Map<String, Object> param);
	
	/**
	 * 사용자의 현재 길드 ID 업데이트
	 */
	public int updateUserCurrentGuildId(Map<String, Object> param);
}

