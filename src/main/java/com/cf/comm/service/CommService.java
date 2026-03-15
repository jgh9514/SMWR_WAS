package com.cf.comm.service;

import java.util.List;
import java.util.Map;

public interface CommService {
	
	public List<Map<String, ?>> selectMenuList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectLoginAccessPageList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectCdList(Map<String, Object> param);
	
	public int updateConfig(Map<String, Object> param);
	
	public List<Map<String, ?>> selectMultiLanguageList(Map<String, Object> param);
	
	public Map<String, String> selectMultiLanguageI18n(Map<String, Object> param);

	public void cachingMultiLanguageI18n();
	
	public List<Map<String, ?>> selectVersionCheck();
	
	public void insertErrorLogs(Map<String, Object> param);
}
