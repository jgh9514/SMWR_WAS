package com.smw.admin.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.admin.batch.mapper.BatchMapper;

@Service
@Primary
public class AdminBatchServiceImpl implements AdminBatchService {

    private static final int DEFAULT_RECENT_RUN_LIMIT = 20;
    private static final int DEFAULT_FAILURE_LIMIT = 10;
    private static final int LOG_PREVIEW_LENGTH = 400;
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String RESULT_RUNNING = "RUNNING";

    @Autowired
    private BatchMapper batchMapper;

    @Override
    public Map<String, Object> getBatchDiagnostics(Map<String, Object> param) {
        Map<String, Object> query = param != null ? new HashMap<>(param) : new HashMap<>();
        if (!query.containsKey("limit")) {
            query.put("limit", 1000);
        }
        List<Map<String, ?>> runs = batchMapper.selectBatchRunHisList(query);
        Map<String, String> batchNameMap = loadBatchNameMap();

        int recentLimit = getInt(query.get("recent_limit"), DEFAULT_RECENT_RUN_LIMIT);
        int failureLimit = getInt(query.get("failure_limit"), DEFAULT_FAILURE_LIMIT);

        Map<Object, Map<String, Object>> summaryByBatchId = new LinkedHashMap<>();
        List<Map<String, Object>> recentRuns = new ArrayList<>();
        List<Map<String, Object>> recentFailures = new ArrayList<>();
        int totalFailedRuns = 0;
        int totalSuccessRuns = 0;
        int totalRunningRuns = 0;

        for (Map<String, ?> run : runs) {
            Object batId = run.get("bat_id");
            String resultCode = toStringOrEmpty(run.get("rslt_cd"));
            String batchName = batchNameMap.getOrDefault(String.valueOf(batId), "배치-" + batId);

            Map<String, Object> summary = summaryByBatchId.computeIfAbsent(batId, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bat_id", batId);
                item.put("bat_nm", batchName);
                item.put("latest_run_sn", run.get("bat_exe_log_sn"));
                item.put("latest_result", resultCode);
                item.put("latest_start_dtm", run.get("exe_dtm"));
                item.put("latest_end_dtm", run.get("end_dtm"));
                item.put("latest_status", resolveStatus(resultCode, run.get("end_dtm")));
                item.put("success_count", 0);
                item.put("failed_count", 0);
                item.put("running_count", 0);
                item.put("last_failure_preview", null);
                return item;
            });

            if (isSuccess(resultCode)) {
                totalSuccessRuns++;
                summary.put("success_count", ((Integer) summary.get("success_count")) + 1);
            } else if (isFailure(resultCode)) {
                totalFailedRuns++;
                summary.put("failed_count", ((Integer) summary.get("failed_count")) + 1);
                if (summary.get("last_failure_preview") == null) {
                    summary.put("last_failure_preview", buildPreview(run.get("rslt_txt")));
                }
            } else if (isRunning(resultCode) || run.get("end_dtm") == null) {
                totalRunningRuns++;
                summary.put("running_count", ((Integer) summary.get("running_count")) + 1);
            }

            if (recentRuns.size() < recentLimit) {
                recentRuns.add(buildRunItem(run, batchName, true));
            }
            if (isFailure(resultCode) && recentFailures.size() < failureLimit) {
                recentFailures.add(buildRunItem(run, batchName, true));
            }
        }

        List<Map<String, Object>> summary = new ArrayList<>(summaryByBatchId.values());
        summary.sort(Comparator.comparing(item -> Objects.toString(item.get("latest_start_dtm"), ""), Comparator.reverseOrder()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("recent_runs", recentRuns);
        result.put("recent_failures", recentFailures);
        result.put("total_runs", runs.size());
        result.put("failed_runs", totalFailedRuns);
        result.put("successful_runs", totalSuccessRuns);
        result.put("running_runs", totalRunningRuns);
        return result;
    }

    @Override
    public Map<String, Object> getBatchRunDetail(Long runSn) {
        Map<String, ?> detail = batchMapper.selectBatchRunHisDetail(runSn);
        Map<String, Object> result = new LinkedHashMap<>();
        if (detail == null || detail.isEmpty()) {
            result.put("found", false);
            return result;
        }

        Map<String, String> batchNameMap = loadBatchNameMap();
        Object batId = detail.get("bat_id");
        result.put("found", true);
        result.put("detail", buildRunItem(detail, batchNameMap.getOrDefault(String.valueOf(batId), "배치-" + batId), false));
        return result;
    }

    private Map<String, String> loadBatchNameMap() {
        Map<String, String> batchNameMap = new HashMap<>();
        List<Map<String, ?>> configs = batchMapper.selectBatchConfig(new HashMap<>());
        for (Map<String, ?> config : configs) {
            if (config == null) {
                continue;
            }
            Object batId = config.get("bat_id");
            Object batNm = config.get("bat_nm");
            if (batId != null && batNm != null) {
                batchNameMap.put(String.valueOf(batId), String.valueOf(batNm));
            }
        }
        return batchNameMap;
    }

    private Map<String, Object> buildRunItem(Map<String, ?> run, String batchName, boolean includePreviewOnly) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("bat_exe_log_sn", run.get("bat_exe_log_sn"));
        item.put("bat_id", run.get("bat_id"));
        item.put("bat_nm", batchName);
        item.put("exe_dtm", run.get("exe_dtm"));
        item.put("end_dtm", run.get("end_dtm"));
        item.put("rslt_cd", run.get("rslt_cd"));
        item.put("status", resolveStatus(run.get("rslt_cd"), run.get("end_dtm")));
        item.put("rslt_txt_preview", buildPreview(run.get("rslt_txt")));
        if (!includePreviewOnly) {
            item.put("rslt_txt", run.get("rslt_txt"));
        }
        return item;
    }

    private String buildPreview(Object rawText) {
        String text = toStringOrEmpty(rawText);
        if (text.isEmpty()) {
            return text;
        }
        String normalized = text.replace("\r", "").trim();
        if (normalized.length() <= LOG_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, LOG_PREVIEW_LENGTH) + "...";
    }

    private int getInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String toStringOrEmpty(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String resolveStatus(Object resultCode, Object endDtm) {
        String result = toStringOrEmpty(resultCode);
        if (isRunning(result) || endDtm == null) {
            return RESULT_RUNNING;
        }
        if (isSuccess(result)) {
            return RESULT_SUCCESS;
        }
        if (isFailure(result)) {
            return RESULT_FAIL;
        }
        return result.isEmpty() ? "UNKNOWN" : result;
    }

    private boolean isSuccess(String resultCode) {
        return RESULT_SUCCESS.equalsIgnoreCase(resultCode);
    }

    private boolean isFailure(String resultCode) {
        return RESULT_FAIL.equalsIgnoreCase(resultCode);
    }

    private boolean isRunning(String resultCode) {
        return RESULT_RUNNING.equalsIgnoreCase(resultCode);
    }
}
