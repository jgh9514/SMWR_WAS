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
		// 디버깅: 파라미터 로깅
		org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(summonerswarServiceImpl.class);
		log.info("selectMonsterDetailList 파라미터: {}", param);
		
		expandMonsterIdsToIncludeCollaborations(param);
		
		// 디버깅: 확장된 파라미터 로깅
		log.info("expandMonsterIdsToIncludeCollaborations 후 파라미터: {}", param);
		
		Map<String, Object> map = new HashMap<String, Object>();
		
		// enemyData 조회 (리스트 반환)
		List<Map<String, ?>> enemyDataList = swMapper.selectMonsterDetailList(param);
		log.info("enemyData 조회 결과: {}개", enemyDataList != null ? enemyDataList.size() : 0);
		
		// recommendedList 조회
		List<Map<String, ?>> recommendedList = swMapper.selectRecommendedAttackDeckList(param);
		log.info("recommendedList 조회 결과: {}개", recommendedList != null ? recommendedList.size() : 0);
		
		// recommendedTotalCount 조회
		int recommendedTotalCount = swMapper.selectRecommendedAttackDeckListCount(param);
		log.info("recommendedTotalCount: {}", recommendedTotalCount);
		
		// historyList 조회
		List<Map<String, ?>> historyList = swMapper.selectMonsterDetailTeamList(param);
		log.info("historyList 조회 결과: {}개", historyList != null ? historyList.size() : 0);
		
		// historyTotalCount 조회
		int historyTotalCount = swMapper.selectMonsterDetailTeamListCount(param);
		log.info("historyTotalCount: {}", historyTotalCount);
		
		map.put("enemyData", enemyDataList);
		map.put("recommendedList", recommendedList);
		map.put("recommendedTotalCount", recommendedTotalCount);
		map.put("historyList", historyList);
		map.put("historyTotalCount", historyTotalCount);
		
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
	
	@Override
	public Map<String, ?> selectMonsterInfo(String monsterId) {
		org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(summonerswarServiceImpl.class);
		log.info("몬스터 기본 정보 조회: monster_id={}", monsterId);
		
		// 몬스터 기본 정보 조회
		Map<String, ?> monsterInfo = swMapper.selectMonsterInfo(monsterId);
		if (monsterInfo == null || monsterInfo.isEmpty()) {
			log.warn("몬스터 정보를 찾을 수 없습니다: monster_id={}", monsterId);
			return new HashMap<>();
		}
		
		// 몬스터 스킬 목록 조회
		List<Map<String, ?>> skills = swMapper.selectMonsterSkills(monsterId);
		log.info("몬스터 스킬 조회 완료: {}개", skills != null ? skills.size() : 0);
		
		// 결과에 스킬 정보 추가
		Map<String, Object> result = new HashMap<>(monsterInfo);
		result.put("skills", skills != null ? skills : new java.util.ArrayList<>());
		
		return result;
	}
}
