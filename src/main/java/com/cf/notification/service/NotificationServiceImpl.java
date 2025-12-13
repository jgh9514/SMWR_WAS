package com.cf.notification.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.cf.notification.mapper.NotificationMapper;

@Service
@Primary
public class NotificationServiceImpl implements NotificationService {

	@Autowired
	private NotificationMapper notificationMapper;

	@Override
	public Map<String, Object> getNotificationList(Map<String, Object> param, HttpSession session) {
		Map<String, Object> result = new HashMap<>();

		// 알림 목록 조회
		List<Map<String, Object>> list = notificationMapper.selectNotificationList(param);

		// 읽지 않은 알림 수 계산
		long unreadCount = list.stream()
			.filter(item -> {
				Object isRead = item.get("is_read");
				String isReadStr = isRead != null ? isRead.toString() : "N";
				return !"Y".equals(isReadStr);
			})
			.count();

		result.put("list", list);
		result.put("unreadCount", (int) unreadCount);
		result.put("total", list.size());

		return result;
	}

	@Override
	public Map<String, Object> markNotificationRead(Map<String, Object> param, HttpSession session) {
		Map<String, Object> result = new HashMap<>();

		// 알림 읽음 처리
		int updated = notificationMapper.updateNotificationRead(param);
		
		if (updated > 0) {
			result.put("result", "SUCCESS");
		} else {
			result.put("result", "FAIL");
			result.put("message", "알림을 찾을 수 없습니다.");
		}

		return result;
	}

	@Override
	public Map<String, Object> markAllNotificationsRead(Map<String, Object> param, HttpSession session) {
		Map<String, Object> result = new HashMap<>();

		// 모든 알림 읽음 처리
		int updated = notificationMapper.updateAllNotificationsRead(param);
		
		result.put("result", "SUCCESS");
		result.put("updatedCount", updated);

		return result;
	}

	@Override
	public void createNotification(String userId, String type, String title, String content, String relatedId, String relatedUrl, String crtUserId) {
		Map<String, Object> param = new HashMap<>();
		param.put("user_id", userId);
		param.put("type", type);
		param.put("title", title);
		param.put("content", content);
		param.put("related_id", relatedId);
		param.put("related_url", relatedUrl);
		param.put("crt_user_id", crtUserId);
		
		notificationMapper.insertNotification(param);
	}
}

