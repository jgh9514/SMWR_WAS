package com.cf.community.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cf.community.mapper.NoticeMapper;

@Service
public class NoticeServiceImpl implements NoticeService {

	@Autowired
	private NoticeMapper mapper;

	@Override
	public Map<String, Object> getNoticeList(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		// 페이지네이션 계산
		int page = param.get("page") != null ? Integer.parseInt(param.get("page").toString()) : 1;
		int limit = param.get("limit") != null ? Integer.parseInt(param.get("limit").toString()) : 10;
		int offset = (page - 1) * limit;
		param.put("offset", offset);
		
		List<Map<String, ?>> list = mapper.selectNoticeList(param);
		int total = mapper.selectNoticeCount(param);
		
		result.put("list", list);
		result.put("total", total);
		result.put("page", page);
		result.put("limit", limit);
		
		return result;
	}

	@Override
	public Map<String, ?> getNoticeDetail(Map<String, Object> param) {
		return mapper.selectNoticeDtl(param);
	}

	@Override
	@Transactional
	public Map<String, Object> saveNotice(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		if (param.get("notice_id") == null || "".equals(param.get("notice_id"))) {
			mapper.insertNotice(param);
			result.put("result", "SUCCESS");
			result.put("notice_id", param.get("notice_id"));
		} else {
			int count = mapper.updateNotice(param);
			if (count > 0) {
				result.put("result", "SUCCESS");
				result.put("notice_id", param.get("notice_id"));
			} else {
				result.put("result", "FAIL");
				result.put("message", "공지사항 수정에 실패했습니다.");
			}
		}
		
		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> deleteNotice(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		int count = mapper.deleteNotice(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
			result.put("message", "공지사항 삭제에 실패했습니다.");
		}
		
		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> increaseNoticeView(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		int count = mapper.increaseNoticeView(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
		}
		
		return result;
	}

	@Override
	public List<Map<String, ?>> getPopupNoticeList(Map<String, Object> param) {
		return mapper.selectPopupNoticeList(param);
	}

	@Override
	@Transactional
	public Map<String, Object> savePopupNoticeView(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		// 단순히 조회수만 증가
		int count = mapper.increaseNoticeView(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
			result.put("message", "조회수 증가에 실패했습니다.");
		}
		
		return result;
	}
}

