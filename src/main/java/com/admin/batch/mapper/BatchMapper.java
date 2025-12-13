package com.admin.batch.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BatchMapper {

	public int insertBatch(Map<String, Object> param);
	public List<Map<String, String>> selectBatchConfig(Map<String, Object> param);
	public int updateBatch(Map<String, Object> param);
	public String getClassName(String param);

	// 배치 실행 이력
	public int insertBatchRunHis(Map<String, Object> param);
	public int updateBatchRunHis(Map<String, Object> param);
	public List<Map<String, ?>> selectBatchRunHisList(Map<String, Object> param);
	public Map<String, ?> selectBatchRunHisDetail(Long runSn);
	
	public List<Map<String, ?>> selectBatHisList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectBatchList(Map<String, Object> param);

}
