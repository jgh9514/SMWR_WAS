package com.smw.admin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.admin.mapper.DashboardMapper;
import com.sysconf.security.RateLimitFilter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
@Primary
public class DashboardServiceImpl implements DashboardService {

	@Autowired
	private DashboardMapper dashboardMapper;

	@Autowired(required = false)
	private MeterRegistry meterRegistry;

	@Autowired(required = false)
	private RateLimitFilter rateLimitFilter;

	@Value("${smw.ops.thresholds.heap-warning-percent:75}")
	private double heapWarningPercent;

	@Value("${smw.ops.thresholds.heap-critical-percent:90}")
	private double heapCriticalPercent;

	@Value("${smw.ops.thresholds.process-cpu-warning-percent:70}")
	private double processCpuWarningPercent;

	@Value("${smw.ops.thresholds.process-cpu-critical-percent:90}")
	private double processCpuCriticalPercent;

	@Value("${smw.ops.thresholds.system-cpu-warning-percent:80}")
	private double systemCpuWarningPercent;

	@Value("${smw.ops.thresholds.system-cpu-critical-percent:95}")
	private double systemCpuCriticalPercent;

	@Value("${smw.ops.thresholds.thread-warning-count:250}")
	private double threadWarningCount;

	@Value("${smw.ops.thresholds.thread-critical-count:400}")
	private double threadCriticalCount;

	@Value("${smw.ops.thresholds.gc-pause-warning-ms:500}")
	private double gcPauseWarningMs;

	@Value("${smw.ops.thresholds.gc-pause-critical-ms:1500}")
	private double gcPauseCriticalMs;

	@Override
	public Map<String, Object> getDashboardStats(Map<String, Object> param) {
		Map<String, Object> result = new HashMap<>();

		// 통계 데이터 조회
		Map<String, Object> stats = dashboardMapper.selectDashboardStats(param);
		result.put("stats", stats);

		// 일별 통계 데이터 조회 (최근 7일)
		List<Map<String, Object>> dailyStats = dashboardMapper.selectDailyStats(param);
		result.put("dailyStats", dailyStats);

		return result;
	}

	@Override
	public Map<String, Object> getOpsMetricsSnapshot() {
		Map<String, Object> metrics = new HashMap<>();
		if (meterRegistry == null) {
			metrics.put("enabled", false);
			metrics.put("rateLimit", rateLimitFilter != null ? rateLimitFilter.getSnapshot() : buildRateLimitDisabledSnapshot());
			return metrics;
		}

		metrics.put("enabled", true);
		metrics.put("api", buildMetricGroup(
				"smw.api.request.count",
				"smw.api.request.duration"));
		metrics.put("batch", buildMetricGroup(
				"smw.batch.execution.count",
				"smw.batch.execution.duration"));
		metrics.put("swarfarmClient", buildMetricGroup(
				"smw.swarfarm.client.calls",
				"smw.swarfarm.client.duration"));
		metrics.put("swarfarmSyncRuns", buildCounterGroup("smw.swarfarm.sync.runs"));
		metrics.put("swarfarmSyncItems", buildCounterGroup("smw.swarfarm.sync.items"));
		metrics.put("rateLimitMetrics", buildRateLimitMetrics());
		metrics.put("topErrorApis", buildTopErrorApis());
		metrics.put("topSlowApis", buildTopSlowApis());
		metrics.put("jvm", buildJvmSnapshot());
		metrics.put("system", buildSystemSnapshot());
		metrics.put("runtimeHealth", buildRuntimeHealth(metrics));
		metrics.put("rateLimit", rateLimitFilter != null ? rateLimitFilter.getSnapshot() : buildRateLimitDisabledSnapshot());
		return metrics;
	}

	private Map<String, Object> buildMetricGroup(String counterName, String timerName) {
		Map<String, Object> group = buildCounterGroup(counterName);
		group.put("timer", buildTimerGroup(timerName));
		return group;
	}

