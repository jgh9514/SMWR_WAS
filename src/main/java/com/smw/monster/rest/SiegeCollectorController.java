package com.smw.monster.rest;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smw.monster.dto.response.SiegeApiArchiveResponse;
import com.smw.monster.dto.response.SiegeBattleLogListResponse;
import com.smw.monster.dto.response.SiegeBattleReplayResponse;
import com.smw.monster.service.SiegeCollectorService;
import com.sysconf.security.AdminPrivilegeResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Siege Collector", description = "점령전 프록시 수집기 적재 데이터 조회")
@RestController
@RequestMapping("/api/v1/summonerswar/siege-collector")
@RequiredArgsConstructor
public class SiegeCollectorController {

	private final SiegeCollectorService siegeCollectorService;
	private final AdminPrivilegeResolver adminPrivilegeResolver;

	private Map<String, Object> getSessUserInfo(HttpServletRequest request) {
		Object attr = request != null ? request.getAttribute("userInfo") : null;
		if (attr instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>) attr;
			return m;
		}
		return null;
	}

	private ResponseEntity<Map<String, Object>> fail(String message, HttpStatus status) {
		Map<String, Object> body = new HashMap<>();
		body.put("result", "FAIL");
		body.put("message", message);
		return new ResponseEntity<>(body, status);
	}

	private ResponseEntity<?> requireLoginAndGuild(HttpServletRequest request, Map<String, Object> param) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		if (userInfo == null || userInfo.get("sess_user_id") == null) {
			return fail("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
		}
		if (userInfo.get("sess_guild_id") == null) {
			return fail("길드 가입이 필요합니다.", HttpStatus.FORBIDDEN);
		}
		if (param != null) {
			param.put("sess_user_id", userInfo.get("sess_user_id"));
			param.put("sess_guild_id", userInfo.get("sess_guild_id"));
		}
		return null;
	}

	private void applyGuildScope(HttpServletRequest request, Map<String, Object> param) {
		boolean viewAll = false;
		Object viewAllObj = param.get("view_all_guilds");
		if (viewAllObj instanceof Boolean b) {
			viewAll = b;
		} else if (viewAllObj != null) {
			viewAll = "true".equalsIgnoreCase(String.valueOf(viewAllObj)) || "Y".equalsIgnoreCase(String.valueOf(viewAllObj));
		}
		Map<String, Object> userInfo = getSessUserInfo(request);
		if (viewAll && userInfo != null && adminPrivilegeResolver.isAdminUser(userInfo)) {
			param.put("view_all_guilds", true);
		}
	}

	@Operation(summary = "매치 전투 로그 목록", description = "battle_log_list — battle_desc·replay_rid_ref 포함(수집기·업로드 공통).")
	@PostMapping("/battle-log-list")
	public ResponseEntity<?> battleLogList(@RequestBody Map<String, Object> param, HttpServletRequest httpRequest) {
		Map<String, Object> q = new HashMap<>();
		if (param != null) {
			q.putAll(param);
		}
		ResponseEntity<?> guard = requireLoginAndGuild(httpRequest, q);
		if (guard != null) {
			return guard;
		}
		applyGuildScope(httpRequest, q);
		try {
			SiegeBattleLogListResponse body = siegeCollectorService.getMatchBattleLogs(q);
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@Operation(summary = "전투 리플레이 조회", description = "siege_battle_replay_raw + payload(jsonb).")
	@PostMapping("/battle-replay")
	public ResponseEntity<?> battleReplay(@RequestBody Map<String, Object> param, HttpServletRequest httpRequest) {
		Map<String, Object> q = new HashMap<>();
		if (param != null) {
			q.putAll(param);
		}
		ResponseEntity<?> guard = requireLoginAndGuild(httpRequest, q);
		if (guard != null) {
			return guard;
		}
		applyGuildScope(httpRequest, q);
		long rid;
		try {
			rid = Long.parseLong(String.valueOf(q.get("rid")));
		} catch (Exception e) {
			return fail("rid 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
		}
		try {
			SiegeBattleReplayResponse body = siegeCollectorService.getBattleReplay(rid, q);
			if (body == null) {
				return fail("리플레이를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
			}
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@Operation(summary = "API 아카이브 최신 1건", description = "siege_collector_api_archive — Ranking/Summary 등 command별.")
	@PostMapping("/api-archive-latest")
	public ResponseEntity<?> apiArchiveLatest(@RequestBody Map<String, Object> param, HttpServletRequest httpRequest) {
		Map<String, Object> q = new HashMap<>();
		if (param != null) {
			q.putAll(param);
		}
		ResponseEntity<?> guard = requireLoginAndGuild(httpRequest, q);
		if (guard != null) {
			return guard;
		}
		applyGuildScope(httpRequest, q);
		try {
			SiegeApiArchiveResponse body = siegeCollectorService.getLatestApiArchive(q);
			if (body == null) {
				return fail("아카이브 데이터가 없습니다.", HttpStatus.NOT_FOUND);
			}
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}
}
