package com.smw.guild.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GuildMapper {
	
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
	 * 길드 삭제 (soft delete)
	 */
	public int deleteGuild(Map<String, Object> param);
	
	/**
	 * 길드 인원수 업데이트
	 */
	public int updateGuildMemberCount(Map<String, Object> param);
	
	/**
	 * 유저의 현재 길드 조회
	 */
	public Map<String, ?> selectUserGuild(Map<String, Object> param);
	
	/**
	 * 유저 길드 이력 등록
	 */
	public int insertUserGuildHistory(Map<String, Object> param);
	
	/**
	 * 유저 길드 이력 조회
	 */
	public List<Map<String, ?>> selectUserGuildHistory(Map<String, Object> param);
	
	/**
	 * 유저 길드 이력 업데이트 (탈퇴일 추가)
	 */
	public int updateUserGuildHistory(Map<String, Object> param);
	
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
	 * 길드 신청 수정 (승인/거절 처리)
	 */
	public int updateGuildApplication(Map<String, Object> param);
	
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
	 * 첨부파일 그룹 번호 생성
	 */
	public Map<String, ?> selectFileId();
	
	/**
	 * 초대 키로 길드 조회
	 */
	public Map<String, ?> selectGuildByInviteKey(Map<String, Object> param);
	
	/**
	 * 초대 키 중복 체크
	 */
	public int checkInviteKeyExists(Map<String, Object> param);
	
	/**
	 * 길드 초대 키만 업데이트
	 */
	public int updateGuildInviteKey(Map<String, Object> param);
	
	/**
	 * 사용자의 현재 길드 ID 업데이트
	 */
	public int updateUserCurrentGuildId(Map<String, Object> param);

	/**
	 * 역할별 사용자 목록 조회 (관리자 조회용)
	 */
	public List<Map<String, ?>> selectUsersByRole(Map<String, Object> param);
}

