package com.cf.community.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cf.community.mapper.InquiryMapper;
import com.cf.notification.service.NotificationService;
import com.smw.guild.mapper.GuildMapper;

@Service
@Primary
public class InquiryServiceImpl implements InquiryService {

	@Autowired
	private InquiryMapper mapper;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private GuildMapper guildMapper;

	@Override
	public Map<String, Object> getInquiryList(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		// 페이지네이션 계산
		int page = param.get("page") != null ? Integer.parseInt(param.get("page").toString()) : 1;
		int limit = param.get("limit") != null ? Integer.parseInt(param.get("limit").toString()) : 10;
		int offset = (page - 1) * limit;
		param.put("offset", offset);
		
		// 세션에서 사용자 정보 가져오기 (관리자 체크 및 자신의 문의만 조회)
		// MyBatis interceptor에서 sess_user_id를 설정하므로, 여기서는 관리자 여부만 체크
		// 관리자가 아니면 자신의 문의만 조회하도록 param에 user_id 설정
		// TODO: 세션에서 관리자 여부 확인 로직 추가 필요 (role_id가 'RL0001'인지 확인)
		// 현재는 관리자가 아닌 경우만 user_id를 설정하여 자신의 문의만 조회하도록 함
		// param에 is_admin이 없거나 "N"이면 user_id를 sess_user_id로 설정
		if (param.get("is_admin") == null || !"Y".equals(param.get("is_admin"))) {
			// sess_user_id는 MyBatis interceptor에서 설정되므로, 여기서는 user_id만 설정
			// param에 user_id가 없으면 sess_user_id를 사용 (XML에서 처리)
		}
		
		List<Map<String, ?>> list = mapper.selectInquiryList(param);
		int total = mapper.selectInquiryCount(param);
		
		result.put("list", list);
		result.put("total", total);
		result.put("page", page);
		result.put("limit", limit);
		
		return result;
	}

	@Override
	public Map<String, ?> getInquiryDetail(Map<String, Object> param) {
		return mapper.selectInquiryDtl(param);
	}

	@Override
	@Transactional
	public Map<String, Object> saveInquiry(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		mapper.insertInquiry(param);
		result.put("result", "SUCCESS");
		result.put("inquiry_id", param.get("inquiry_id"));
		
		// 관리자에게 알림 생성
		Map<String, Object> adminParam = new HashMap<>();
		adminParam.put("role_id", "RL0001");
		List<Map<String, ?>> admins = guildMapper.selectUsersByRole(adminParam);
		
		String inquiryId = param.get("inquiry_id") != null ? param.get("inquiry_id").toString() : null;
		String title = (String) param.get("title");
		
		for (Map<String, ?> admin : admins) {
			String adminId = (String) admin.get("user_id");
			if (adminId != null) {
				notificationService.createNotification(
					adminId,
					"INQUIRY_PENDING",
					"새로운 1대1 문의가 등록되었습니다",
					title != null ? title : "새로운 문의가 등록되었습니다.",
					inquiryId,
					"/inquiry",
					(String) param.get("crt_user_id")
				);
			}
		}
		
		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> answerInquiry(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		// 문의 상세 조회 (작성자 ID 확인용)
		Map<String, ?> inquiry = mapper.selectInquiryDtl(param);
		
		int count = mapper.updateInquiryAnswer(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
			
			// 문의 작성자에게 알림 생성
			if (inquiry != null) {
				String inquiryUserId = (String) inquiry.get("user_id");
				String inquiryId = param.get("inquiry_id") != null ? param.get("inquiry_id").toString() : null;
				String inquiryTitle = (String) inquiry.get("title");
				
				if (inquiryUserId != null) {
					notificationService.createNotification(
						inquiryUserId,
						"INQUIRY_ANSWERED",
						"1대1 문의에 답변이 등록되었습니다",
						inquiryTitle != null ? inquiryTitle + " 문의에 답변이 등록되었습니다." : "문의에 답변이 등록되었습니다.",
						inquiryId,
						"/inquiry",
						(String) param.get("upt_user_id")
					);
				}
			}
		} else {
			result.put("result", "FAIL");
			result.put("message", "답변 등록에 실패했습니다.");
		}
		
		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> deleteInquiry(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		int count = mapper.deleteInquiry(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
			result.put("message", "문의 삭제에 실패했습니다.");
		}
		
		return result;
	}
}

