package com.smw.monster.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SwarfarmDungeonMapper {
    
    /**
     * 던전 정보 저장 또는 업데이트
     */
    int upsertDungeon(Map<String, Object> param);
    
    /**
     * Dungeon ID로 던전 존재 여부 확인
     */
    Integer countByDungeonId(@Param("dungeon_id") Integer dungeonId);
    
    /**
     * 던전 레벨 삭제
     */
    int deleteDungeonLevels(@Param("dungeon_id") Integer dungeonId);
    
    /**
     * 던전 레벨 저장
     */
    int insertDungeonLevel(Map<String, Object> param);
}

