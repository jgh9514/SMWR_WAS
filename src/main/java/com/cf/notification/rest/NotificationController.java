package com.cf.notification.rest;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cf.notification.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notification", description = "알림 관리 API")
@RestController
@RequestMapping("/api/v1/notification")
public class NotificationController {

	@Autowired
	private NotificationService notificationService;

	/**
	 * 알림 목록 조회
	 */
	@Operation(summary = "알림 목록 조회", description = "사용자의 알림 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getNotificationList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = notificationService.getNotificationList(param, session);
		return ResponseEntity.ok(result);
	}

	/**
	 * 알림 읽음 처리
	 */
	@Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 처리합니다.")
	@PostMapping("/read")
	public ResponseEntity<?> markNotificationRead(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = notificationService.markNotificationRead(param, session);
		return ResponseEntity.ok(result);
	}

	/**
	 * 모든 알림 읽음 처리
	 */
	@Operation(summary = "모든 알림 읽음 처리", description = "사용자의 모든 알림을 읽음 처리합니다.")
	@PostMapping("/read-all")
	public ResponseEntity<?> markAllNotificationsRead(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		Map<String, Object> result = notificationService.markAllNotificationsRead(param, session);
		return ResponseEntity.ok(result);
	}
}

