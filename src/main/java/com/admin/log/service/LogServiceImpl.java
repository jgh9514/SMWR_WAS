package com.admin.log.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PreDestroy;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.admin.batch.mapper.BatchMapper;
import com.admin.log.mapper.LogMapper;
import com.sysconf.util.DateUtil;

@Service
@Primary
public class LogServiceImpl implements LogService {
	
	private static final String API_ID_NONE = "__NONE__";
	private final ConcurrentHashMap<String, String> apiIdCache = new ConcurrentHashMap<>();
	private volatile Set<String> apiExecutionLogColumns;
	private final ExecutorService apiLogExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
		private final AtomicInteger seq = new AtomicInteger(1);
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "api-log-" + seq.getAndIncrement());
			t.setDaemon(true);
			return t;
		}
	});

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
		result.put("topErrors", mapper.selectTopErrorApiLogs(queryParam));
		result.put("topSlow", mapper.selectTopSlowApiLogs(queryParam));
		result.put("recentErrorSamples", mapper.selectRecentErrorApiLogSamples(queryParam));
		result.put("recentSlowSamples", mapper.selectRecentSlowApiLogSamples(queryParam));
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
		if (param == null) {
			return;
		}
		// URL이 없으면 스킵
		Object urlObj = param != null ? param.get("url") : null;
		String url = urlObj != null ? urlObj.toString() : null;
		if (url == null || url.isEmpty()) {
			return;
		}

		// API URL -> api_id 캐시 (DB select 최소화)
		String cached = apiIdCache.get(url);
		if (API_ID_NONE.equals(cached)) {
			return; // 등록되지 않은 API
		}
		if (cached == null) {
			Map<String, Object> api = mapper.selectApiByUrl(param);
			if (api == null || api.get("api_id") == null) {
				apiIdCache.put(url, API_ID_NONE);
				return;
			}
			cached = api.get("api_id").toString();
			apiIdCache.put(url, cached);
		}

		param.put("api_id", cached);
		param.put("exe_dtm", dateUtil.now());
		applyOptionalApiLogColumns(param);
		mapper.insertApiExecutionLog(param);
	}
	
	@Override
	public void insertApiLogAsync(Map<String, Object> param) {
		// 요청 thread에서 분리 (copy해서 동시성 이슈 방지)
		final Map<String, Object> copy = param != null ? new HashMap<>(param) : new HashMap<>();
		final java.util.Map<String, String> mdc = ThreadContext.getImmutableContext();
		apiLogExecutor.submit(() -> {
			if (mdc != null && !mdc.isEmpty()) {
				ThreadContext.putAll(mdc);
			}
			try {
				insertApiLog(copy);
			} catch (Exception ignore) {
				// 로깅 실패는 업무 흐름에 영향 주지 않음
			} finally {
				if (mdc != null && !mdc.isEmpty()) {
					for (String k : mdc.keySet()) ThreadContext.remove(k);
				}
			}
		});
	}
	
	@PreDestroy
	public void shutdown() {
		try {
			apiLogExecutor.shutdown();
		} catch (Exception ignore) {
			// no-op
		}
	}

	@Override
	public List<Map<String, String>> selectBatchConfig(Map<String, Object> param) {
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
