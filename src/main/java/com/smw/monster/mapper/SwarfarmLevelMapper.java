package com.smw.monster.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SwarfarmLevelMapper {
    
    /**
     * 레벨 정보 저장 또는 업데이트
     */
    int upsertLevel(Map<String, Object> param);
    
    /**
     * Level ID로 레벨 존재 여부 확인
     */
    Integer countByLevelId(@Param("level_id") Integer levelId);
    
    /**
     * 레벨 웨이브 삭제 (웨이브와 적 모두 삭제됨 - CASCADE)
     */
    int deleteLevelWaves(@Param("level_id") Integer levelId);
    
    /**
     * 레벨 웨이브 저장
     */
    int insertLevelWave(Map<String, Object> param);
    
    /**
     * 레벨 웨이브 적 몬스터 저장
     */
    int insertLevelEnemy(Map<String, Object> param);
}

