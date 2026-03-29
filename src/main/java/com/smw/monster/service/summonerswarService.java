package com.smw.monster.service;

import java.util.List;
import java.util.Map;

public interface summonerswarService {

	public List<Map<String, ?>> selectMonsterList(Map<String, Object> param);

	public List<Map<String, ?>> selectEnemyTeamList(Map<String, Object> param);

	public int selectTotalPageCount(Map<String, Object> param);
	
	public int insertEnemyTeamSave(Map<String, Object> param);
		
	public int insertFriendlyteamTeamSave(Map<String, Object> param);

	/**
	 * 전투 집계에 없는 방덱을 siege_defense_deck_manual에 등록해 목록에 노출합니다.
	 */
	public int upsertSiegeDefenseDeckManual(Map<String, Object> param);
	
	public Map<String, ?> selectMonsterDetailList(Map<String, Object> param);
	
	/** 몬스터 상세 - 기본 정보만 (enemyData) */
	public Map<String, ?> selectMonsterDetailBasic(Map<String, Object> param);
	
	/** 몬스터 상세 - 추천 공덱만 */
	public Map<String, ?> selectMonsterDetailRecommended(Map<String, Object> param);
	
	/** 몬스터 상세 - 공성률 이력만 */
	public Map<String, ?> selectMonsterDetailHistory(Map<String, Object> param);
	
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

	/**
	 * 추천 공덱 스탯 수정 (몬스터는 변경 불가)
	 */
	public int updateRecommendedAttackDeckStats(Map<String, Object> param);
	
	public Map<String, ?> selectCurrentSeason(Map<String, Object> param);

	public List<Map<String, ?>> selectSeasonList(Map<String, Object> param);
	
	public int deleteGuildSiegeBattleDeckByMatchId(String matchId);
	
	public int deleteGuildSiegeBattleLogByMatchId(String matchId);
	
	public int deleteGuildSiegeInfoByMatchId(String matchId);
	
	/**
	 * 몬스터 기본 정보 조회 (스탯, 스킬, 리더 포함)
	 */
	public Map<String, ?> selectMonsterInfo(String monsterId);
	
}
