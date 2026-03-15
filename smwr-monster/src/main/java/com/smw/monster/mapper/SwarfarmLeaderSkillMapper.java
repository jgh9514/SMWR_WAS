package com.smw.monster.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SwarfarmLeaderSkillMapper {
    
    /**
     * 리더 스킬 정보 저장 또는 업데이트
     */
    int upsertLeaderSkill(Map<String, Object> param);
    
    /**
     * Leader Skill ID로 리더 스킬 존재 여부 확인
     */
    Integer countByLeaderSkillId(@Param("leader_skill_id") Integer leaderSkillId);

    /**
     * monster_leaders 동기화 (표시용 icon_path 포함)
     */
    int upsertMonsterLeader(Map<String, Object> param);
}
