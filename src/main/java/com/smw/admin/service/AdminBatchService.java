package com.smw.admin.service;

import java.util.Map;

public interface AdminBatchService {

    Map<String, Object> getBatchDiagnostics(Map<String, Object> param);

    Map<String, Object> getBatchRunDetail(Long runSn);
}
