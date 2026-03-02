package com.smw.admin.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminPerfMapper {
	/**
	 * pg_stat_statements 설치/설정 진단
	 */
	Map<String, Object> selectPgStatStatementsDiagnostics(Map<String, Object> param);

	/**
	 * pg_stat_statements 컬럼 목록 조회 (버전 호환용)
	 */
	List<Map<String, Object>> selectPgStatStatementsColumns(Map<String, Object> param);

	/**
	 * 느린 쿼리 TOP (PostgreSQL 13+ 컬럼: total_exec_time/mean_exec_time/max_exec_time)
	 */
	List<Map<String, Object>> selectSlowQueriesV13(Map<String, Object> param);

	/**
	 * 느린 쿼리 TOP (PostgreSQL 12 이하 컬럼: total_time/mean_time/max_time)
	 */
	List<Map<String, Object>> selectSlowQueriesLegacy(Map<String, Object> param);

	/**
	 * 현재 실행중(장시간) 쿼리 목록
	 */
	List<Map<String, Object>> selectRunningQueries(Map<String, Object> param);

	/**
	 * pg_stat_statements 누적 통계 리셋
	 */
	Object resetPgStatStatements(Map<String, Object> param);
}

