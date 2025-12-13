package com.admin.log.service;

import java.util.List;
import java.util.Map;

public interface LogService {

	public List<Map<String, ?>> selectLoginHisList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectApiHisList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectBatHisList(Map<String, Object> param);
	
	public String selectDetailBatHis(String id);
	
	public List<Map<String, ?>> selectBatchList(Map<String, Object> param);

	public List<Map<String, String>> selectBatchConfig(Map<String, Object> param);
	
	public void insertApiLog(Map<String, Object> param);
}
