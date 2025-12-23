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
}
