package com.smw.admin.service;

import java.util.List;
import java.util.Map;

public interface AdminMonsterService {
	
	/**
	 * 몬스터 목록 조회 (관리자용)
	 */
	List<Map<String, Object>> getMonsterList(Map<String, Object> param);
	
	/**
	 * 몬스터 총 개수 조회
	 */
	int getMonsterCount(Map<String, Object> param);
	
	/**
	 * 몬스터 상세 정보 조회
	 */
	Map<String, Object> getMonsterDetail(String monsterId);
	
	/**
	 * 몬스터 정보 수정
	 */
	int updateMonster(Map<String, Object> param);
}
