package com.admin.page.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface PageMapper {

    public List<Map<String, String>> selectPageConditionItems(Map<String, Object> param);

	public List<Map<String, ?>> selectPageList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectPageConditionList(Map<String, Object> param);
	
	public int deletePage(Map<String, Object> param);

	public int insertPage(Map<String, Object> param);
	
	public int updatePage(Map<String, Object> param);

	public int deletePageConditionList(Map<String, Object> param);

	public int deletePageCondition(Map<String, Object> param);
	
	public int insertPageCondition(Map<String, Object> param);
	
	public int updatePageCondition(Map<String, Object> param);

	public Map<String, ?> selectPageConditionInfo(Map<String, Object> param);

	public List<Map<String, Object>> selectPageAuth(Map<String, Object> param);

	public Map<String, Object> selectPageAuthTable(Map<String, Object> param);
	public Map<String, Object> selectAuthTable(Map<String, Object> param);
	public Map<String, Object> selectAuthCheckYn(Map<String, Object> param);
}