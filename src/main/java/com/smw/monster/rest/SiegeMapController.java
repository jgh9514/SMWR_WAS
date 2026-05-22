package com.smw.monster.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smw.monster.dto.request.SiegeMapSnapshotUploadRequest;
import com.smw.monster.dto.response.SiegeMapBaseDefenseResponse;
import com.smw.monster.service.SiegeMapService;
import com.sysconf.security.AdminPrivilegeResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Siege Map", description = "점령전 실시간 지도 스냅샷")
@RestController
@RequestMapping("/api/v1/summonerswar/siege-map")
@RequiredArgsConstructor
public class SiegeMapController {

	private final SiegeMapService siegeMapService;
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

	private ResponseEntity<Map<String, Object>> fail(String message, HttpStatus status) {
		Map<String, Object> body = new HashMap<>();
		body.put("result", "FAIL");
		body.put("message", message);
		return new ResponseEntity<>(body, status);
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
			param.remove("sess_guild_id");
		}
	}

	@Operation(summary = "MatchupInfo 스냅샷 적재", description = "GetGuildSiegeMatchupInfo 응답 JSON 저장. 프론트 파일 업로드 없음 — 배치·DB 파이프라인용.")
	@PostMapping("/snapshot-upload")
	public ResponseEntity<?> uploadSnapshot(@RequestBody SiegeMapSnapshotUploadRequest request, HttpServletRequest httpRequest) {
		Map<String, Object> param = new HashMap<>();
		ResponseEntity<?> guard = requireLoginAndGuild(httpRequest, param);
		if (guard != null) {
			return guard;
		}
		try {
			Map<String, Object> result = siegeMapService.ingestMatchupSnapshot(request);
			return ResponseEntity.ok(result);
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@Operation(summary = "지도 스냅샷 조회", description = "match_id·snapshot_id(선택)로 거점·길드 스냅샷을 조회합니다.")
	@PostMapping("/view")
	public ResponseEntity<?> getMapView(@RequestBody Map<String, Object> param, HttpServletRequest httpRequest) {
		ResponseEntity<?> guard = requireLoginAndGuild(httpRequest, param);
		if (guard != null) {
			return guard;
		}
		String matchId = param.get("match_id") != null ? String.valueOf(param.get("match_id")) : null;
		Long snapshotId = null;
		if (param.get("snapshot_id") != null) {
			try {
				snapshotId = Long.parseLong(String.valueOf(param.get("snapshot_id")));
			} catch (NumberFormatException ignored) {
				return fail("snapshot_id 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
			}
		}
		var sessGuildId = param.get("sess_guild_id");
		var myGuildId = sessGuildId != null ? String.valueOf(sessGuildId) : null;
		Map<String, Object> view = siegeMapService.getMapView(matchId, snapshotId, myGuildId);
		if (view == null) {
			return fail("매치를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
		}
		return ResponseEntity.ok(view);
	}

	@Operation(summary = "지도 매치 히스토리 목록", description = "스냅샷이 있는 점령전 매치 목록(길드 참여 기준).")
	@PostMapping("/history-list")
	public ResponseEntity<?> historyList(@RequestBody Map<String, Object> param, HttpServletRequest httpRequest) {
		Map<String, Object> q = new HashMap<>();
		if (param != null) {
			q.putAll(param);
		}
		ResponseEntity<?> guard = requireLoginAndGuild(httpRequest, q);
		if (guard != null) {
			return guard;
		}
		applyGuildScope(httpRequest, q);
		int paging = parseInt(q.get("paging"), 10);
		int offset = parseOffset(q);
		q.put("limit", paging);
		q.put("offset", offset);
		List<Map<String, ?>> list = siegeMapService.getMatchHistory(q);
		Map<String, Object> countParam = new HashMap<>(q);
		countParam.remove("limit");
		countParam.remove("offset");
		int total = siegeMapService.getMatchHistoryCount(countParam);
		int totalPage = paging > 0 ? (int) Math.ceil(total / (double) paging) : 0;
		Map<String, Object> resp = new HashMap<>();
		resp.put("list", list);
		resp.put("totalPage", totalPage);
		resp.put("totalCount", total);
		return ResponseEntity.ok(resp);
	}

	@Operation(summary = "거점 방덱 조회", description = "GetGuildSiegeBaseDefenseUnitList 스냅샷(해당 시점 이전 최신) + Matchup 거점 상태.")
	@PostMapping("/base-defense")
	public ResponseEntity<?> baseDefense(@RequestBody Map<String, Object> param, HttpServletRequest httpRequest) {
		ResponseEntity<?> guard = requireLoginAndGuild(httpRequest, param);
		if (guard != null) {
			return guard;
		}
		String matchId = param.get("match_id") != null ? String.valueOf(param.get("match_id")) : null;
		if (matchId == null || matchId.isBlank()) {
			return fail("match_id가 필요합니다.", HttpStatus.BAD_REQUEST);
		}
		int baseNumber;
		try {
			baseNumber = Integer.parseInt(String.valueOf(param.get("base_number")));
		} catch (NumberFormatException e) {
			return fail("base_number 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
		}
		Long snapshotId = null;
		if (param.get("snapshot_id") != null) {
			try {
				snapshotId = Long.parseLong(String.valueOf(param.get("snapshot_id")));
			} catch (NumberFormatException e) {
				return fail("snapshot_id 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
			}
		}
		try {
			SiegeMapBaseDefenseResponse body = siegeMapService.getBaseDefense(matchId, baseNumber, snapshotId);
			return ResponseEntity.ok(body);
		} catch (IllegalArgumentException e) {
			return fail(e.getMessage(), HttpStatus.BAD_REQUEST);
		}
	}

	@Operation(summary = "거점 레이아웃·이미지 마스터", description = "지도 좌표(slot_no 0=본진)·4성/5성·상태별 아이콘 경로. 정적 마스터(길드 스코프 없음).")
	@PostMapping("/layout-master")
	public ResponseEntity<?> layoutMaster() {
		return ResponseEntity.ok(siegeMapService.getLayoutMaster());
	}

	@Operation(summary = "매치 스냅샷 타임라인", description = "재생/슬라이더용 captured_at 목록.")
	@PostMapping("/timeline")
	public ResponseEntity<?> timeline(@RequestBody Map<String, Object> param, HttpServletRequest httpRequest) {
		ResponseEntity<?> guard = requireLoginAndGuild(httpRequest, param);
		if (guard != null) {
			return guard;
		}
		String matchId = param.get("match_id") != null ? String.valueOf(param.get("match_id")) : null;
		if (matchId == null || matchId.isBlank()) {
			return fail("match_id가 필요합니다.", HttpStatus.BAD_REQUEST);
		}
		return ResponseEntity.ok(siegeMapService.getSnapshotTimeline(matchId));
	}

	private static int parseInt(Object o, int defaultVal) {
		if (o == null) {
			return defaultVal;
		}
		try {
			return Integer.parseInt(String.valueOf(o));
		} catch (NumberFormatException e) {
			return defaultVal;
		}
	}

	private static int parseOffset(Map<String, Object> q) {
		if (q.get("offset") != null) {
			return parseInt(q.get("offset"), 0);
		}
		if (q.get("page") != null) {
			int page = parseInt(q.get("page"), 1);
			int paging = parseInt(q.get("paging"), 10);
			return Math.max(0, (page - 1) * paging);
		}
		return 0;
	}
}
