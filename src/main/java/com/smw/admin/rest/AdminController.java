package com.smw.admin.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smw.admin.service.AdminBatchService;
import com.smw.admin.service.AdminMonsterService;
import com.smw.admin.service.DashboardService;
import com.smw.admin.service.AdminPerfService;
import com.admin.log.service.LogService;
import com.sysconf.annotation.RequireAdmin;
import com.sysconf.constants.Constant;
import com.sysconf.security.AdminPrivilegeResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Dashboard", description = "관리자 대시보드 API")
@RequireAdmin
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

	/** 운영 개요: 배치·DB·메트릭·API 로그를 한 요청에서 병렬 조회 */
	private static final Executor ADMIN_OPS_OVERVIEW_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

	/** ops/overview 배치 진단 — 전체 1000건 스캔 대신 최근 N건만 (요약·실패 목록용) */
	private static final int OPS_OVERVIEW_BATCH_RUN_LIMIT = 200;

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private AdminMonsterService adminMonsterService;
	
	@Autowired
	private AdminPerfService adminPerfService;

	@Autowired
	private AdminBatchService adminBatchService;

	@Autowired
	private LogService logService;

	@Autowired
	private AdminPrivilegeResolver adminPrivilegeResolver;

	@Value("${smw.ops.thresholds.api-error-rate-warning-percent:3}")
	private double apiErrorRateWarningPercent;

	@Value("${smw.ops.thresholds.api-error-rate-critical-percent:10}")
	private double apiErrorRateCriticalPercent;

	@Value("${smw.ops.thresholds.api-slow-rate-warning-percent:5}")
	private double apiSlowRateWarningPercent;

	@Value("${smw.ops.thresholds.api-slow-rate-critical-percent:15}")
	private double apiSlowRateCriticalPercent;

	@SuppressWarnings("unchecked")
	private Map<String, Object> getSessUserInfo(HttpServletRequest request) {
		Object attr = request != null ? request.getAttribute("userInfo") : null;
		if (attr instanceof Map) {
			return (Map<String, Object>) attr;
		}
		return null;
	}

	private boolean isAdminUser(HttpServletRequest request) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		return userInfo != null && adminPrivilegeResolver.isAdminUser(userInfo);
	}
	
	private ResponseEntity<?> requireAdmin(HttpServletRequest request) {
		Map<String, Object> userInfo = getSessUserInfo(request);
		if (userInfo == null || userInfo.get("sess_user_id") == null) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "로그인이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
		}
		if (!isAdminUser(request)) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "관리자 권한이 필요합니다.");
			return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
		}
		return null;
	}

	/**
	 * 대시보드 통계 조회
	 */
	@Operation(summary = "대시보드 통계 조회", description = "관리자 대시보드의 통계 데이터를 조회합니다.")
	@PostMapping("/dashboard/stats")
	public ResponseEntity<?> getDashboardStats(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		Map<String, Object> result = dashboardService.getDashboardStats(param);
		return ResponseEntity.ok(result);
	}

	/**
	 * 몬스터 목록 조회 (관리자용)
	 */
	@Operation(summary = "몬스터 목록 조회", description = "관리자 페이지에서 몬스터 목록을 조회합니다.")
	@PostMapping("/monster/list")
	public ResponseEntity<?> getMonsterList(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		// 페이지네이션 파라미터 설정
		int page = param.get("page") != null ? Integer.parseInt(param.get("page").toString()) : 1;
		int limit = param.get("limit") != null ? Integer.parseInt(param.get("limit").toString()) : 20;
		int offset = (page - 1) * limit;
		
		param.put("limit", limit);
		param.put("offset", offset);
		
		List<Map<String, Object>> list = adminMonsterService.getMonsterList(param);
		int totalCount = adminMonsterService.getMonsterCount(param);
		
		Map<String, Object> result = new HashMap<>();
		result.put("list", list);
		result.put("totalCount", totalCount);
		result.put("page", page);
		result.put("limit", limit);
		
		return ResponseEntity.ok(result);
	}

	/**
	 * 몬스터 상세 정보 조회
	 */
	@Operation(summary = "몬스터 상세 정보 조회", description = "특정 몬스터의 상세 정보를 조회합니다.")
	@PostMapping("/monster/detail")
	public ResponseEntity<?> getMonsterDetail(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		String monsterId = param.get("monster_id") != null ? param.get("monster_id").toString() : null;
		
		if (monsterId == null || monsterId.isEmpty()) {
			Map<String, Object> error = new HashMap<>();
			error.put("error", "monster_id는 필수입니다.");
			return ResponseEntity.badRequest().body(error);
		}
		
		Map<String, Object> result = adminMonsterService.getMonsterDetail(monsterId);
		return ResponseEntity.ok(result);
	}

	/**
	 * 몬스터 정보 수정
	 */
	@Operation(summary = "몬스터 정보 수정", description = "몬스터 정보를 수정합니다.")
	@PostMapping("/monster/update")
	public ResponseEntity<?> updateMonster(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		String monsterId = param.get("monster_id") != null ? param.get("monster_id").toString() : null;
		
		if (monsterId == null || monsterId.isEmpty()) {
			Map<String, Object> error = new HashMap<>();
			error.put("error", "monster_id는 필수입니다.");
			return ResponseEntity.badRequest().body(error);
		}
		
		int result = adminMonsterService.updateMonster(param);
		
		Map<String, Object> response = new HashMap<>();
		if (result > 0) {
			response.put("success", true);
			response.put("message", "수정되었습니다.");
		} else {
			// UPDATE 0건(변경 없음)이어도 몬스터가 존재하면 멱등 성공
			Map<String, Object> existing = adminMonsterService.getMonsterDetail(monsterId);
			if (existing != null && !existing.isEmpty()) {
				response.put("success", true);
				response.put("message", "수정되었습니다.");
			} else {
				response.put("success", false);
				response.put("message", "수정에 실패했습니다.");
			}
		}
		
		return ResponseEntity.ok(response);
	}
	
	/**
	 * 운영 진단 - 통합 개요
	 */
	@Operation(summary = "운영 개요 조회", description = "배치 상태와 DB 성능 진단을 한 번에 조회합니다.")
	@PostMapping("/ops/overview")
	public ResponseEntity<?> getOpsOverview(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;

		Map<String, Object> query = param != null ? new HashMap<>(param) : new HashMap<>();
		Map<String, Object> overview = new HashMap<>();
		overview.put("result", "SUCCESS");

		Map<String, Object> batchQuery = buildOpsOverviewBatchQuery(query);
		Map<String, Object> apiLogQuery = buildOpsOverviewApiLogQuery(query);

		CompletableFuture<Map<String, Object>> fBatch = CompletableFuture.supplyAsync(() -> {
			Map<String, Object> part = new HashMap<>();
			try {
				part.put("batch", adminBatchService.getBatchDiagnostics(batchQuery));
				part.put("batch_status", "SUCCESS");
			} catch (Exception e) {
				part.put("batch_status", "FAIL");
				part.put("batch_error", e.getMessage());
			}
			return part;
		}, ADMIN_OPS_OVERVIEW_EXECUTOR);

		CompletableFuture<Map<String, Object>> fDb = CompletableFuture.supplyAsync(() -> {
			Map<String, Object> part = new HashMap<>();
			try {
				part.put("db", adminPerfService.getDiagnostics(new HashMap<>(query)));
				part.put("db_status", "SUCCESS");
			} catch (Exception e) {
				part.put("db_status", "FAIL");
				part.put("db_error", e.getMessage());
			}
			return part;
		}, ADMIN_OPS_OVERVIEW_EXECUTOR);

		CompletableFuture<Map<String, Object>> fMetrics = CompletableFuture.supplyAsync(() -> {
			Map<String, Object> part = new HashMap<>();
			try {
				part.put("metrics", dashboardService.getOpsMetricsSnapshot());
				part.put("metrics_status", "SUCCESS");
			} catch (Exception e) {
				part.put("metrics_status", "FAIL");
				part.put("metrics_error", e.getMessage());
			}
			return part;
		}, ADMIN_OPS_OVERVIEW_EXECUTOR);

		CompletableFuture<Map<String, Object>> fApiLogs = CompletableFuture.supplyAsync(() -> {
			Map<String, Object> part = new HashMap<>();
			try {
				part.put("api_logs", logService.getRecentApiDiagnostics(apiLogQuery));
				part.put("api_logs_status", "SUCCESS");
			} catch (Exception e) {
				part.put("api_logs_status", "FAIL");
				part.put("api_logs_error", e.getMessage());
			}
			return part;
		}, ADMIN_OPS_OVERVIEW_EXECUTOR);

		CompletableFuture.allOf(fBatch, fDb, fMetrics, fApiLogs).join();
		overview.putAll(fBatch.join());
		overview.putAll(fDb.join());
		overview.putAll(fMetrics.join());
		overview.putAll(fApiLogs.join());

		overview.put("health", buildOpsHealth(overview));

		return ResponseEntity.ok(overview);
	}

	private static Map<String, Object> buildOpsOverviewBatchQuery(Map<String, Object> base) {
		Map<String, Object> q = new HashMap<>(base);
		if (!q.containsKey("limit")) {
			q.put("limit", OPS_OVERVIEW_BATCH_RUN_LIMIT);
		}
		q.putIfAbsent("recent_limit", 20);
		q.putIfAbsent("failure_limit", 10);
		return q;
	}

	private static Map<String, Object> buildOpsOverviewApiLogQuery(Map<String, Object> base) {
		Map<String, Object> q = new HashMap<>(base);
		q.putIfAbsent("window_hours", 24);
		q.putIfAbsent("summary_limit", 10);
		return q;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> buildOpsHealth(Map<String, Object> overview) {
		Map<String, Object> health = new HashMap<>();
		Map<String, Object> metrics = overview.get("metrics") instanceof Map ? (Map<String, Object>) overview.get("metrics") : null;
		Map<String, Object> apiLogs = overview.get("api_logs") instanceof Map ? (Map<String, Object>) overview.get("api_logs") : null;

		String runtimeStatus = extractNestedStatus(metrics, "runtimeHealth");
		Map<String, Object> apiErrorRate = buildApiRateStatus(apiLogs, true);
		Map<String, Object> apiSlowRate = buildApiRateStatus(apiLogs, false);
		List<String> incidentTypes = buildIncidentTypes(overview, metrics, runtimeStatus, apiErrorRate, apiSlowRate);
		String overallStatus = maxStatus(
				runtimeStatus,
				String.valueOf(apiErrorRate.get("status")),
				String.valueOf(apiSlowRate.get("status")),
				componentStatus(overview.get("batch_status")),
				componentStatus(overview.get("db_status")),
				componentStatus(overview.get("metrics_status")),
				componentStatus(overview.get("api_logs_status")));

		health.put("runtime", runtimeStatus);
		health.put("apiErrorRate", apiErrorRate);
		health.put("apiSlowRate", apiSlowRate);
		health.put("status", overallStatus);
		health.put("incidentTypes", incidentTypes);
		health.put("primaryIncidentType", incidentTypes.isEmpty() ? "stable" : incidentTypes.get(0));
		health.put("incidentDetails", buildIncidentDetails(incidentTypes, overview, runtimeStatus, apiErrorRate, apiSlowRate));
		health.put("reasons", buildOpsReasons(overview, runtimeStatus, apiErrorRate, apiSlowRate));
		health.put("summaryMessage", buildOpsSummaryMessage(overallStatus));
		health.put("recommendedActions", buildRecommendedActions(overview, runtimeStatus, apiErrorRate, apiSlowRate));
		return health;
	}

	@SuppressWarnings("unchecked")
	private String extractNestedStatus(Map<String, Object> source, String key) {
		if (source == null) {
			return "unknown";
		}
		Object nested = source.get(key);
		if (!(nested instanceof Map)) {
			return "unknown";
		}
		Object status = ((Map<String, Object>) nested).get("status");
		return status != null ? String.valueOf(status) : "unknown";
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> buildApiRateStatus(Map<String, Object> apiLogs, boolean errorRate) {
		Map<String, Object> status = new HashMap<>();
		Map<String, Object> summary = apiLogs != null && apiLogs.get("summary") instanceof Map
				? (Map<String, Object>) apiLogs.get("summary") : null;
		double total = toDouble(summary != null ? summary.get("recent_count") : null);
		double numerator = toDouble(summary != null ? summary.get(errorRate ? "error_count" : "slow_count") : null);
		double rate = total > 0 ? round2((numerator / total) * 100.0) : 0.0;
		double warning = errorRate ? apiErrorRateWarningPercent : apiSlowRateWarningPercent;
		double critical = errorRate ? apiErrorRateCriticalPercent : apiSlowRateCriticalPercent;
		String metric = errorRate ? "apiErrorRatePercent" : "apiSlowRatePercent";
		String level = "ok";
		if (total <= 0) {
			level = "unknown";
		} else if (rate >= critical) {
			level = "critical";
		} else if (rate >= warning) {
			level = "warning";
		}
		status.put("metric", metric);
		status.put("value", rate);
		status.put("totalCount", round2(total));
		status.put("matchedCount", round2(numerator));
		status.put("warningThreshold", round2(warning));
		status.put("criticalThreshold", round2(critical));
		status.put("status", level);
		status.put("message", metric + "=" + rate + "% (" + level + ")");
		return status;
	}

	private double toDouble(Object value) {
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		if (value == null) {
			return 0.0;
		}
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException ignore) {
			return 0.0;
		}
	}

	private double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private String maxStatus(String... statuses) {
		String max = "ok";
		for (String status : statuses) {
			if ("critical".equalsIgnoreCase(status)) {
				return "critical";
			}
			if ("warning".equalsIgnoreCase(status)) {
				max = "warning";
			} else if ("unknown".equalsIgnoreCase(status) && "ok".equals(max)) {
				max = "unknown";
			}
		}
		return max;
	}

	private String componentStatus(Object value) {
		if ("FAIL".equalsIgnoreCase(String.valueOf(value))) {
			return "critical";
		}
		if (value == null) {
			return "unknown";
		}
		return "ok";
	}

	private List<String> buildOpsReasons(Map<String, Object> overview, String runtimeStatus,
			Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		List<String> reasons = new java.util.ArrayList<>();
		appendFailureReason(reasons, "batch", overview.get("batch_status"), overview.get("batch_error"));
		appendFailureReason(reasons, "db", overview.get("db_status"), overview.get("db_error"));
		appendFailureReason(reasons, "metrics", overview.get("metrics_status"), overview.get("metrics_error"));
		appendFailureReason(reasons, "api_logs", overview.get("api_logs_status"), overview.get("api_logs_error"));

		if ("warning".equalsIgnoreCase(runtimeStatus) || "critical".equalsIgnoreCase(runtimeStatus)) {
			reasons.add("runtimeHealth=" + runtimeStatus);
		}
		appendRateReason(reasons, apiErrorRate);
		appendRateReason(reasons, apiSlowRate);
		return reasons;
	}

	private List<Map<String, Object>> buildIncidentDetails(List<String> incidentTypes, Map<String, Object> overview,
			String runtimeStatus, Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		List<Map<String, Object>> details = new java.util.ArrayList<>();
		for (String incidentType : incidentTypes) {
			Map<String, Object> detail = new HashMap<>();
			detail.put("incidentType", incidentType);
			detail.put("title", resolveIncidentTitle(incidentType));
			detail.put("shortLabel", resolveIncidentShortLabel(incidentType));
			detail.put("owner", resolveIncidentOwner(incidentType));
			detail.put("priority", resolveIncidentPriority(incidentType, overview, runtimeStatus, apiErrorRate, apiSlowRate));
			detail.put("playbook", resolveIncidentPlaybook(incidentType));
			detail.put("message", resolveIncidentMessage(incidentType, overview, runtimeStatus, apiErrorRate, apiSlowRate));
			detail.put("badgeColor", resolveIncidentBadgeColor(incidentType, overview, runtimeStatus, apiErrorRate, apiSlowRate));
			detail.put("slaMinutes", resolveIncidentSlaMinutes(incidentType, overview, runtimeStatus, apiErrorRate, apiSlowRate));
			detail.put("autoRefreshSeconds", resolveIncidentAutoRefreshSeconds(incidentType, overview, runtimeStatus, apiErrorRate, apiSlowRate));
			detail.put("target", resolveIncidentTarget(incidentType));
			detail.put("method", "POST");
			detail.put("requestBody", buildIncidentRequestBody(incidentType, apiErrorRate, apiSlowRate));
			detail.put("deepLink", buildIncidentDeepLink(incidentType, apiErrorRate, apiSlowRate));
			details.add(detail);
		}
		return details;
	}

	private void appendFailureReason(List<String> reasons, String component, Object status, Object error) {
		if ("FAIL".equalsIgnoreCase(String.valueOf(status))) {
			String message = error != null && !String.valueOf(error).trim().isEmpty()
					? String.valueOf(error) : "unknown";
			reasons.add(component + " diagnostics failed: " + message);
		}
	}

	private void appendRateReason(List<String> reasons, Map<String, Object> rateStatus) {
		if (rateStatus == null) {
			return;
		}
		String status = String.valueOf(rateStatus.get("status"));
		if ("warning".equalsIgnoreCase(status) || "critical".equalsIgnoreCase(status)) {
			reasons.add(String.valueOf(rateStatus.get("message")));
		}
	}

	private String buildOpsSummaryMessage(String status) {
		if ("critical".equalsIgnoreCase(status)) {
			return "즉시 확인이 필요한 운영 이상 징후가 감지되었습니다.";
		}
		if ("warning".equalsIgnoreCase(status)) {
			return "주의가 필요한 운영 이상 징후가 감지되었습니다.";
		}
		if ("unknown".equalsIgnoreCase(status)) {
			return "일부 운영 지표를 아직 판단할 수 없습니다.";
		}
		return "운영 핵심 지표는 안정 범위입니다.";
	}

	private List<String> buildRecommendedActions(Map<String, Object> overview, String runtimeStatus,
			Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		List<String> actions = new java.util.ArrayList<>();
		if ("FAIL".equalsIgnoreCase(String.valueOf(overview.get("db_status")))) {
			actions.add("DB 진단 실패 원인을 먼저 확인하고 연결 상태 및 권한을 점검하세요.");
		}
		if ("FAIL".equalsIgnoreCase(String.valueOf(overview.get("batch_status")))) {
			actions.add("최근 배치 실패 로그와 배치 실행 이력을 확인하세요.");
		}
		if ("critical".equalsIgnoreCase(runtimeStatus) || "warning".equalsIgnoreCase(runtimeStatus)) {
			actions.add("JVM 메모리, CPU, 스레드, GC pause 지표를 우선 확인하세요.");
		}
		if (isAlertRate(apiErrorRate)) {
			actions.add("최근 에러 API 샘플과 trace_id 기준 상세 로그를 조회하세요.");
		}
		if (isAlertRate(apiSlowRate)) {
			actions.add("최근 슬로우 API 샘플과 DB 슬로우 쿼리 통계를 함께 확인하세요.");
		}
		if (actions.isEmpty()) {
			actions.add("즉시 대응이 필요한 이상 징후는 없습니다.");
		}
		return actions;
	}

	private boolean isAlertRate(Map<String, Object> rateStatus) {
		if (rateStatus == null) {
			return false;
		}
		String status = String.valueOf(rateStatus.get("status"));
		return "warning".equalsIgnoreCase(status) || "critical".equalsIgnoreCase(status);
	}

	private String resolveIncidentOwner(String incidentType) {
		if ("db_diagnostics_unavailable".equals(incidentType)) {
			return "dba";
		}
		if ("batch_diagnostics_failed".equals(incidentType)) {
			return "backend";
		}
		if ("metrics_unavailable".equals(incidentType)) {
			return "platform";
		}
		if ("api_logs_unavailable".equals(incidentType)) {
			return "backend";
		}
		if ("api_error_spike".equals(incidentType) || "slow_api_surge".equals(incidentType)) {
			return "backend";
		}
		if ("heap_pressure".equals(incidentType)
				|| "process_cpu_saturation".equals(incidentType)
				|| "system_cpu_saturation".equals(incidentType)
				|| "thread_pressure".equals(incidentType)
				|| "gc_pause_spike".equals(incidentType)) {
			return "platform";
		}
		return "backend";
	}

	private String resolveIncidentPlaybook(String incidentType) {
		if ("db_diagnostics_unavailable".equals(incidentType)) {
			return "check-db-diagnostics";
		}
		if ("batch_diagnostics_failed".equals(incidentType)) {
			return "review-batch-history";
		}
		if ("metrics_unavailable".equals(incidentType)) {
			return "check-actuator-metrics";
		}
		if ("api_logs_unavailable".equals(incidentType)) {
			return "check-api-log-storage";
		}
		if ("api_error_spike".equals(incidentType)) {
			return "investigate-api-errors";
		}
		if ("slow_api_surge".equals(incidentType)) {
			return "investigate-slow-apis";
		}
		if ("heap_pressure".equals(incidentType)) {
			return "inspect-jvm-heap";
		}
		if ("process_cpu_saturation".equals(incidentType) || "system_cpu_saturation".equals(incidentType)) {
			return "inspect-cpu-pressure";
		}
		if ("thread_pressure".equals(incidentType)) {
			return "inspect-thread-pressure";
		}
		if ("gc_pause_spike".equals(incidentType)) {
			return "inspect-gc-pauses";
		}
		return "general-investigation";
	}

	private String resolveIncidentPriority(String incidentType, Map<String, Object> overview,
			String runtimeStatus, Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		if ("critical".equalsIgnoreCase(runtimeStatus)
				&& ("heap_pressure".equals(incidentType)
				|| "process_cpu_saturation".equals(incidentType)
				|| "system_cpu_saturation".equals(incidentType)
				|| "thread_pressure".equals(incidentType)
				|| "gc_pause_spike".equals(incidentType))) {
			return "p1";
		}
		if ("critical".equalsIgnoreCase(String.valueOf(apiErrorRate.get("status"))) && "api_error_spike".equals(incidentType)) {
			return "p1";
		}
		if ("critical".equalsIgnoreCase(String.valueOf(apiSlowRate.get("status"))) && "slow_api_surge".equals(incidentType)) {
			return "p1";
		}
		if ("FAIL".equalsIgnoreCase(String.valueOf(overview.get("db_status"))) && "db_diagnostics_unavailable".equals(incidentType)) {
			return "p1";
		}
		if ("FAIL".equalsIgnoreCase(String.valueOf(overview.get("metrics_status"))) && "metrics_unavailable".equals(incidentType)) {
			return "p1";
		}
		return "p2";
	}

	private String resolveIncidentMessage(String incidentType, Map<String, Object> overview,
			String runtimeStatus, Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		if ("db_diagnostics_unavailable".equals(incidentType)) {
			return "DB 진단 정보를 가져오지 못했습니다: " + safeText(overview.get("db_error"));
		}
		if ("batch_diagnostics_failed".equals(incidentType)) {
			return "배치 진단 정보를 가져오지 못했습니다: " + safeText(overview.get("batch_error"));
		}
		if ("metrics_unavailable".equals(incidentType)) {
			return "메트릭 정보를 가져오지 못했습니다: " + safeText(overview.get("metrics_error"));
		}
		if ("api_logs_unavailable".equals(incidentType)) {
			return "API 로그 진단 정보를 가져오지 못했습니다: " + safeText(overview.get("api_logs_error"));
		}
		if ("api_error_spike".equals(incidentType)) {
			return safeText(apiErrorRate.get("message"));
		}
		if ("slow_api_surge".equals(incidentType)) {
			return safeText(apiSlowRate.get("message"));
		}
		if ("heap_pressure".equals(incidentType)
				|| "process_cpu_saturation".equals(incidentType)
				|| "system_cpu_saturation".equals(incidentType)
				|| "thread_pressure".equals(incidentType)
				|| "gc_pause_spike".equals(incidentType)) {
			return "runtimeHealth=" + runtimeStatus;
		}
		return "운영 이상 징후가 감지되었습니다.";
	}

	private String resolveIncidentTitle(String incidentType) {
		if ("db_diagnostics_unavailable".equals(incidentType)) return "DB 진단 조회 실패";
		if ("batch_diagnostics_failed".equals(incidentType)) return "배치 진단 실패";
		if ("metrics_unavailable".equals(incidentType)) return "메트릭 수집 불가";
		if ("api_logs_unavailable".equals(incidentType)) return "API 로그 진단 불가";
		if ("api_error_spike".equals(incidentType)) return "API 에러 급증";
		if ("slow_api_surge".equals(incidentType)) return "슬로우 API 급증";
		if ("heap_pressure".equals(incidentType)) return "JVM 힙 압박";
		if ("process_cpu_saturation".equals(incidentType)) return "프로세스 CPU 포화";
		if ("system_cpu_saturation".equals(incidentType)) return "시스템 CPU 포화";
		if ("thread_pressure".equals(incidentType)) return "스레드 압박";
		if ("gc_pause_spike".equals(incidentType)) return "GC Pause 급증";
		return "운영 이상 징후";
	}

	private String resolveIncidentShortLabel(String incidentType) {
		if ("db_diagnostics_unavailable".equals(incidentType)) return "DB";
		if ("batch_diagnostics_failed".equals(incidentType)) return "배치";
		if ("metrics_unavailable".equals(incidentType)) return "메트릭";
		if ("api_logs_unavailable".equals(incidentType)) return "API 로그";
		if ("api_error_spike".equals(incidentType)) return "API 에러";
		if ("slow_api_surge".equals(incidentType)) return "슬로우 API";
		if ("heap_pressure".equals(incidentType)) return "Heap";
		if ("process_cpu_saturation".equals(incidentType)) return "Process CPU";
		if ("system_cpu_saturation".equals(incidentType)) return "System CPU";
		if ("thread_pressure".equals(incidentType)) return "Thread";
		if ("gc_pause_spike".equals(incidentType)) return "GC";
		return "운영";
	}

	private String resolveIncidentBadgeColor(String incidentType, Map<String, Object> overview,
			String runtimeStatus, Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		String priority = resolveIncidentPriority(incidentType, overview, runtimeStatus, apiErrorRate, apiSlowRate);
		if ("p1".equalsIgnoreCase(priority)) {
			return "red";
		}
		if ("api_error_spike".equals(incidentType)
				|| "slow_api_surge".equals(incidentType)
				|| "batch_diagnostics_failed".equals(incidentType)
				|| "db_diagnostics_unavailable".equals(incidentType)) {
			return "orange";
		}
		if ("metrics_unavailable".equals(incidentType) || "api_logs_unavailable".equals(incidentType)) {
			return "purple";
		}
		return "yellow";
	}

	private int resolveIncidentSlaMinutes(String incidentType, Map<String, Object> overview,
			String runtimeStatus, Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		String priority = resolveIncidentPriority(incidentType, overview, runtimeStatus, apiErrorRate, apiSlowRate);
		if ("p1".equalsIgnoreCase(priority)) {
			return 15;
		}
		if ("metrics_unavailable".equals(incidentType) || "api_logs_unavailable".equals(incidentType)) {
			return 30;
		}
		return 60;
	}

	private int resolveIncidentAutoRefreshSeconds(String incidentType, Map<String, Object> overview,
			String runtimeStatus, Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		String priority = resolveIncidentPriority(incidentType, overview, runtimeStatus, apiErrorRate, apiSlowRate);
		if ("p1".equalsIgnoreCase(priority)) {
			return 15;
		}
		if ("api_error_spike".equals(incidentType)
				|| "slow_api_surge".equals(incidentType)
				|| "heap_pressure".equals(incidentType)
				|| "process_cpu_saturation".equals(incidentType)
				|| "system_cpu_saturation".equals(incidentType)
				|| "thread_pressure".equals(incidentType)
				|| "gc_pause_spike".equals(incidentType)) {
			return 30;
		}
		return 60;
	}

	private String resolveIncidentTarget(String incidentType) {
		if ("db_diagnostics_unavailable".equals(incidentType)) {
			return "/api/v1/admin/perf/slow-queries";
		}
		if ("batch_diagnostics_failed".equals(incidentType)) {
			return "/api/v1/admin/batch/diagnostics";
		}
		if ("metrics_unavailable".equals(incidentType)) {
			return "/api/v1/admin/ops/overview";
		}
		if ("api_logs_unavailable".equals(incidentType)
				|| "api_error_spike".equals(incidentType)
				|| "slow_api_surge".equals(incidentType)) {
			return "/api/v1/common/sm/api-his";
		}
		return "/api/v1/admin/ops/overview";
	}

	private Map<String, Object> buildIncidentRequestBody(String incidentType,
			Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		Map<String, Object> body = new HashMap<>();
		body.put("limit", 100);

		if ("api_error_spike".equals(incidentType)) {
			body.put("error_only", true);
			body.put("offset", 0);
			return body;
		}
		if ("slow_api_surge".equals(incidentType)) {
			body.put("slow_only", true);
			body.put("slow_threshold_ms", toLong(apiSlowRate != null ? apiSlowRate.get("warningThreshold") : null, 1000L));
			body.put("offset", 0);
			return body;
		}
		if ("api_logs_unavailable".equals(incidentType)) {
			body.put("offset", 0);
			return body;
		}
		if ("db_diagnostics_unavailable".equals(incidentType)) {
			body.put("limit", 20);
			return body;
		}
		return new HashMap<>();
	}

	private String buildIncidentDeepLink(String incidentType,
			Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		if ("api_error_spike".equals(incidentType)) {
			return "/admin/apihislist?incident=api_error_spike&error_only=true";
		}
		if ("slow_api_surge".equals(incidentType)) {
			long threshold = toLong(apiSlowRate != null ? apiSlowRate.get("warningThreshold") : null, 1000L);
			return "/admin/apihislist?incident=slow_api_surge&slow_only=true&slow_threshold_ms=" + threshold;
		}
		if ("batch_diagnostics_failed".equals(incidentType)) {
			return "/admin/batch?incident=batch_diagnostics_failed&filter=failed";
		}
		if ("db_diagnostics_unavailable".equals(incidentType)) {
			return "/admin/query-perf?incident=db_diagnostics_unavailable&tab=slow&min_mean_ms=200&order_by=mean_ms";
		}
		if ("metrics_unavailable".equals(incidentType)) {
			return "/admin?incident=metrics_unavailable";
		}
		if ("api_logs_unavailable".equals(incidentType)) {
			return "/admin/apihislist?incident=api_logs_unavailable";
		}
		if ("heap_pressure".equals(incidentType)
				|| "process_cpu_saturation".equals(incidentType)
				|| "system_cpu_saturation".equals(incidentType)
				|| "thread_pressure".equals(incidentType)
				|| "gc_pause_spike".equals(incidentType)) {
			return "/admin?incident=" + incidentType + "&view=runtime-health";
		}
		return "/admin";
	}

	private String safeText(Object value) {
		if (value == null) {
			return "unknown";
		}
		String text = String.valueOf(value).trim();
		return text.isEmpty() ? "unknown" : text;
	}

	private long toLong(Object value, long defaultValue) {
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		if (value == null) {
			return defaultValue;
		}
		try {
			return Long.parseLong(String.valueOf(value));
		} catch (NumberFormatException ignore) {
			return defaultValue;
		}
	}

	@SuppressWarnings("unchecked")
	private List<String> buildIncidentTypes(Map<String, Object> overview, Map<String, Object> metrics, String runtimeStatus,
			Map<String, Object> apiErrorRate, Map<String, Object> apiSlowRate) {
		List<String> incidentTypes = new java.util.ArrayList<>();

		if ("FAIL".equalsIgnoreCase(String.valueOf(overview.get("db_status")))) {
			incidentTypes.add("db_diagnostics_unavailable");
		}
		if ("FAIL".equalsIgnoreCase(String.valueOf(overview.get("batch_status")))) {
			incidentTypes.add("batch_diagnostics_failed");
		}
		if ("FAIL".equalsIgnoreCase(String.valueOf(overview.get("metrics_status")))) {
			incidentTypes.add("metrics_unavailable");
		}
		if ("FAIL".equalsIgnoreCase(String.valueOf(overview.get("api_logs_status")))) {
			incidentTypes.add("api_logs_unavailable");
		}
		if (isAlertRate(apiErrorRate)) {
			incidentTypes.add("api_error_spike");
		}
		if (isAlertRate(apiSlowRate)) {
			incidentTypes.add("slow_api_surge");
		}
		if ("warning".equalsIgnoreCase(runtimeStatus) || "critical".equalsIgnoreCase(runtimeStatus)) {
			Object runtimeHealthObj = metrics != null ? metrics.get("runtimeHealth") : null;
			if (runtimeHealthObj instanceof Map) {
				Object runtimeIncidentTypes = ((Map<String, Object>) runtimeHealthObj).get("incidentTypes");
				if (runtimeIncidentTypes instanceof List) {
					for (Object item : (List<?>) runtimeIncidentTypes) {
						String value = item != null ? String.valueOf(item) : "";
						if (!value.isEmpty() && !incidentTypes.contains(value)) {
							incidentTypes.add(value);
						}
					}
				}
			}
			if (incidentTypes.isEmpty()) {
				incidentTypes.add("runtime_pressure");
			}
		}
		return incidentTypes;
	}

	/**
	 * 운영 진단 - 최근 배치 실행 요약
	 */
	@Operation(summary = "배치 실행 진단", description = "최근 배치 실행 요약, 최근 실패 목록, 최근 실행 로그 미리보기를 조회합니다.")
	@PostMapping("/batch/diagnostics")
	public ResponseEntity<?> getBatchDiagnostics(@RequestBody(required = false) Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			Map<String, Object> diagnostics = adminBatchService.getBatchDiagnostics(param);
			Map<String, Object> result = new HashMap<>();
			result.put("result", "SUCCESS");
			result.put("diagnostics", diagnostics);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "배치 진단 정보를 조회할 수 없습니다.");
			body.put("error", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * 운영 진단 - 배치 실행 상세
	 */
	@Operation(summary = "배치 실행 상세", description = "특정 배치 실행(runSn)의 상세 로그를 조회합니다.")
	@PostMapping("/batch/run-detail/{runSn}")
	public ResponseEntity<?> getBatchRunDetail(@PathVariable Long runSn, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			Map<String, Object> detail = adminBatchService.getBatchRunDetail(runSn);
			if (!Boolean.TRUE.equals(detail.get("found"))) {
				Map<String, Object> body = new HashMap<>();
				body.put("result", "FAIL");
				body.put("message", "배치 실행 이력을 찾을 수 없습니다.");
				return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
			}
			Map<String, Object> result = new HashMap<>();
			result.put("result", "SUCCESS");
			result.putAll(detail);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "배치 실행 상세를 조회할 수 없습니다.");
			body.put("error", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * DB 성능(쿼리) - 느린 쿼리 TOP 조회
	 */
	@Operation(summary = "느린 쿼리 TOP 조회", description = "pg_stat_statements 기반 느린 쿼리 TOP을 조회합니다.")
	@PostMapping("/perf/slow-queries")
	public ResponseEntity<?> getSlowQueries(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			List<Map<String, Object>> list = adminPerfService.getSlowQueries(param);
			Map<String, Object> result = new HashMap<>();
			result.put("list", list);
			result.put("source", "pg_stat_statements");
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> diag = null;
			try {
				diag = adminPerfService.getDiagnostics(new HashMap<>());
			} catch (Exception ignore) {
				// ignore
			}
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			body.put("message", "슬로우 쿼리 통계를 조회할 수 없습니다. (pg_stat_statements 미설치/권한/버전 확인 필요)\n원인: " + detail);
			body.put("diagnostics", diag);
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * DB 성능(쿼리) - 현재 실행중 쿼리 조회
	 */
	@Operation(summary = "현재 실행중 쿼리 조회", description = "pg_stat_activity 기반 현재 실행중(장시간) 쿼리를 조회합니다.")
	@PostMapping("/perf/running-queries")
	public ResponseEntity<?> getRunningQueries(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			List<Map<String, Object>> list = adminPerfService.getRunningQueries(param);
			Map<String, Object> result = new HashMap<>();
			result.put("list", list);
			result.put("source", "pg_stat_activity");
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "실행중 쿼리 목록을 조회할 수 없습니다.");
			body.put("error", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	/**
	 * DB 성능(쿼리) - pg_stat_statements 진단
	 */
	@Operation(summary = "쿼리 성능 진단", description = "pg_stat_statements 설치/설정 상태를 진단합니다.")
	@PostMapping("/perf/diagnostics")
	public ResponseEntity<?> getPerfDiagnostics(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			Map<String, Object> diag = adminPerfService.getDiagnostics(param);
			Map<String, Object> result = new HashMap<>();
			result.put("result", "SUCCESS");
			result.put("diagnostics", diag);
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "진단 정보를 조회할 수 없습니다.");
			body.put("error", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
	
	/**
	 * DB 성능(쿼리) - 누적 통계 리셋
	 */
	@Operation(summary = "쿼리 통계 리셋", description = "pg_stat_statements 누적 통계를 초기화합니다.")
	@PostMapping("/perf/reset")
	public ResponseEntity<?> resetQueryStats(@RequestBody Map<String, Object> param, HttpSession session, HttpServletRequest request) {
		ResponseEntity<?> guard = requireAdmin(request);
		if (guard != null) return guard;
		try {
			adminPerfService.resetQueryStats(param);
			Map<String, Object> result = new HashMap<>();
			result.put("result", "SUCCESS");
			return ResponseEntity.ok(result);
		} catch (Exception e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", "FAIL");
			body.put("message", "쿼리 통계를 리셋할 수 없습니다. (권한 확인 필요)");
			body.put("error", e.getMessage());
			return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}

