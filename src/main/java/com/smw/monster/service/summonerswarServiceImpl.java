package com.smw.monster.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.monster.mapper.summonerswarMapper;

@Service
@Primary
public class summonerswarServiceImpl implements summonerswarService {
	
	@Autowired
	summonerswarMapper swMapper;
	
	@Override
	public List<Map<String, ?>> selectMonsterList(Map<String, Object> param) {
		return swMapper.selectMonsterList(param);
	}
	
	@Override
	public List<Map<String, ?>> selectEnemyTeamList(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		return swMapper.selectEnemyTeamList(param);
	}
	
	/**
	 * Expand monster IDs to include all related monsters (collab, etc.)
	 */
	private void expandMonsterIdsToIncludeCollaborations(Map<String, Object> param) {
		String[] fixedMonsterIdKeys = {"monster_id1", "dm1"};
		for (String key : fixedMonsterIdKeys) {
			if (param.containsKey(key) && param.get(key) != null && !param.get(key).toString().isEmpty()) {
				String monsterId = param.get(key).toString();
				List<String> allRelatedIds = swMapper.getAllRelatedMonsterIds(monsterId);
				// allRelatedIds가 null이거나 empty여도 최소한 원본 ID를 포함한 _list 생성
				if (allRelatedIds != null && !allRelatedIds.isEmpty()) {
					String idsString = allRelatedIds.stream()
						.map(id -> "'" + id + "'")
						.collect(java.util.stream.Collectors.joining(","));
					param.put(key + "_list", idsString);
				} else {
					// 관련 ID가 없어도 원본 ID를 포함한 _list 생성
					param.put(key + "_list", "'" + monsterId + "'");
				}
			}
		}
		
		String[] orderedMonsterIdKeys = {"monster_id2", "monster_id3", "dm2", "dm3"};
		for (String key : orderedMonsterIdKeys) {
			if (param.containsKey(key) && param.get(key) != null && !param.get(key).toString().isEmpty()) {
				String monsterId = param.get(key).toString();
				List<String> allRelatedIds = swMapper.getAllRelatedMonsterIds(monsterId);
				// allRelatedIds가 null이거나 empty여도 최소한 원본 ID를 포함한 _list 생성
				if (allRelatedIds != null && !allRelatedIds.isEmpty()) {
					String idsString = allRelatedIds.stream()
						.map(id -> "'" + id + "'")
						.collect(java.util.stream.Collectors.joining(","));
					param.put(key + "_list", idsString);
				} else {
					// 관련 ID가 없어도 원본 ID를 포함한 _list 생성
					param.put(key + "_list", "'" + monsterId + "'");
				}
			}
		}
	}
	
	@Override
	public int selectTotalPageCount(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		return swMapper.selectTotalPageCount(param);
	}
	
	@Override
	public int insertEnemyTeamSave(Map<String, Object> param) {
		return swMapper.insertEnemyTeamSave(param);
	}
	
	@Override
	public int insertFriendlyteamTeamSave(Map<String, Object> param) {
		return swMapper.insertFriendlyteamTeamSave(param);
	}

	@Override
	public Map<String, ?> selectMonsterDetailList(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("enemyData", swMapper.selectMonsterDetailList(param));
		map.put("recommendedList", swMapper.selectRecommendedAttackDeckList(param));
		map.put("recommendedTotalCount", swMapper.selectRecommendedAttackDeckListCount(param));
		map.put("historyList", swMapper.selectMonsterDetailTeamList(param));
		map.put("historyTotalCount", swMapper.selectMonsterDetailTeamListCount(param));
		return map;
	}
	
	@Override
	public int selectMonsterDetailTeamListCount(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		return swMapper.selectMonsterDetailTeamListCount(param);
	}
	
	@Override
	public int selectRecommendedAttackDeckListCount(Map<String, Object> param) {
		return swMapper.selectRecommendedAttackDeckListCount(param);
	}
	
	@Override
	public Map<String, ?> selectGuildMatchCheck(Map<String, ?> param) {
		return swMapper.selectGuildMatchCheck(param);
	}
	
	@Override
	public int insertGuildSiegeInfo(Map<String, ?> param) {
		return swMapper.insertGuildSiegeInfo(param);
	}
	
	@Override
	public Map<String, ?> selectBattleMatchCheck(Map<String, ?> param) {
		return swMapper.selectBattleMatchCheck(param);
	}
	
	@Override
	public int insertGuildSiegeBattleLog(Map<String, ?> param) {
		return swMapper.insertGuildSiegeBattleLog(param);
	}
	
	@Override
	public int insertGuildSiegeBattleDeck(Map<String, ?> param) {
		return swMapper.insertGuildSiegeBattleDeck(param);
	}
	
	@Override
	public int selectArenaKeyCheck(Map<String, ?> param) {
		return swMapper.selectArenaKeyCheck(param);
	}
	
	@Override
	public int insertArenaInfo(Map<String, ?> param) {
		return swMapper.insertArenaInfo(param);
	}
	
	@Override
	public int insertArenaUserInfo(Map<String, ?> param) {
		return swMapper.insertArenaUserInfo(param);
	}
	
	@Override
	public int insertArenaPickInfo(Map<String, ?> param) {
		return swMapper.insertArenaPickInfo(param);
	}
	
	@Override
	public int insertArenaUnitInfo(Map<String, ?> param) {
		return swMapper.insertArenaUnitInfo(param);
	}
	
	@Override
	public List<Map<String, ?>> selectRecordList(Map<String, Object> param) {
		return swMapper.selectRecordList(param);
	}
	
	@Override
	public List<Map<String, ?>> selectRecordUserDetail(Map<String, Object> param) {
		return swMapper.selectRecordUserDetail(param);
	}
	
	@Override
	public List<Map<String, ?>> selectGuildSiegeHistorySimple(Map<String, Object> param) {
		return swMapper.selectGuildSiegeHistorySimple(param);
	}
	
	@Override
	public int selectGuildSiegeHistoryCount(Map<String, Object> param) {
		return swMapper.selectGuildSiegeHistoryCount(param);
	}
	
	@Override
	public Map<String, ?> selectDeckDetail(Map<String, Object> param) {
		return swMapper.selectDeckDetail(param);
	}
	
	@Override
	public int deleteDeckDetail(Map<String, Object> param) {
		return swMapper.deleteDeckDetail(param);
	}
	
	@Override
	public Map<String, ?> selectCurrentSeason(Map<String, Object> param) {
		return swMapper.selectCurrentSeason(param);
	}
	
	@Override
	public int deleteGuildSiegeBattleDeckByMatchId(String matchId) {
		return swMapper.deleteGuildSiegeBattleDeckByMatchId(matchId);
	}
	
	@Override
	public int deleteGuildSiegeBattleLogByMatchId(String matchId) {
		return swMapper.deleteGuildSiegeBattleLogByMatchId(matchId);
	}
	
	@Override
	public int deleteGuildSiegeInfoByMatchId(String matchId) {
		return swMapper.deleteGuildSiegeInfoByMatchId(matchId);
	}
}
