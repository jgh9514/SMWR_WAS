package com.cf.community.service;

import java.util.Map;

public interface InquiryService {
	
	/**
	 * 1대1문의 목록 조회 (페이지네이션 포함)
	 */
	public Map<String, Object> getInquiryList(Map<String, Object> param);
	
	/**
	 * 1대1문의 상세 조회
	 */
	public Map<String, ?> getInquiryDetail(Map<String, Object> param);
	
	/**
	 * 1대1문의 등록
	 */
	public Map<String, Object> saveInquiry(Map<String, Object> param);
	
	/**
	 * 1대1문의 답변 수정
	 */
	public Map<String, Object> answerInquiry(Map<String, Object> param);
	
	/**
	 * 1대1문의 삭제
	 */
	public Map<String, Object> deleteInquiry(Map<String, Object> param);
}

