package com.smw.monster.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface summonerswarMapper {

	public List<Map<String, ?>> selectMonsterList(Map<String, Object> param);

	public List<Map<String, ?>> selectEnemyTeamList(Map<String, Object> param);

	public int selectTotalPageCount(Map<String, Object> param);
			
	public int insertEnemyTeamSave(Map<String, Object> param);
	
	public int insertFriendlyteamTeamSave(Map<String, Object> param);

	/**
	 * 전투 집계에 없는 방덱을 목록에 보이도록 등록(추천 공덱 저장 시)
	 */
	public int upsertSiegeDefenseDeckManual(Map<String, Object> param);

	/**
	 * 공덱 수정일 갱신
	 */
	public int touchRecommendedAttackDeck(Map<String, Object> param);
	
	/**
	 * 공덱 몬스터 스탯 upsert
	 */
	public int upsertRecommendedAttackDeckStats(Map<String, Object> param);

	public List<Map<String, ?>> selectMonsterDetailList(Map<String, Object> param);
	
	public List<Map<String, ?>> selectRecommendedAttackDeckList(Map<String, Object> param);
	
	public int selectRecommendedAttackDeckListCount(Map<String, Object> param);

	public List<Map<String, ?>> selectMonsterDetailTeamList(Map<String, Object> param);
	
	public int selectMonsterDetailTeamListCount(Map<String, Object> param);
	
	public Map<String, ?> selectGuildMatchCheck(Map<String, ?> param);
	
	public int insertGuildSiegeInfo(Map<String, ?> param);
	
	public Map<String, ?> selectBattleMatchCheck(Map<String, ?> param);
	
	public int insertGuildSiegeBattleLog(Map<String, ?> param);
	
	public int insertGuildSiegeBattleDeck(Map<String, ?> param);
	
	public int selectArenaKeyCheck(Map<String, ?> param);
	
	public int insertArenaInfo(Map<String, ?> param);
	
	public int insertArenaUserInfo(Map<String, ?> param);
	
	public int insertArenaPickInfo(Map<String, ?> param);
	
	public int insertArenaUnitInfo(Map<String, ?> param);

	public List<Map<String, ?>> selectRecordList(Map<String, Object> param);

	public List<Map<String, ?>> selectRecordUserDetail(Map<String, Object> param);
	
	public List<Map<String, ?>> selectGuildSiegeHistorySimple(Map<String, Object> param);
	
	public int selectGuildSiegeHistoryCount(Map<String, Object> param);
	
	public String getOriginalMonsterId(String monsterId);
	
	public List<String> getAllRelatedMonsterIds(String monsterId);
	
	public Map<String, ?> selectDeckDetail(Map<String, Object> param);
	
	public int deleteDeckDetail(Map<String, Object> param);
	
	public Map<String, ?> selectCurrentSeason(Map<String, Object> param);

	public List<Map<String, ?>> selectSeasonList(Map<String, Object> param);
	
	public int deleteGuildSiegeBattleDeckByMatchId(@Param("matchId") String matchId);
	
	public int deleteGuildSiegeBattleLogByMatchId(@Param("matchId") String matchId);
	
	public int deleteGuildSiegeInfoByMatchId(@Param("matchId") String matchId);
	
	/**
	 * 몬스터 기본 정보 조회 (스탯, 스킬, 리더 포함)
	 */
	public Map<String, ?> selectMonsterInfo(@Param("monster_id") String monsterId);
	
	/**
	 * 몬스터 스킬 목록 조회
	 */
	public List<Map<String, ?>> selectMonsterSkills(@Param("monster_id") String monsterId);

	/**
	 * 몬스터에 연결된 스킬의 이펙트 행 + 이펙트 마스터 아이콘 경로
	 */
	List<Map<String, ?>> selectMonsterSkillEffects(@Param("monster_id") String monsterId);

	/** 상세 전용: 같은 family_id 몬스터 전부 (monster-list 조건과 무관) */
	List<Map<String, ?>> selectMonstersByFamilyId(@Param("family_id") long familyId);

	/** family_id 없을 때 같은 영문명(un_name) 패밀리 보완 */
	List<Map<String, ?>> selectMonstersByUnName(@Param("un_name") String unName);

	/**
	 * monster_id 앞부분+마지막 속성 자리가 같은 진화 라인 (노말/1각/2각) — family_id 없어도 조회
	 */
	List<Map<String, ?>> selectMonstersByEvolutionGroupKey(@Param("evolution_group_key") String evolutionGroupKey);

	/** 같은 스킬 그룹(속성 다른 패밀리) 보완 */
	List<Map<String, ?>> selectMonstersBySkillGroupId(@Param("skill_group_id") long skillGroupId);

	/**
	 * 같은 별(star) + 같은 각성 단계(monster_id 끝에서 두 번째 숫자) 몬스터 집단에서 스탯별 MIN/MAX (상세 Max 막대용)
	 */
	Map<String, ?> selectMonsterStatCohortBounds(@Param("star") int star, @Param("awaken_digit") int awakenDigit);
}