	private Map<String, Object> buildCounterGroup(String counterName) {
		Map<String, Object> group = new HashMap<>();
		double totalCount = 0.0;
		Collection<Counter> counters = meterRegistry.find(counterName).counters();
		for (Counter counter : counters) {
			totalCount += counter.count();
		}
		group.put("name", counterName);
		group.put("series", counters.size());
		group.put("count", totalCount);
		return group;
	}

	private Map<String, Object> buildTimerGroup(String timerName) {
		Map<String, Object> group = new HashMap<>();
		long totalCount = 0L;
		double totalMs = 0.0;
		double maxMs = 0.0;
		Collection<Timer> timers = meterRegistry.find(timerName).timers();
		for (Timer timer : timers) {
			totalCount += timer.count();
			totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
			maxMs = Math.max(maxMs, timer.max(TimeUnit.MILLISECONDS));
		}
		group.put("name", timerName);
		group.put("series", timers.size());
		group.put("count", totalCount);
		group.put("totalMs", round2(totalMs));
		group.put("maxMs", round2(maxMs));
		group.put("avgMs", totalCount > 0 ? round2(totalMs / totalCount) : 0.0);
		return group;
	}

	private double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private List<Map<String, Object>> buildTopErrorApis() {
		Map<String, Double> aggregated = new HashMap<>();
		Collection<Counter> counters = meterRegistry.find("smw.api.request.count").counters();
		for (Counter counter : counters) {
			String outcome = counter.getId().getTag("outcome");
			if (!"CLIENT_ERROR".equals(outcome) && !"SERVER_ERROR".equals(outcome)) {
				continue;
			}
			String uri = safeTag(counter, "uri");
			String method = safeTag(counter, "method");
			String status = safeTag(counter, "status");
			String key = method + " " + uri + " [" + status + "/" + outcome + "]";
			double nextCount = counter.count();
			Double current = aggregated.get(key);
			aggregated.put(key, current == null ? nextCount : current + nextCount);
		}

		List<Map<String, Object>> topErrors = new ArrayList<>();
		aggregated.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
				.limit(10)
				.forEach(entry -> {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("target", entry.getKey());
					item.put("count", round2(entry.getValue()));
					topErrors.add(item);
				});
		return topErrors;
	}

	private List<Map<String, Object>> buildTopSlowApis() {
		Collection<Timer> timers = meterRegistry.find("smw.api.request.duration").timers();
		List<Map<String, Object>> slowApis = new ArrayList<>();
		timers.stream()
				.filter(timer -> timer.count() > 0)
				.sorted(Comparator
						.comparingDouble((Timer timer) -> timer.max(TimeUnit.MILLISECONDS))
						.reversed()
						.thenComparingDouble(timer -> timer.totalTime(TimeUnit.MILLISECONDS)))
				.limit(10)
				.forEach(timer -> {
					Map<String, Object> item = new LinkedHashMap<>();
					String method = safeTag(timer, "method");
					String uri = safeTag(timer, "uri");
					String status = safeTag(timer, "status");
					String outcome = safeTag(timer, "outcome");
					item.put("target", method + " " + uri + " [" + status + "/" + outcome + "]");
					item.put("count", timer.count());
					item.put("avgMs", round2(timer.totalTime(TimeUnit.MILLISECONDS) / timer.count()));
					item.put("maxMs", round2(timer.max(TimeUnit.MILLISECONDS)));
					item.put("totalMs", round2(timer.totalTime(TimeUnit.MILLISECONDS)));
					slowApis.add(item);
				});
		return slowApis;
	}

	private Map<String, Object> buildJvmSnapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();

		double heapUsedBytes = sumGaugeValues("jvm.memory.used", "area", "heap");
		double heapMaxBytes = sumGaugeValues("jvm.memory.max", "area", "heap");
		double nonHeapUsedBytes = sumGaugeValues("jvm.memory.used", "area", "nonheap");
		double nonHeapMaxBytes = sumGaugeValues("jvm.memory.max", "area", "nonheap");

