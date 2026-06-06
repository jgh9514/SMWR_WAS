package com.admin.batch.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BatchMapper {

	public int insertBatch(Map<String, Object> param);
	/** MyBatis sqlMap: 숫자 컬럼은 Long 등 원시 JDBC 타입에 가깝게 매핑됨 */
	public List<Map<String, ?>> selectBatchConfig(Map<String, Object> param);
	public int updateBatch(Map<String, Object> param);
	public String getClassName(String param);

	// 배치 실행 이력
	public int insertBatchRunHis(Map<String, Object> param);
	public int updateBatchRunHis(Map<String, Object> param);
	public List<Map<String, ?>> selectBatchRunHisList(Map<String, Object> param);
	public Map<String, ?> selectBatchRunHisDetail(Long runSn);
	
	public List<Map<String, ?>> selectBatHisList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectBatchList(Map<String, Object> param);

	/** 배치 오류 시 use_yn = 'N' 으로 비활성화 */
	public int disableBatch(Long batId);

	/** RUNNING 고착 행을 ABORTED 로 정리. @return 갱신 건수 */
	int abortStaleBatchRunHis(@Param("staleHours") int staleHours);

	Long selectMaxRunSnByBatId(@Param("batId") Long batId);

	String selectBatchRunHisResultCode(@Param("runSn") Long runSn);

}
