package com.cf.community.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InquiryMapper {
	
	/**
	 * 1대1문의 목록 조회
	 */
	public List<Map<String, ?>> selectInquiryList(Map<String, Object> param);
	
	/**
	 * 1대1문의 개수 조회
	 */
	public int selectInquiryCount(Map<String, Object> param);
	
	/**
	 * 1대1문의 상세 조회
	 */
	public Map<String, ?> selectInquiryDtl(Map<String, Object> param);
	
	/**
	 * 1대1문의 등록
	 */
	public void insertInquiry(Map<String, Object> param);
	
	/**
	 * 1대1문의 답변 수정
	 */
	public int updateInquiryAnswer(Map<String, Object> param);
	
	/**
	 * 1대1문의 삭제
	 */
	public int deleteInquiry(Map<String, Object> param);
}

