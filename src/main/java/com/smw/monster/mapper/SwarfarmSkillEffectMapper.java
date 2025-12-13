package com.smw.monster.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SwarfarmSkillEffectMapper {
    
    /**
     * 스킬 이펙트 정보 저장 또는 업데이트
     */
    int upsertSkillEffect(Map<String, Object> param);
    
    /**
     * Effect ID로 스킬 이펙트 존재 여부 확인
     */
    Integer countByEffectId(@Param("effect_id") Integer effectId);
}

