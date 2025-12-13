package com.cf.community.service;

import java.util.List;
import java.util.Map;

public interface CommentService {
	/**
	 * 댓글 목록 조회
	 */
	List<Map<String, ?>> getCommentList(Map<String, Object> param);

	/**
	 * 댓글 등록
	 */
	Map<String, Object> saveComment(Map<String, Object> param);

	/**
	 * 댓글 수정
	 */
	Map<String, Object> updateComment(Map<String, Object> param);

	/**
	 * 댓글 삭제
	 */
	Map<String, Object> deleteComment(Map<String, Object> param);
}

