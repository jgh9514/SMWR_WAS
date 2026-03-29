package com.smw.monster.mapper;

import java.util.List;
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
     * 스킬 마스터 다건 upsert (페이지 단위 배치)
     */
    int upsertSkillsBatch(@Param("items") List<Map<String, Object>> items);

    /**
     * 스킬 ID 목록에 해당하는 업그레이드 행 일괄 삭제
     */
    int deleteSkillUpgradesBySkillIds(@Param("skillIds") List<Integer> skillIds);

    /**
     * 스킬 ID 목록에 해당하는 이펙트 행 일괄 삭제
     */
    int deleteSkillEffectsBySkillIds(@Param("skillIds") List<Integer> skillIds);
    
    /**
     * Swarfarm ID로 스킬 존재 여부 확인
     */
    Integer countBySwarfarmId(@Param("swarfarm_id") Integer swarfarmId);

    /**
     * 전체 Swarfarm 스킬 ID 조회
     */
    List<Integer> selectAllSwarfarmIds();
    
    /**
     * Swarfarm ID로 skill_id 찾기
     */
    Integer findSkillIdBySwarfarmId(@Param("swarfarm_id") Integer swarfarmId);
    
    /**
     * 스킬 업그레이드 삭제 (단일 스킬)
     */
    int deleteSkillUpgrades(@Param("skill_id") Integer skillId);
    
    /**
     * 스킬 업그레이드 저장
     */
    int insertSkillUpgrade(Map<String, Object> param);

    /**
     * 스킬 업그레이드 일괄 저장
     */
    int insertSkillUpgradesBatch(@Param("items") List<Map<String, Object>> items);
    
    /**
     * 스킬 효과 삭제
     */
    int deleteSkillEffects(@Param("skill_id") Integer skillId);
    
    /**
     * 스킬 효과 저장
     */
    int insertSkillEffect(Map<String, Object> param);

    /**
     * 스킬 효과 일괄 저장
     */
    int insertSkillEffectsBatch(@Param("items") List<Map<String, Object>> items);
}

