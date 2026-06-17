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
import com.sysconf.interceptor.SessionThread;
import com.sysconf.security.AdminPrivilegeResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class InquiryServiceImpl implements InquiryService {

	@Autowired
	private InquiryMapper mapper;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;

	@Override
	public Map<String, Object> getInquiryList(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();
		
		// 페이지네이션 계산
		int page = param.get("page") != null ? Integer.parseInt(param.get("page").toString()) : 1;
		int limit = param.get("limit") != null ? Integer.parseInt(param.get("limit").toString()) : 10;
		int offset = (page - 1) * limit;
		param.put("offset", offset);
		
		applySessionScope(param);
		
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
		Map<String, ?> detail = mapper.selectInquiryDtl(param);
		if (detail == null || !canAccessInquiry(detail)) {
			return null;
		}
		return detail;
	}

	@Override
	@Transactional
	public Map<String, Object> saveInquiry(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		if (resolveSessUserId(SessionThread.SESSION_USER_INFO.get()) == null) {
			result.put("result", "FAIL");
			result.put("message", "로그인이 필요합니다.");
			return result;
		}
		
		mapper.insertInquiry(param);
		result.put("result", "SUCCESS");
		result.put("message", "문의가 등록되었습니다.");
		result.put("inquiry_id", param.get("inquiry_id"));
		
		String inquiryId = param.get("inquiry_id") != null ? param.get("inquiry_id").toString() : null;
		String title = (String) param.get("title");
		
		try {
			for (String adminId : adminPrivilegeResolver.listConfiguredAdminUserIds()) {
				String creatorUserId = param.get("sess_user_id") != null
					? String.valueOf(param.get("sess_user_id"))
					: (param.get("crt_user_id") != null ? String.valueOf(param.get("crt_user_id")) : null);
				notificationService.createNotification(
					adminId,
					"INQUIRY_PENDING",
					"새로운 1대1 문의가 등록되었습니다",
					title != null ? title : "새로운 문의가 등록되었습니다.",
					inquiryId,
					"/inquiry",
					creatorUserId
				);
			}
		} catch (Exception e) {
			log.warn("문의 등록 알림 생성 실패 (문의 등록은 유지): inquiry_id={}", inquiryId, e);
		}
		
		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> answerInquiry(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		if (!isAdminSession()) {
			result.put("result", "FAIL");
			result.put("message", "답변 권한이 없습니다.");
			return result;
		}
		
		// 문의 상세 조회 (작성자 ID 확인용)
		Map<String, ?> inquiry = mapper.selectInquiryDtl(param);
		
		int count = mapper.updateInquiryAnswer(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
			result.put("message", "답변이 등록되었습니다.");
			
			if (inquiry != null) {
				String inquiryUserId = (String) inquiry.get("user_id");
				String inquiryId = param.get("inquiry_id") != null ? param.get("inquiry_id").toString() : null;
				String inquiryTitle = (String) inquiry.get("title");
				
				if (inquiryUserId != null) {
					try {
						String updaterUserId = param.get("sess_user_id") != null
							? String.valueOf(param.get("sess_user_id"))
							: (param.get("upt_user_id") != null ? String.valueOf(param.get("upt_user_id")) : null);
						notificationService.createNotification(
							inquiryUserId,
							"INQUIRY_ANSWERED",
							"1대1 문의에 답변이 등록되었습니다",
							inquiryTitle != null ? inquiryTitle + " 문의에 답변이 등록되었습니다." : "문의에 답변이 등록되었습니다.",
							inquiryId,
							"/inquiry",
							updaterUserId
						);
					} catch (Exception e) {
						log.warn("문의 답변 알림 생성 실패 (답변 등록은 유지): inquiry_id={}", inquiryId, e);
					}
				}
			}
		} else {
			Map<String, ?> again = mapper.selectInquiryDtl(param);
			if (again != null && again.get("answer") != null && !String.valueOf(again.get("answer")).trim().isEmpty()) {
				result.put("result", "SUCCESS");
				result.put("message", "답변이 등록되었습니다.");
			} else {
				result.put("result", "FAIL");
				result.put("message", "답변 등록에 실패했습니다.");
			}
		}
		
		return result;
	}

	@Override
	@Transactional
	public Map<String, Object> deleteInquiry(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		applySessionScope(param);
		
		int count = mapper.deleteInquiry(param);
		if (count > 0) {
			result.put("result", "SUCCESS");
			result.put("message", "문의가 삭제되었습니다.");
		} else {
			Map<String, ?> again = mapper.selectInquiryDtl(param);
			if (again == null) {
				result.put("result", "SUCCESS");
				result.put("message", "문의가 삭제되었습니다.");
			} else {
				result.put("result", "FAIL");
				result.put("message", "문의 삭제에 실패했습니다.");
			}
		}
		
		return result;
	}

	private void applySessionScope(Map<String, Object> param) {
		param.put("is_admin", isAdminSession() ? "Y" : "N");
	}

	private boolean isAdminSession() {
		return adminPrivilegeResolver.isAdminUser(buildAdminCheckMap(SessionThread.SESSION_USER_INFO.get()));
	}

	private boolean canAccessInquiry(Map<String, ?> inquiry) {
		if (isAdminSession()) {
			return true;
		}
		String sessUserId = resolveSessUserId(SessionThread.SESSION_USER_INFO.get());
		if (sessUserId == null) {
			return false;
		}
		Object ownerId = inquiry.get("user_id");
		return ownerId != null && sessUserId.equals(String.valueOf(ownerId));
	}

	private Map<String, Object> buildAdminCheckMap(Map<String, Object> userInfo) {
		Map<String, Object> adminCheck = new HashMap<>();
		if (userInfo == null) {
			return adminCheck;
		}
		adminCheck.put("sess_user_id", userInfo.get("sess_user_id"));
		adminCheck.put("roles", userInfo.get("sess_role"));
		return adminCheck;
	}

	private String resolveSessUserId(Map<String, Object> userInfo) {
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

