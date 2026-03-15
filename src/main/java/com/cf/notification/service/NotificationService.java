package com.cf.notification.service;

import java.util.Map;

import javax.servlet.http.HttpSession;

public interface NotificationService {
	
	/**
	 * 알림 목록 조회
	 */
	Map<String, Object> getNotificationList(Map<String, Object> param, HttpSession session);

	/**
	 * 알림 읽음 처리
	 */
	Map<String, Object> markNotificationRead(Map<String, Object> param, HttpSession session);

	/**
	 * 모든 알림 읽음 처리
	 */
	Map<String, Object> markAllNotificationsRead(Map<String, Object> param, HttpSession session);

	/**
	 * 알림 생성
	 * @param userId 알림을 받을 사용자 ID
	 * @param type 알림 타입
	 * @param title 알림 제목
	 * @param content 알림 내용
	 * @param relatedId 관련 ID (선택)
	 * @param relatedUrl 관련 URL (선택)
	 * @param crtUserId 생성자 ID
	 */
	void createNotification(String userId, String type, String title, String content, String relatedId, String relatedUrl, String crtUserId);
}

