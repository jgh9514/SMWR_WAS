package com.admin.code.service;

import java.util.List;
import java.util.Map;

public interface CdService {

	public List<Map<String, ?>> selectCdGrpList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectCdList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectCdUtilList(Map<String, Object> param);
	
	public String selectBsnsCdKey(Map<String, Object> param);
	
	public int insertCdGrp(Map<String, Object> param);
	
	public int updateCdGrp(Map<String, Object> param);
	
	public int deleteCdGrp(Map<String, Object> param);
	
	public int deleteCdList(Map<String, Object> param);
	
	public int insertCd(Map<String, Object> param);
	
	public int updateCd(Map<String, Object> param);
	
	public int deleteCd(Map<String, Object> param);
}
