package com.admin.log.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogMapper {

	public List<Map<String, ?>> selectLoginHisList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectApiHisList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectBatHisList(Map<String, Object> param);
	
	public String selectDetailBatHis(String id);
	
	public List<Map<String, ?>> selectBatchList(Map<String, Object> param);
	
	public void insertApiExecutionLog(Map<String, Object> param);
	
	public Map<String, Object> selectApiByUrl(Map<String, Object> param);
}
