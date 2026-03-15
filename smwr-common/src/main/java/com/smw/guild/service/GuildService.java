package com.smw.guild.service;

import java.util.List;
import java.util.Map;

/**
 * 길드 서비스 인터페이스.
 * smwr-api에 실제 구현, smwr-admin에는 Stub 구현 사용.
 */
public interface GuildService {

	/**
	 * 길드 목록 조회
	 */
	List<Map<String, ?>> selectGuildList(Map<String, Object> param);

	/**
	 * 길드 검색 (회원가입용)
	 */
	List<Map<String, ?>> searchGuildList(Map<String, Object> param);

	/**
	 * 길드 상세 조회
	 */
	Map<String, ?> selectGuildDtl(Map<String, Object> param);

	/**
	 * 길드 등록
	 */
	int insertGuild(Map<String, Object> param);

	/**
	 * 길드 수정
	 */
	int updateGuild(Map<String, Object> param);

	/**
	 * 길드 삭제
	 */
	int deleteGuild(Map<String, Object> param);

	/**
	 * 유저의 현재 길드 조회
	 */
	Map<String, ?> selectUserGuild(Map<String, Object> param);

	/**
	 * 유저 길드 가입
	 */
	int insertUserGuild(Map<String, Object> param);

	/**
	 * 유저 길드 탈퇴
	 */
	int deleteUserGuild(Map<String, Object> param);

	/**
	 * 유저 길드 이력 조회
	 */
	List<Map<String, ?>> selectUserGuildHistory(Map<String, Object> param);

	/**
	 * 길드 신청 목록 조회
	 */
	List<Map<String, ?>> selectGuildApplicationList(Map<String, Object> param);

	/**
	 * 길드 신청 상세 조회
	 */
	Map<String, ?> selectGuildApplicationDtl(Map<String, Object> param);

	/**
	 * 사용자의 현재 대기 중인 길드 신청 조회
	 */
	Map<String, ?> selectUserPendingApplication(Map<String, Object> param);

	/**
	 * 길드 신청 등록
	 */
	int insertGuildApplication(Map<String, Object> param);

	/**
	 * 길드 신청 승인/거절 처리
	 */
	int processGuildApplication(Map<String, Object> param);

	List<Map<String, ?>> selectJoinApplicationList(Map<String, Object> param);

	Map<String, ?> selectMyPendingJoinApplication(Map<String, Object> param);

	int insertJoinApplication(Map<String, Object> param);

	int processJoinApplication(Map<String, Object> param);

	int cancelMyJoinApplication(Map<String, Object> param);

	/**
	 * 길드 멤버 목록 조회
	 */
	List<Map<String, ?>> selectGuildMemberList(Map<String, Object> param);

	/**
	 * 첨부파일 목록 조회
	 */
	List<Map<String, ?>> selectFileAttachmentList(Map<String, Object> param);

	/**
	 * 첨부파일 등록
	 */
	int insertFileAttachment(Map<String, Object> param);

	/**
	 * 첨부파일 삭제
	 */
	int deleteFileAttachment(Map<String, Object> param);

	/**
	 * 초대 키로 길드 조회
	 */
	Map<String, ?> selectGuildByInviteKey(Map<String, Object> param);

	/**
	 * 초대 키로 길드 가입
	 */
	int joinGuildByInviteKey(Map<String, Object> param);

	/**
	 * 초대 키 생성
	 */
	String generateInviteKey();

	/**
	 * 초대 코드 채번 (길드의 초대 코드를 새로 생성하고 업데이트)
	 */
	Map<String, ?> generateInviteCode(Map<String, Object> param);

	/**
	 * 사용자의 현재 길드 ID 업데이트
	 */
	int updateUserCurrentGuildId(Map<String, Object> param);
}
