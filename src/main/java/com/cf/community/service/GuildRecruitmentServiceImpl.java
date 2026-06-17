package com.cf.community.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cf.community.mapper.GuildRecruitmentMapper;
import com.sysconf.security.AdminPrivilegeResolver;

@Service
@Primary
public class GuildRecruitmentServiceImpl implements GuildRecruitmentService {

	@Autowired
	private GuildRecruitmentMapper mapper;

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;

	@Override
	public Map<String, Object> getList(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		int page = param.get("page") != null ? Integer.parseInt(param.get("page").toString()) : 1;
		int limit = param.get("limit") != null ? Integer.parseInt(param.get("limit").toString()) : 20;
		int offset = (page - 1) * limit;
		param.put("offset", offset);
		param.put("limit", limit);

		List<Map<String, ?>> list = mapper.selectGuildRecruitmentList(param);
		int total = mapper.selectGuildRecruitmentCount(param);
		result.put("list", list);
		result.put("total", total);
		result.put("page", page);
		result.put("limit", limit);
		return result;
	}

	@Override
	public Map<String, ?> getDetail(Map<String, Object> param) {
		return mapper.selectGuildRecruitmentDtl(param);
	}

	@Override
	@Transactional
	public Map<String, Object> save(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		Object postId = param.get("post_id");
		if (postId != null && !postId.toString().isBlank()) {
			int u = mapper.updateGuildRecruitment(param);
			if (u == 0) {
				Map<String, ?> existing = mapper.selectGuildRecruitmentDtl(param);
				if (existing == null) {
					result.put("result", "FAIL");
					result.put("message", "수정 권한이 없거나 게시글을 찾을 수 없습니다.");
					return result;
				}
			}
			result.put("result", "SUCCESS");
			result.put("post_id", postId);
			result.put("message", "수정되었습니다.");
			return result;
		}
		mapper.insertGuildRecruitment(param);
		result.put("result", "SUCCESS");
		result.put("post_id", param.get("post_id"));
		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> delete(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		String sessUser = param.get("sess_user_id") != null ? String.valueOf(param.get("sess_user_id")) : null;
		boolean admin = sessUser != null && adminPrivilegeResolver.listConfiguredAdminUserIds().contains(sessUser);
		if (admin) {
			param.put("is_admin", "Y");
		}
		int d = mapper.deleteGuildRecruitment(param);
		if (d == 0) {
			Map<String, ?> existing = mapper.selectGuildRecruitmentDtl(param);
			if (existing != null) {
				result.put("result", "FAIL");
				result.put("message", "삭제 권한이 없거나 게시글을 찾을 수 없습니다.");
				return result;
			}
		}
		result.put("result", "SUCCESS");
		result.put("message", "삭제되었습니다.");
		return result;
	}
}
