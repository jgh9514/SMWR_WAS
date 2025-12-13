package com.cf.community.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper {
	/**
	 * 댓글 목록 조회
	 */
	List<Map<String, ?>> selectCommentList(Map<String, Object> param);

	/**
	 * 댓글 상세 조회
	 */
	Map<String, ?> selectCommentDetail(Map<String, Object> param);

	/**
	 * 댓글 등록
	 */
	int insertComment(Map<String, Object> param);

	/**
	 * 댓글 수정
	 */
	int updateComment(Map<String, Object> param);

	/**
	 * 댓글 삭제
	 */
	int deleteComment(Map<String, Object> param);
}

