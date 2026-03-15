package com.cf.notification.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationMapper {

	/**
	 * 알림 목록 조회
	 */
	List<Map<String, Object>> selectNotificationList(Map<String, Object> param);

	/**
	 * 알림 읽음 처리
	 */
	int updateNotificationRead(Map<String, Object> param);

	/**
	 * 모든 알림 읽음 처리
	 */
	int updateAllNotificationsRead(Map<String, Object> param);

	/**
	 * 알림 생성
	 */
	int insertNotification(Map<String, Object> param);
}

