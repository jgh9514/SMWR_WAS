package com.cf.community.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoticeMapper {
	
	/**
	 * 공지사항 목록 조회
	 */
	public List<Map<String, ?>> selectNoticeList(Map<String, Object> param);
	
	/**
	 * 공지사항 개수 조회
	 */
	public int selectNoticeCount(Map<String, Object> param);
	
	/**
	 * 공지사항 상세 조회
	 */
	public Map<String, ?> selectNoticeDtl(Map<String, Object> param);
	
	/**
	 * 공지사항 등록
	 */
	public void insertNotice(Map<String, Object> param);
	
	/**
	 * 공지사항 수정
	 */
	public int updateNotice(Map<String, Object> param);
	
	/**
	 * 공지사항 삭제
	 */
	public int deleteNotice(Map<String, Object> param);
	
	/**
	 * 공지사항 조회수 증가
	 */
	public int increaseNoticeView(Map<String, Object> param);
	
	/**
	 * 팝업 공지사항 목록 조회 (사용자가 아직 보지 않은 팝업 공지사항)
	 */
	public List<Map<String, ?>> selectPopupNoticeList(Map<String, Object> param);
	
	/**
	 * 팝업 공지사항 조회 기록 개수 조회
	 */
	public int selectPopupNoticeViewCount(Map<String, Object> param);
	
	/**
	 * 팝업 공지사항 조회 기록 저장
	 */
	public void insertPopupNoticeView(Map<String, Object> param);
}