		Map<String, Object> memory = new LinkedHashMap<>();
		memory.put("heapUsedMb", toMb(heapUsedBytes));
		memory.put("heapMaxMb", toMb(heapMaxBytes));
		memory.put("heapUsagePercent", percent(heapUsedBytes, heapMaxBytes));
		memory.put("nonHeapUsedMb", toMb(nonHeapUsedBytes));
		memory.put("nonHeapMaxMb", toMb(nonHeapMaxBytes));
		memory.put("nonHeapUsagePercent", percent(nonHeapUsedBytes, nonHeapMaxBytes));
		snapshot.put("memory", memory);

		Map<String, Object> threads = new LinkedHashMap<>();
		threads.put("live", round2(getGaugeValue("jvm.threads.live")));
		threads.put("daemon", round2(getGaugeValue("jvm.threads.daemon")));
		threads.put("peak", round2(getGaugeValue("jvm.threads.peak")));
		snapshot.put("threads", threads);

		snapshot.put("gcPause", buildTimerGroup("jvm.gc.pause"));
		return snapshot;
	}

	private Map<String, Object> buildSystemSnapshot() {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("processCpuUsagePercent", percentFromRatio(getGaugeValue("process.cpu.usage")));
		snapshot.put("systemCpuUsagePercent", percentFromRatio(getGaugeValue("system.cpu.usage")));
		snapshot.put("systemLoadAverage1m", round2(getGaugeValue("system.load.average.1m")));
		snapshot.put("uptimeSeconds", round2(getGaugeValue("process.uptime")));
		snapshot.put("openFileDescriptors", round2(getGaugeValue("process.files.open")));
		snapshot.put("maxFileDescriptors", round2(getGaugeValue("process.files.max")));
		return snapshot;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> buildRuntimeHealth(Map<String, Object> metrics) {
		Map<String, Object> health = new LinkedHashMap<>();
		Map<String, Object> jvm = (Map<String, Object>) metrics.get("jvm");
		Map<String, Object> system = (Map<String, Object>) metrics.get("system");
		Map<String, Object> memory = jvm != null ? castMap(jvm.get("memory")) : null;
		Map<String, Object> threads = jvm != null ? castMap(jvm.get("threads")) : null;
		Map<String, Object> gcPause = jvm != null ? castMap(jvm.get("gcPause")) : null;

		Map<String, Object> heapStatus = buildStatus(
				toDouble(memory != null ? memory.get("heapUsagePercent") : null),
				heapWarningPercent,
				heapCriticalPercent,
				"heapUsagePercent",
				"%");
		Map<String, Object> processCpuStatus = buildStatus(
				toDouble(system != null ? system.get("processCpuUsagePercent") : null),
				processCpuWarningPercent,
				processCpuCriticalPercent,
				"processCpuUsagePercent",
				"%");
		Map<String, Object> systemCpuStatus = buildStatus(
				toDouble(system != null ? system.get("systemCpuUsagePercent") : null),
				systemCpuWarningPercent,
				systemCpuCriticalPercent,
				"systemCpuUsagePercent",
				"%");
		Map<String, Object> threadStatus = buildStatus(
				toDouble(threads != null ? threads.get("live") : null),
				threadWarningCount,
				threadCriticalCount,
				"liveThreads",
				"");
		Map<String, Object> gcStatus = buildStatus(
				toDouble(gcPause != null ? gcPause.get("maxMs") : null),
				gcPauseWarningMs,
				gcPauseCriticalMs,
				"gcPauseMaxMs",
				"ms");

		health.put("heap", heapStatus);
		health.put("processCpu", processCpuStatus);
		health.put("systemCpu", systemCpuStatus);
		health.put("threads", threadStatus);
		health.put("gcPause", gcStatus);
		String overallStatus = maxStatus(
				stringValue(heapStatus.get("status")),
				stringValue(processCpuStatus.get("status")),
				stringValue(systemCpuStatus.get("status")),
				stringValue(threadStatus.get("status")),
				stringValue(gcStatus.get("status")));
		List<String> incidentTypes = buildRuntimeIncidentTypes(
				heapStatus,
				processCpuStatus,
				systemCpuStatus,
				threadStatus,
				gcStatus);
		health.put("status", overallStatus);
		health.put("incidentTypes", incidentTypes);
		health.put("primaryIncidentType", incidentTypes.isEmpty() ? "stable" : incidentTypes.get(0));
		health.put("reasons", buildRuntimeReasons(heapStatus, processCpuStatus, systemCpuStatus, threadStatus, gcStatus));
		health.put("summaryMessage", buildRuntimeSummaryMessage(overallStatus));
		return health;
	}

	private String safeTag(Counter counter, String tagName) {
		String value = counter.getId().getTag(tagName);
		return value != null ? value : "unknown";
	}

	private String safeTag(Timer timer, String tagName) {
		String value = timer.getId().getTag(tagName);
		return value != null ? value : "unknown";
	}

	private double getGaugeValue(String gaugeName) {
		Gauge gauge = meterRegistry.find(gaugeName).gauge();
		return gauge != null ? sanitizeDouble(gauge.value()) : 0.0;
	}

	private double sumGaugeValues(String gaugeName, String tagName, String tagValue) {
		double total = 0.0;
		Collection<Gauge> gauges = meterRegistry.find(gaugeName).gauges();
		for (Gauge gauge : gauges) {
			String currentTag = gauge.getId().getTag(tagName);
			if (tagValue.equals(currentTag)) {
				total += sanitizeDouble(gauge.value());
			}
		}
		return total;
	}

	private double sanitizeDouble(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
			return 0.0;
		}
		return value;
	}

	private double toMb(double bytes) {
		return round2(bytes / (1024.0 * 1024.0));
	}

	private double percent(double value, double max) {
		if (max <= 0) {
			return 0.0;
		}
		return round2((value / max) * 100.0);
	}

	private double percentFromRatio(double ratio) {
		return round2(sanitizeDouble(ratio) * 100.0);
	}

	private Map<String, Object> buildStatus(double value, double warningThreshold, double criticalThreshold, String metric, String unit) {
		Map<String, Object> status = new LinkedHashMap<>();
		String level = "ok";
		if (value >= criticalThreshold) {
			level = "critical";
		} else if (value >= warningThreshold) {
			level = "warning";
		}
		status.put("metric", metric);
		status.put("value", round2(value));
		status.put("warningThreshold", round2(warningThreshold));
		status.put("criticalThreshold", round2(criticalThreshold));
		status.put("unit", unit);
		status.put("status", level);
		status.put("message", metric + "=" + round2(value) + unit + " (" + level + ")");
		return status;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> castMap(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : null;
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

	private String stringValue(Object value) {
		return value != null ? String.valueOf(value) : "ok";
	}

	private String maxStatus(String... statuses) {
		String max = "ok";
		for (String status : statuses) {
			if ("critical".equalsIgnoreCase(status)) {
				return "critical";
			}
			if ("warning".equalsIgnoreCase(status)) {
				max = "warning";
			}
		}
		return max;
	}

	private List<String> buildRuntimeReasons(
			Map<String, Object> heapStatus,
			Map<String, Object> processCpuStatus,
			Map<String, Object> systemCpuStatus,
			Map<String, Object> threadStatus,
			Map<String, Object> gcStatus) {
		List<String> reasons = new ArrayList<>();
		appendRuntimeReason(reasons, heapStatus);
		appendRuntimeReason(reasons, processCpuStatus);
		appendRuntimeReason(reasons, systemCpuStatus);
		appendRuntimeReason(reasons, threadStatus);
		appendRuntimeReason(reasons, gcStatus);
		return reasons;
	}

	private void appendRuntimeReason(List<String> reasons, Map<String, Object> item) {
		if (item == null) {
			return;
		}
		String status = stringValue(item.get("status"));
		if ("warning".equalsIgnoreCase(status) || "critical".equalsIgnoreCase(status)) {
			reasons.add(stringValue(item.get("message")));
		}
	}

	private String buildRuntimeSummaryMessage(String status) {
		if ("critical".equalsIgnoreCase(status)) {
			return "JVM 또는 시스템 런타임 지표 중 즉시 확인이 필요한 항목이 있습니다.";
		}
		if ("warning".equalsIgnoreCase(status)) {
			return "JVM 또는 시스템 런타임 지표 중 추적이 필요한 항목이 있습니다.";
		}
		return "JVM 및 시스템 런타임 지표는 안정 범위입니다.";
	}

	private List<String> buildRuntimeIncidentTypes(
			Map<String, Object> heapStatus,
			Map<String, Object> processCpuStatus,
			Map<String, Object> systemCpuStatus,
			Map<String, Object> threadStatus,
			Map<String, Object> gcStatus) {
		List<String> incidentTypes = new ArrayList<>();
		appendRuntimeIncidentType(incidentTypes, heapStatus, "heap_pressure");
		appendRuntimeIncidentType(incidentTypes, processCpuStatus, "process_cpu_saturation");
		appendRuntimeIncidentType(incidentTypes, systemCpuStatus, "system_cpu_saturation");
		appendRuntimeIncidentType(incidentTypes, threadStatus, "thread_pressure");
		appendRuntimeIncidentType(incidentTypes, gcStatus, "gc_pause_spike");
		return incidentTypes;
	}

	private void appendRuntimeIncidentType(List<String> incidentTypes, Map<String, Object> item, String incidentType) {
		if (item == null) {
			return;
		}
		String status = stringValue(item.get("status"));
		if (("warning".equalsIgnoreCase(status) || "critical".equalsIgnoreCase(status))
				&& !incidentTypes.contains(incidentType)) {
			incidentTypes.add(incidentType);
		}
	}

	private Map<String, Object> buildRateLimitDisabledSnapshot() {
		Map<String, Object> snapshot = new HashMap<>();
		snapshot.put("enabled", false);
		return snapshot;
	}

	private Map<String, Object> buildRateLimitMetrics() {
		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("name", "smw.rate_limit.requests");
		metrics.put("enabled", meterRegistry != null);

		double totalAllowed = 0.0;
		double totalBlocked = 0.0;
		double loginBlocked = 0.0;
		double generalBlocked = 0.0;

		Collection<Counter> counters = meterRegistry.find("smw.rate_limit.requests").counters();
		for (Counter counter : counters) {
			String endpointType = safeTag(counter, "endpoint_type");
			String outcome = safeTag(counter, "outcome");
			double count = counter.count();
			if ("allowed".equals(outcome)) {
				totalAllowed += count;
			}
			if ("blocked".equals(outcome)) {
				totalBlocked += count;
				if ("login".equals(endpointType)) {
					loginBlocked += count;
				} else {
					generalBlocked += count;
				}
			}
		}

		metrics.put("series", counters.size());
		metrics.put("allowed", round2(totalAllowed));
		metrics.put("blocked", round2(totalBlocked));
		metrics.put("loginBlocked", round2(loginBlocked));
		metrics.put("generalBlocked", round2(generalBlocked));
		metrics.put("topBlockedTargets", buildTopBlockedTargets(counters));
		return metrics;
	}

	private List<Map<String, Object>> buildTopBlockedTargets(Collection<Counter> counters) {
		Map<String, Double> aggregated = new HashMap<>();
		for (Counter counter : counters) {
			String outcome = safeTag(counter, "outcome");
			if (!"blocked".equals(outcome)) {
				continue;
			}
			String endpointType = safeTag(counter, "endpoint_type");
			String uri = safeTag(counter, "uri");
			String key = endpointType + " " + uri;
			double nextCount = counter.count();
			Double current = aggregated.get(key);
			aggregated.put(key, current == null ? nextCount : current + nextCount);
		}

		List<Map<String, Object>> targets = new ArrayList<>();
		aggregated.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
				.limit(10)
				.forEach(entry -> {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("target", entry.getKey());
					item.put("count", round2(entry.getValue()));
					targets.add(item);
				});
		return targets;
	}
}

