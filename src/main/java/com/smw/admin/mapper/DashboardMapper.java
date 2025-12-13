package com.smw.admin.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DashboardMapper {

	/**
	 * 대시보드 통계 조회
	 */
	Map<String, Object> selectDashboardStats(Map<String, Object> param);

	/**
	 * 일별 통계 데이터 조회 (최근 7일)
	 */
	List<Map<String, Object>> selectDailyStats(Map<String, Object> param);
}

