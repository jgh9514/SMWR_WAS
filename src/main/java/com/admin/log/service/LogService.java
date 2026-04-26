package com.admin.log.service;

import java.util.List;
import java.util.Map;

public interface LogService {

	public List<Map<String, ?>> selectLoginHisList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectApiHisList(Map<String, Object> param);

	public int selectApiHisCount(Map<String, Object> param);

	public Map<String, Object> getRecentApiDiagnostics(Map<String, Object> param);
	
	public List<Map<String, ?>> selectBatHisList(Map<String, Object> param);
	
	public String selectDetailBatHis(String id);
	
	public List<Map<String, ?>> selectBatchList(Map<String, Object> param);

	public List<Map<String, ?>> selectBatchConfig(Map<String, Object> param);

	public List<Map<String, ?>> selectBatchRunHisList(Map<String, Object> param);

	public Map<String, ?> selectBatchRunHisDetail(Long runSn);
	
	public void insertApiLog(Map<String, Object> param);
	
	/**
	 * API 로그를 비동기로 적재합니다. (요청 응답 시간에 영향 최소화)
	 */
	public void insertApiLogAsync(Map<String, Object> param);
}
