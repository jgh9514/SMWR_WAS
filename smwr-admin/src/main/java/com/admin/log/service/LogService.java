package com.admin.log.service;

import java.util.List;
import java.util.Map;

public interface LogService {

	List<Map<String, String>> selectBatchConfig(Map<String, Object> param);

	void insertApiLogAsync(Map<String, Object> param);

	List<Map<String, ?>> selectLoginHisList(Map<String, Object> param);

	List<Map<String, ?>> selectApiHisList(Map<String, Object> param);

	List<Map<String, ?>> selectBatHisList(Map<String, Object> param);

	List<Map<String, ?>> selectBatchList(Map<String, Object> param);

	String selectDetailBatHis(String id);
}
