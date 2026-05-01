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

	/** 청크 처리용: swex_account_import 에서 wizard_id 를 키셋 순으로 limit 개 조회. afterWizardId=null 이면 처음부터. */
	List<String> selectDistinctWizardIdsFromSwexKeyset(@Param("afterWizardId") String afterWizardId, @Param("limit") int limit);

	/** 청크 처리용: 지정 wizard_id 목록의 보유 집계 행 삭제 */
	int deleteUserMonsterOwnedAggForWizards(@Param("wizardIds") List<String> wizardIds);

	/** 청크 처리용: 지정 wizard_id 목록에 대해 SWEX 최신 임포트 기준 집계 INSERT */
	int insertUserMonsterOwnedAggFromSwexForWizards(@Param("wizardIds") List<String> wizardIds);
}


