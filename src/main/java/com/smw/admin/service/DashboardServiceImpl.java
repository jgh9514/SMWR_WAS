package com.smw.admin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.smw.admin.mapper.DashboardMapper;

@Service
public class DashboardServiceImpl implements DashboardService {

	@Autowired
	private DashboardMapper dashboardMapper;

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
}

