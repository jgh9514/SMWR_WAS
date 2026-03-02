package com.smw.admin.service;

import java.util.List;
import java.util.Map;

public interface AdminPerfService {
	Map<String, Object> getDiagnostics(Map<String, Object> param);

	List<Map<String, Object>> getSlowQueries(Map<String, Object> param);

	List<Map<String, Object>> getRunningQueries(Map<String, Object> param);

	void resetQueryStats(Map<String, Object> param);
}

