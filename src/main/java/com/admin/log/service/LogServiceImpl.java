package com.admin.log.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.admin.batch.mapper.BatchMapper;
import com.admin.log.mapper.LogMapper;
import com.sysconf.util.DateUtil;

@Service
@Primary
public class LogServiceImpl implements LogService {
	
	private volatile Set<String> apiExecutionLogColumns;

	@Autowired
	DateUtil dateUtil;

	@Autowired
	LogMapper mapper;

	@Autowired
	BatchMapper batchMapper;
	
	@Override
	public List<Map<String, ?>> selectLoginHisList(Map<String, Object> param) {
		return mapper.selectLoginHisList(param);
	}
	
	@Override
	public List<Map<String, ?>> selectApiHisList(Map<String, Object> param) {
		Map<String, Object> queryParam = buildApiHistoryQuery(param);
		return mapper.selectApiHisList(queryParam);
	}

	@Override
	public int selectApiHisCount(Map<String, Object> param) {
		Map<String, Object> queryParam = buildApiHistoryQuery(param);
		return mapper.selectApiHisCount(queryParam);
	}

	/**
	 * 운영 개요(health)용: 기간 내 건수·에러·슬로우 집계 1회만 조회.
	 * 상위 URL·샘플 행은 DB 부하가 커서 생략 — 상세는 {@link LogService#selectApiHisList} 등으로 조회.
	 */
	@Override
	public Map<String, Object> getRecentApiDiagnostics(Map<String, Object> param) {
		Map<String, Object> queryParam = buildApiDiagnosticsQuery(param);
		Map<String, Object> result = new HashMap<>();
		result.put("windowHours", queryParam.get("window_hours"));
		result.put("startExeDtm", queryParam.get("start_exe_dtm"));
		result.put("slowThresholdMs", queryParam.get("slow_threshold_ms"));
		result.put("limit", queryParam.get("summary_limit"));
		result.put("traceIdEnabled", queryParam.get("api_log_has_trace_id"));
		result.put("httpStatusEnabled", queryParam.get("api_log_has_http_status"));
		result.put("elapsedMsEnabled", queryParam.get("api_log_has_elapsed_ms"));
		result.put("summary", mapper.selectRecentApiLogSummary(queryParam));
		result.put("topErrors", Collections.emptyList());
		result.put("topSlow", Collections.emptyList());
		result.put("recentErrorSamples", Collections.emptyList());
		result.put("recentSlowSamples", Collections.emptyList());
		return result;
	}
	
	@Override
	public List<Map<String, ?>> selectBatHisList(Map<String, Object> param) {
		return batchMapper.selectBatHisList(param);
	}
	
	@Override
	public String selectDetailBatHis(String id) {
		return mapper.selectDetailBatHis(id);
	}

	@Override
	public List<Map<String, ?>> selectBatchList(Map<String, Object> param) {
		return batchMapper.selectBatchList(param);
	}

	@Override
	public void insertApiLog(Map<String, Object> param) {
		// sys_api(URL→api_id) 제거로 sys_api_exe_log 적재 경로 없음 — 호출은 인터셉터 호환용 no-op
	}

	@Override
	public void insertApiLogAsync(Map<String, Object> param) {
		// 적재 비활성 — 매 요청마다 스레드풀·MDC·맵 복사 생략 (insertApiLog 도 DB 미사용)
	}

	@Override
	public List<Map<String, ?>> selectBatchConfig(Map<String, Object> param) {
		Map<String, Object> queryParam = param != null ? new HashMap<>(param) : new HashMap<>();
		return batchMapper.selectBatchConfig(queryParam);
	}

	private void applyOptionalApiLogColumns(Map<String, Object> param) {
		Set<String> columns = getApiExecutionLogColumns();
		param.put("api_log_has_trace_id", columns.contains("trace_id"));
		param.put("api_log_has_http_status", columns.contains("http_status"));
		param.put("api_log_has_elapsed_ms", columns.contains("elapsed_ms"));
	}

