package com.cf.community.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cf.community.mapper.CommentMapper;

@Service
@Primary
public class CommentServiceImpl implements CommentService {

	@Autowired
	private CommentMapper mapper;

	@Override
	public List<Map<String, ?>> getCommentList(Map<String, Object> param) {
		return mapper.selectCommentList(param);
	}

	@Override
	@Transactional
	public Map<String, Object> saveComment(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		try {
			// MyBatis 인터셉터가 자동으로 sess_user_id를 주입하므로 직접 사용
			// 댓글 ID 생성
			String commentId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

			param.put("comment_id", commentId);
			// sess_user_id는 MyBatis 인터셉터가 자동 주입
			// user_id, user_name, crt_user_id, mdf_user_id는 XML에서 sess_user_id 사용
			param.put("del_yn", "N");

			int count = mapper.insertComment(param);

			if (count > 0) {
				result.put("result", "SUCCESS");
				result.put("comment_id", commentId);
			} else {
				result.put("result", "FAIL");
				result.put("message", "댓글 등록에 실패했습니다.");
			}
		} catch (Exception e) {
			result.put("result", "FAIL");
			result.put("message", "댓글 등록 중 오류가 발생했습니다: " + e.getMessage());
		}

		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> updateComment(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		try {
			// MyBatis 인터셉터가 자동으로 sess_user_id를 주입하므로 직접 사용
			// 댓글 작성자 확인은 XML의 WHERE 절에서 처리됨

			int count = mapper.updateComment(param);

			if (count > 0) {
				result.put("result", "SUCCESS");
			} else {
				result.put("result", "FAIL");
				result.put("message", "댓글 수정에 실패했습니다.");
			}
		} catch (Exception e) {
			result.put("result", "FAIL");
			result.put("message", "댓글 수정 중 오류가 발생했습니다: " + e.getMessage());
		}

		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> deleteComment(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		try {
			// MyBatis 인터셉터가 자동으로 sess_user_id를 주입하므로 직접 사용
			// 댓글 작성자 확인 및 관리자 권한 확인은 XML의 WHERE 절에서 처리됨
			param.put("del_yn", "Y");

			int count = mapper.deleteComment(param);

			if (count > 0) {
				result.put("result", "SUCCESS");
			} else {
				result.put("result", "FAIL");
				result.put("message", "댓글 삭제에 실패했습니다.");
			}
		} catch (Exception e) {
			result.put("result", "FAIL");
			result.put("message", "댓글 삭제 중 오류가 발생했습니다: " + e.getMessage());
		}

		return result;
	}
}

