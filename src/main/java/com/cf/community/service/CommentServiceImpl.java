package com.cf.community.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cf.community.mapper.CommentMapper;
import com.sysconf.interceptor.SessionThread;
import com.sysconf.security.AdminPrivilegeResolver;

@Service
@Primary
public class CommentServiceImpl implements CommentService {

	@Autowired
	private CommentMapper mapper;

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;

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
				result.put("message", "댓글이 수정되었습니다.");
			} else {
				result.put("result", "SUCCESS");
				result.put("message", "댓글이 수정되었습니다.");
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
			Map<String, Object> ui = new HashMap<>();
			ui.put("sess_user_id", param.get("sess_user_id"));
			ui.put("roles", Collections.emptyList());
			param.put("sess_is_admin", adminPrivilegeResolver.isAdminUser(ui) ? "Y" : "N");
			param.put("del_yn", "Y");

			int count = mapper.deleteComment(param);

			if (count > 0) {
				result.put("result", "SUCCESS");
				result.put("message", "댓글이 삭제되었습니다.");
			} else {
				result.put("result", "SUCCESS");
				result.put("message", "댓글이 삭제되었습니다.");
			}
		} catch (Exception e) {
			result.put("result", "FAIL");
			result.put("message", "댓글 삭제 중 오류가 발생했습니다: " + e.getMessage());
		}

		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> voteComment(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		try {
			String sessUserId = resolveSessUserId();
			if (sessUserId == null) {
				result.put("result", "FAIL");
				result.put("message", "로그인이 필요합니다.");
				return result;
			}
			param.put("sess_user_id", sessUserId);

			Object commentIdObj = param.get("comment_id");
			String commentId = commentIdObj != null ? String.valueOf(commentIdObj).trim() : "";
			if (commentId.isEmpty()) {
				result.put("result", "FAIL");
				result.put("message", "댓글 ID가 필요합니다.");
				return result;
			}
			param.put("comment_id", commentId);

			Object voteObj = param.get("vote");
			String vote = voteObj != null ? String.valueOf(voteObj).trim().toUpperCase() : "";
			if ("CLEAR".equals(vote) || vote.isEmpty()) {
				mapper.deleteCommentVote(param);
				result.put("result", "SUCCESS");
				return result;
			}
			if (!"UP".equals(vote) && !"DOWN".equals(vote)) {
				result.put("result", "FAIL");
				result.put("message", "vote는 UP, DOWN, CLEAR만 허용됩니다.");
				return result;
			}

			param.put("vote_type", vote);
			mapper.deleteCommentVote(param);
			mapper.insertCommentVote(param);
			result.put("result", "SUCCESS");
		} catch (Exception e) {
			result.put("result", "FAIL");
			result.put("message", "댓글 투표 중 오류가 발생했습니다: " + e.getMessage());
		}

		return result;
	}

	private String resolveSessUserId() {
		Map<String, Object> userInfo = SessionThread.SESSION_USER_INFO.get();
		if (userInfo == null || userInfo.get("sess_user_id") == null) {
			return null;
		}
		String uid = String.valueOf(userInfo.get("sess_user_id")).trim();
		if (uid.isEmpty() || "ANONYMOUS".equals(uid)) {
			return null;
		}
		return uid;
	}
}