	private Map<String, Object> buildApiHistoryQuery(Map<String, Object> param) {
		Map<String, Object> queryParam = param != null ? new HashMap<>(param) : new HashMap<>();
		applyOptionalApiLogColumns(queryParam);
		normalizeApiHistoryQuery(queryParam);
		return queryParam;
	}

	private Map<String, Object> buildApiDiagnosticsQuery(Map<String, Object> param) {
		Map<String, Object> queryParam = buildApiHistoryQuery(param);
		int windowHours = clampInt(queryParam.get("window_hours"), 24, 1, 24 * 30);
		int summaryLimit = clampInt(queryParam.get("summary_limit"), 10, 1, 100);
		queryParam.put("window_hours", windowHours);
		queryParam.put("summary_limit", summaryLimit);
		if (queryParam.get("start_exe_dtm") == null || String.valueOf(queryParam.get("start_exe_dtm")).trim().isEmpty()) {
			queryParam.put("start_exe_dtm", formatDate(new Date(System.currentTimeMillis() - (windowHours * 60L * 60L * 1000L))));
		}
		return queryParam;
	}

	private void normalizeApiHistoryQuery(Map<String, Object> param) {
		param.put("limit", clampInt(param.get("limit"), 200, 1, 1000));
		param.put("offset", clampInt(param.get("offset"), 0, 0, Integer.MAX_VALUE));
		param.put("http_status", parseInteger(param.get("http_status")));
		param.put("min_elapsed_ms", parseLong(param.get("min_elapsed_ms")));
		param.put("max_elapsed_ms", parseLong(param.get("max_elapsed_ms")));
		param.put("error_only", parseBoolean(param.get("error_only")));
		param.put("slow_only", parseBoolean(param.get("slow_only")));

		Long slowThresholdMs = parseLong(param.get("slow_threshold_ms"));
		param.put("slow_threshold_ms", slowThresholdMs != null ? slowThresholdMs : 1000L);
	}

	private Set<String> getApiExecutionLogColumns() {
		Set<String> cached = apiExecutionLogColumns;
		if (cached != null) {
			return cached;
		}

		synchronized (this) {
			if (apiExecutionLogColumns != null) {
				return apiExecutionLogColumns;
			}
			Set<String> columns = new HashSet<>();
			try {
				List<Map<String, Object>> rows = mapper.selectApiExecutionLogColumns();
				for (Map<String, Object> row : rows) {
					if (row == null) {
						continue;
					}
					Object columnName = row.get("column_name");
					if (columnName != null) {
						columns.add(String.valueOf(columnName).toLowerCase());
					}
				}
			} catch (Exception ignore) {
				// 컬럼 메타데이터 조회 실패 시 optional 컬럼 없이 동작
			}
			apiExecutionLogColumns = columns;
			return apiExecutionLogColumns;
		}
	}

	private Integer clampInt(Object value, int defaultValue, int minValue, int maxValue) {
		Integer parsed = parseInteger(value);
		if (parsed == null) {
			return defaultValue;
		}
		if (parsed < minValue) {
			return minValue;
		}
		if (parsed > maxValue) {
			return maxValue;
		}
		return parsed;
	}

	private Integer parseInteger(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return Integer.valueOf(((Number) value).intValue());
		}
		String text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return null;
		}
		try {
			return Integer.valueOf(text);
		} catch (NumberFormatException ignore) {
			return null;
		}
	}

	private Long parseLong(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return Long.valueOf(((Number) value).longValue());
		}
		String text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return null;
		}
		try {
			return Long.valueOf(text);
		} catch (NumberFormatException ignore) {
			return null;
		}
	}

	private Boolean parseBoolean(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Boolean) {
			return (Boolean) value;
		}
		String text = String.valueOf(value).trim();
		if (text.isEmpty()) {
			return null;
		}
		if ("true".equalsIgnoreCase(text) || "y".equalsIgnoreCase(text) || "1".equals(text)) {
			return Boolean.TRUE;
		}
		if ("false".equalsIgnoreCase(text) || "n".equalsIgnoreCase(text) || "0".equals(text)) {
			return Boolean.FALSE;
		}
		return null;
	}

	private String formatDate(Date date) {
		return new SimpleDateFormat(dateUtil.getPattern()).format(date);
	}
}
