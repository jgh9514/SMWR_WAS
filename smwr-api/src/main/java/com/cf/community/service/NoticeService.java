package com.cf.community.service;

import java.util.List;
import java.util.Map;

public interface NoticeService {
	
	/**
	 * 공지사항 목록 조회 (페이지네이션 포함)
	 */
	public Map<String, Object> getNoticeList(Map<String, Object> param);
	
	/**
	 * 공지사항 상세 조회
	 */
	public Map<String, ?> getNoticeDetail(Map<String, Object> param);
	
	/**
	 * 공지사항 등록/수정
	 */
	public Map<String, Object> saveNotice(Map<String, Object> param);
	
	/**
	 * 공지사항 삭제
	 */
	public Map<String, Object> deleteNotice(Map<String, Object> param);
	
	/**
	 * 공지사항 조회수 증가
	 */
	public Map<String, Object> increaseNoticeView(Map<String, Object> param);
	
	/**
	 * 팝업 공지사항 목록 조회 (사용자가 아직 보지 않은 팝업 공지사항)
	 */
	public List<Map<String, ?>> getPopupNoticeList(Map<String, Object> param);
	
	/**
	 * 팝업 공지사항 조회 기록 저장
	 */
	public Map<String, Object> savePopupNoticeView(Map<String, Object> param);
}

