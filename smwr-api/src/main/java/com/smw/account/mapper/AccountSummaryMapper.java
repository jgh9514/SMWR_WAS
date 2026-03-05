package com.smw.account.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountSummaryMapper {

	int insertImport(Map<String, Object> param);

	int insertMonstersBulk(@Param("list") List<Map<String, Object>> list);

	int insertRunesBulk(@Param("list") List<Map<String, Object>> list);

	Map<String, ?> selectLatestImport(Map<String, Object> param);

	List<Map<String, ?>> selectImportList(Map<String, Object> param);

	Map<String, ?> selectImportDetail(Map<String, Object> param);

	List<Map<String, ?>> selectMonsterList(Map<String, Object> param);

	int selectMonsterListCount(Map<String, Object> param);

	List<Map<String, ?>> selectMonsterCatalog(Map<String, Object> param);

	int selectMonsterCatalogCount(Map<String, Object> param);

	List<Map<String, ?>> selectRuneList(Map<String, Object> param);

	int selectRuneListCount(Map<String, Object> param);

	List<Map<String, ?>> selectRunesForScoreSummary(Map<String, Object> param);
}


