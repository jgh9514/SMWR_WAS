package com.cf.notification.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cf.notification.mapper.NotificationMapper;

/**
 * 알림 INSERT 전용 — REQUIRES_NEW로 본 트랜잭션(PG aborted)과 분리.
 */
@Service
public class NotificationTxHelper {

	@Autowired
	private NotificationMapper notificationMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertNotification(Map<String, Object> param) {
		notificationMapper.insertNotification(param);
	}
}
