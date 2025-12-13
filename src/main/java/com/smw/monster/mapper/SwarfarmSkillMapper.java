package com.smw.monster.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SwarfarmSkillMapper {
    
    /**
     * 스킬 정보 저장 또는 업데이트
     */
    int upsertSkill(Map<String, Object> param);
    
    /**
     * Swarfarm ID로 스킬 존재 여부 확인
     */
    Integer countBySwarfarmId(@Param("swarfarm_id") Integer swarfarmId);
    
    /**
     * Swarfarm ID로 skill_id 찾기
     */
    String findSkillIdBySwarfarmId(@Param("swarfarm_id") Integer swarfarmId);
    
    /**
     * 스킬 업그레이드 삭제
     */
    int deleteSkillUpgrades(@Param("skill_id") String skillId);
    
    /**
     * 스킬 업그레이드 저장
     */
    int insertSkillUpgrade(Map<String, Object> param);
    
    /**
     * 스킬 효과 삭제
     */
    int deleteSkillEffects(@Param("skill_id") String skillId);
    
    /**
     * 스킬 효과 저장
     */
    int insertSkillEffect(Map<String, Object> param);
    
    /**
     * 스킬 사용 몬스터 매핑 삭제
     */
    int deleteSkillUsedOn(@Param("skill_id") String skillId);
    
    /**
     * 스킬 사용 몬스터 매핑 저장
     */
    int insertSkillUsedOn(Map<String, Object> param);
}

