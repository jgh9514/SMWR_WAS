package com.smw.monster.service;

import java.util.List;
import java.util.Map;

public interface summonerswarService {

	public List<Map<String, ?>> selectMonsterList(Map<String, Object> param);

	public List<Map<String, ?>> selectEnemyTeamList(Map<String, Object> param);

	public int selectTotalPageCount(Map<String, Object> param);
	
	public int insertEnemyTeamSave(Map<String, Object> param);
		
	public int insertFriendlyteamTeamSave(Map<String, Object> param);
	
	public Map<String, ?> selectMonsterDetailList(Map<String, Object> param);
	
	public int selectMonsterDetailTeamListCount(Map<String, Object> param);
	
	public int selectRecommendedAttackDeckListCount(Map<String, Object> param);
	
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
	
	public Map<String, ?> selectDeckDetail(Map<String, Object> param);
	
	public int deleteDeckDetail(Map<String, Object> param);
	
	public Map<String, ?> selectCurrentSeason(Map<String, Object> param);
	
	public int deleteGuildSiegeBattleDeckByMatchId(String matchId);
	
	public int deleteGuildSiegeBattleLogByMatchId(String matchId);
	
	public int deleteGuildSiegeInfoByMatchId(String matchId);
	
}
