package com.smw.monster.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SwarfarmMonsterMapper {
    
    /**
     * 몬스터 정보 저장 또는 업데이트
     */
    int upsertMonster(Map<String, Object> param);
    
    /**
     * Swarfarm ID로 몬스터 존재 여부 확인
     */
    Integer countBySwarfarmId(@Param("swarfarm_id") Integer swarfarmId);
    
    /**
     * Swarfarm ID로 monster_id 찾기
     */
    String findMonsterIdBySwarfarmId(@Param("swarfarm_id") Integer swarfarmId);
    
    /**
     * 모든 Swarfarm ID 목록 조회 (성능 최적화용)
     */
    List<Integer> selectAllSwarfarmIds();
    
    /**
     * 몬스터 스킬 삭제 (monster_id로)
     */
    int deleteMonsterSkillsByMonsterId(@Param("monster_id") String monsterId);
    
    /**
     * 몬스터 스킬 저장
     */
    int insertMonsterSkill(Map<String, Object> param);
    
    /**
     * 몬스터 획득 경로 삭제 (monster_id로)
     */
    int deleteMonsterSourcesByMonsterId(@Param("monster_id") String monsterId);
    
    /**
     * 몬스터 획득 경로 저장
     */
    int insertMonsterSource(Map<String, Object> param);
}

