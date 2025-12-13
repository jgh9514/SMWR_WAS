package com.admin.multilang.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MultiLangMapper {

	public List<Map<String, ?>> selectMlangList(Map<String, Object> param);
	
	public int insertMlang(Map<String, Object> param);
	
	public int updateMlang(Map<String, Object> param);
	
	public int deleteMlang(Map<String, Object> param);
	
	public int deleteMlangUpCd(Map<String, Object> param);
	
	public int updateRoleMlangList(Map<String, Object> param);
	
	public String selectMlangInfo(Map<String, Object> param);
	
	public List<Map<String, String>> selectMlangListInfo(Map<String, Object> param);
	
	
	// 다국어 추가/갱신/삭제
	public int upsertMlang(Map<String, Object> param);

	// 다국어 다중 삭제
	public int deleteMlangList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectI18nList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectMultiLanguageList(Map<String, Object> param);
	
	public String selectMlangSeq(String mlang_tp_cd);
}

