package com.admin.api.service;

import java.util.List;
import java.util.Map;

public interface ApiService {

	public List<Map<String, ?>> selectApiList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectApiRoleList(Map<String, Object> param);
	
	public String selectApiKey(Map<String, Object> param);
	
	public int insertApiList(Map<String, Object> param);
	
	public int updateApiList(Map<String, Object> param);
	
	public int deleteApiList(Map<String, Object> param);
	
	public int deleteApiRole(Map<String, Object> param);
	
	public int insertApiRole(Map<String, Object> param);
	
	public int updateApiRole(Map<String, Object> param);
}
