package com.admin.multilang.service;

import java.util.List;
import java.util.Map;

public interface MultiLangService {
	
	public List<Map<String, ?>> selectMlangList(Map<String, Object> param);
	
	public int insertMlang(Map<String, Object> param);
	
	public int updateMlang(Map<String, Object> param);
	
	public int deleteMlang(Map<String, Object> param);
	
	public int deleteMlangUpCd(Map<String, Object> param);
	
	public String selectMlangInfo(Map<String, Object> param);
	
	public List<Map<String, Object>> selectMlangListInfo(Map<String, Object> param);
	
	public String selectMlangSeq(String mlang_tp_cd);
}

