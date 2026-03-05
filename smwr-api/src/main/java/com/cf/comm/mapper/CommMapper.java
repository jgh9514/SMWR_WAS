package com.cf.comm.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommMapper {
	
	public List<Map<String, ?>> selectMenuList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectLoginAccessPageList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectVersionCheck();
	
	public void insertErrorLogs(Map<String, Object> param);
}
