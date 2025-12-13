package com.smw.admin.service;

import java.util.Map;

public interface DashboardService {
	
	/**
	 * 대시보드 통계 조회
	 */
	Map<String, Object> getDashboardStats(Map<String, Object> param);
}

