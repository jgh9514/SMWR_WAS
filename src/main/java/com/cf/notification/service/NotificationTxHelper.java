package com.cf.notification.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cf.notification.mapper.NotificationMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 알림 INSERT 전용 — REQUIRES_NEW로 본 트랜잭션(PG aborted)과 분리.
 */
@Slf4j
@Service
public class NotificationTxHelper {

	@Autowired
	private NotificationMapper notificationMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertNotification(Map<String, Object> param) {
		try {
			notificationMapper.insertNotification(param);
		} catch (Exception e) {
			log.warn("알림 INSERT 실패(본 트랜잭션과 분리됨): type={}, user_id={}",
				param.get("type"), param.get("user_id"), e);
		}
	}
}
