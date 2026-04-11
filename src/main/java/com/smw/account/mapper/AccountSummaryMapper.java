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

	/** 보유 몬스터 집계 전체 삭제 (배치 재적재 전) */
	int deleteAllUserMonsterOwnedAgg();

	/**
	 * SWEX: 소환사별 최신 임포트(import_id 최대) 기준 master_id 당 보유 마리 수 집계.
	 * 시즌·RTA와 무관.
	 */
	int insertUserMonsterOwnedAggFromSwex();

	/** {@link #insertUserMonsterOwnedAggFromSwex()} 직후 행 수 확인용 (INSERT 반환값은 PG JDBC에서 2^31 초과 시 int 오버플로로 음수가 될 수 있음) */
	long countUserMonsterOwnedAgg();
}


