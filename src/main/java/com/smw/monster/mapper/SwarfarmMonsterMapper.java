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
     * Swarfarm ID별 이미지 스냅샷 (갱신 시 S3 재업로드 생략 판단용)
     */
    List<Map<String, Object>> selectSwarfarmImageSnapshots();

    /**
     * 몬스터 캐시용 전체 조회 (monster_id, kr_name, image_url, monster_elemental)
     */
    List<Map<String, Object>> selectAllForCache();
    
    /**
     * 몬스터 스킬 삭제 (monster_id로)
     */
    int deleteMonsterSkillsByMonsterId(@Param("monster_id") String monsterId);

    /**
     * 스킬 ID로 몬스터–스킬 매핑 전부 삭제 (스킬 동기화 시 Swarfarm used_on 재구성용)
     */
    int deleteMonsterSkillLinksBySkillId(@Param("skill_id") Integer skillId);

    /**
     * 스킬 ID 목록에 해당하는 몬스터–스킬 링크 일괄 삭제 (페이지 배치 동기화용)
     */
    int deleteMonsterSkillLinksBySkillIds(@Param("skillIds") List<Integer> skillIds);

    /**
     * Swarfarm ID 목록에 대한 monster_id 일괄 조회 (used_on 캐시용, 키: swarfarm_id, monster_id)
     */
    List<Map<String, Object>> selectMonsterIdsBySwarfarmIds(@Param("ids") List<Integer> ids);
    
    /**
     * 몬스터 스킬 저장
     */
    int insertMonsterSkill(Map<String, Object> param);

    /**
     * 몬스터 스킬 일괄 저장
     */
    int insertMonsterSkillsBatch(@Param("items") List<Map<String, Object>> items);
    
    /**
     * 몬스터 획득 경로 삭제 (monster_id로)
     */
    int deleteMonsterSourcesByMonsterId(@Param("monster_id") String monsterId);
    
    /**
     * 몬스터 획득 경로 저장
     */
    int insertMonsterSource(Map<String, Object> param);

    /**
     * 몬스터 획득 경로 일괄 저장
     */
    int insertMonsterSourcesBatch(@Param("items") List<Map<String, Object>> items);
}

