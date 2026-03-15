package com.admin.page.service;

import java.util.List;
import java.util.Map;

public interface PageService {

    public List<Map<String, Object>> selectPageConditionItems(Map<String, Object> param);

	public List<Map<String, ?>> selectPageList(Map<String, Object> param);

	public List<Map<String, ?>> selectPageConditionList(Map<String, Object> param);
	
	public int deletePage(Map<String, Object> param);
	
	public int insertPage(Map<String, Object> param);
	
	public int updatePage(Map<String, Object> param);
	
	public int deletePageCondition(Map<String, Object> param);

	public int savePageCondition(Map<String, Object> param);
	
	public int updatePageCondition(Map<String, Object> param);

	public Map<String, ?> selectPageConditionInfo(Map<String, Object> param);

	public Map<String, Object> selectPageAuth(Map<String, Object> param);
}