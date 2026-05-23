package com.cf.notification.rest;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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

	@SuppressWarnings("unchecked")
	private String getSessUserId(HttpServletRequest request) {
		Object attr = request.getAttribute("userInfo");
		if (attr instanceof Map) {
			Map<String, Object> userInfo = (Map<String, Object>) attr;
			Object v = userInfo.get("sess_user_id");
			return v != null ? v.toString().trim() : null;
		}
		return null;
	}

	private Map<String, Object> ensureParam(Map<String, Object> param) {
		return param != null ? param : new HashMap<>();
	}

	/**
	 * 알림 목록 조회
	 */
	@Operation(summary = "알림 목록 조회", description = "사용자의 알림 목록을 조회합니다.")
	@PostMapping("/list")
	public ResponseEntity<?> getNotificationList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String userId = getSessUserId(request);
		if (userId == null || userId.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
		}
		Map<String, Object> p = ensureParam(param);
		p.put("user_id", userId);
		Map<String, Object> result = notificationService.getNotificationList(p, session);
		return ResponseEntity.ok(result);
	}

	/**
	 * 알림 읽음 처리
	 */
	@Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 처리합니다.")
	@PostMapping("/read")
	public ResponseEntity<?> markNotificationRead(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String userId = getSessUserId(request);
		if (userId == null || userId.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "FAIL", "message", "로그인이 필요합니다."));
		}
		Map<String, Object> p = ensureParam(param);
		p.put("user_id", userId);
		Map<String, Object> result = notificationService.markNotificationRead(p, session);
		return ResponseEntity.ok(result);
	}

	/**
	 * 모든 알림 읽음 처리
	 */
	@Operation(summary = "모든 알림 읽음 처리", description = "사용자의 모든 알림을 읽음 처리합니다.")
	@PostMapping("/read-all")
	public ResponseEntity<?> markAllNotificationsRead(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String userId = getSessUserId(request);
		if (userId == null || userId.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "FAIL", "message", "로그인이 필요합니다."));
		}
		Map<String, Object> p = ensureParam(param);
		p.put("user_id", userId);
		Map<String, Object> result = notificationService.markAllNotificationsRead(p, session);
		return ResponseEntity.ok(result);
	}

	/**
	 * 알림 숨김(목록에서 제거)
	 */
	@Operation(summary = "알림 숨김", description = "알림을 목록에서 숨깁니다(soft delete).")
	@PostMapping("/dismiss")
	public ResponseEntity<?> dismissNotification(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		String userId = getSessUserId(request);
		if (userId == null || userId.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "FAIL", "message", "로그인이 필요합니다."));
		}
		Map<String, Object> p = ensureParam(param);
		p.put("user_id", userId);
		Map<String, Object> result = notificationService.dismissNotification(p, session);
		return ResponseEntity.ok(result);
	}
}

