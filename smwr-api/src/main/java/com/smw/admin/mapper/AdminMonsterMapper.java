package com.smw.admin.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminMonsterMapper {

	/**
	 * 몬스터 목록 조회 (관리자용)
	 */
	List<Map<String, Object>> selectMonsterList(Map<String, Object> param);

	/**
	 * 몬스터 총 개수 조회
	 */
	int selectMonsterCount(Map<String, Object> param);

	/**
	 * 몬스터 상세 정보 조회
	 */
	Map<String, Object> selectMonsterDetail(@Param("monster_id") String monsterId);

	/**
	 * 몬스터 정보 수정
	 */
	int updateMonster(Map<String, Object> param);
}
