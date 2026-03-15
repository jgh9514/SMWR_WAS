package com.smw.monster.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.monster.mapper.summonerswarMapper;

@Service
@Primary
public class summonerswarServiceImpl implements summonerswarService {
	
	@Autowired
	summonerswarMapper swMapper;
	
	@Override
	@Cacheable(
		cacheNames = "monsterList",
		key = "'all'",
		condition = "#root.args[0] == null || #root.args[0].isEmpty()"
	)
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
				// allRelatedIds가 null이거나 empty여도 최소한 원본 ID를 포함한 _list/_ids 생성
				List<String> ids = (allRelatedIds != null && !allRelatedIds.isEmpty())
						? allRelatedIds
						: java.util.Arrays.asList(monsterId);
				param.put(key + "_ids", ids);
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
				// allRelatedIds가 null이거나 empty여도 최소한 원본 ID를 포함한 _list/_ids 생성
				List<String> ids = (allRelatedIds != null && !allRelatedIds.isEmpty())
						? allRelatedIds
						: java.util.Arrays.asList(monsterId);
				param.put(key + "_ids", ids);
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
		if (param == null) return 0;
		int n = swMapper.insertFriendlyteamTeamSave(param);
		if (n <= 0) return n;
		
		// insertFriendlyteamTeamSave에서 selectKey로 deck_id가 채워짐
		Object deckIdObj = param != null ? param.get("deck_id") : null;
		if (deckIdObj == null) return n;
		
		String deckId = String.valueOf(deckIdObj);
		String m1 = param.get("atk_monster_1") != null ? String.valueOf(param.get("atk_monster_1")) : null;
		String m2 = param.get("atk_monster_2") != null ? String.valueOf(param.get("atk_monster_2")) : null;
		String m3 = param.get("atk_monster_3") != null ? String.valueOf(param.get("atk_monster_3")) : null;
		
		// 스탯 저장 (없으면 0으로 upsert)
		upsertDeckStatsFromPayload(param, deckId, m1, "monster_1_stats");
		upsertDeckStatsFromPayload(param, deckId, m2, "monster_2_stats");
		upsertDeckStatsFromPayload(param, deckId, m3, "monster_3_stats");
		
		// 수정일 갱신
		Map<String, Object> touch = new HashMap<>();
		touch.put("deck_id", deckId);
		touch.put("sess_user_id", param.get("sess_user_id"));
		swMapper.touchRecommendedAttackDeck(touch);
		
		return n;
	}

	@SuppressWarnings("unchecked")
	private void upsertDeckStatsFromPayload(Map<String, Object> root, String deckId, String monsterId, String statsKey) {
		if (deckId == null || monsterId == null || monsterId.isEmpty()) return;
		Object raw = root != null ? root.get(statsKey) : null;
		Map<String, Object> stats = (raw instanceof Map) ? (Map<String, Object>) raw : java.util.Collections.emptyMap();
		
		Map<String, Object> p = new HashMap<>();
		p.put("deck_id", deckId);
		p.put("monster_id", monsterId);
		p.put("hp", intOrZero(stats.get("hp")));
		p.put("atk", intOrZero(stats.get("atk")));
		p.put("def", intOrZero(stats.get("def")));
		p.put("spd", intOrZero(stats.get("spd")));
		p.put("crit_rate", intOrZero(firstNonNull(stats.get("critRate"), stats.get("crit_rate"))));
		p.put("crit_dmg", intOrZero(firstNonNull(stats.get("critDmg"), stats.get("crit_dmg"))));
		p.put("resistance", intOrZero(stats.get("resistance")));
		p.put("accuracy", intOrZero(stats.get("accuracy")));
		p.put("sess_user_id", root != null ? root.get("sess_user_id") : null);
		
		swMapper.upsertRecommendedAttackDeckStats(p);
	}
	
	private Object firstNonNull(Object a, Object b) {
		return a != null ? a : b;
	}
	
	private int intOrZero(Object v) {
		if (v == null) return 0;
		if (v instanceof Number) return ((Number) v).intValue();
		try {
			String s = String.valueOf(v).trim();
			if (s.isEmpty()) return 0;
			return (int) Double.parseDouble(s);
		} catch (Exception ignore) {
			return 0;
		}
	}

	@Override
	public int updateRecommendedAttackDeckStats(Map<String, Object> param) {
		if (param == null || param.get("deck_id") == null) return 0;
		
		// 몬스터 변경은 허용하지 않음: DB에서 공격 몬스터 ID를 가져와 그 몬스터에만 적용
		Map<String, Object> q = new HashMap<>();
		q.put("deck_id", String.valueOf(param.get("deck_id")));
		Map<String, ?> deck = swMapper.selectDeckDetail(q);
		if (deck == null || deck.isEmpty()) return 0;
		
		String deckId = String.valueOf(q.get("deck_id"));
		String m1 = deck.get("atk_monster_1") != null ? String.valueOf(deck.get("atk_monster_1")) : null;
		String m2 = deck.get("atk_monster_2") != null ? String.valueOf(deck.get("atk_monster_2")) : null;
		String m3 = deck.get("atk_monster_3") != null ? String.valueOf(deck.get("atk_monster_3")) : null;
		
		upsertDeckStatsFromPayload(param, deckId, m1, "monster_1_stats");
		upsertDeckStatsFromPayload(param, deckId, m2, "monster_2_stats");
		upsertDeckStatsFromPayload(param, deckId, m3, "monster_3_stats");
		
		Map<String, Object> touch = new HashMap<>();
		touch.put("deck_id", deckId);
		touch.put("sess_user_id", param.get("sess_user_id"));
		swMapper.touchRecommendedAttackDeck(touch);
		return 1;
	}

	@Override
	public Map<String, ?> selectMonsterDetailList(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);

		Map<String, Object> map = new HashMap<String, Object>();
		
		// enemyData 조회 (리스트 반환)
		List<Map<String, ?>> enemyDataList = swMapper.selectMonsterDetailList(param);
		
		// recommendedList 조회
		List<Map<String, ?>> recommendedList = swMapper.selectRecommendedAttackDeckList(param);
		
		// recommendedTotalCount 조회
		int recommendedTotalCount = swMapper.selectRecommendedAttackDeckListCount(param);
		
		// historyList 조회
		List<Map<String, ?>> historyList = swMapper.selectMonsterDetailTeamList(param);
		
		// historyTotalCount 조회
		int historyTotalCount = swMapper.selectMonsterDetailTeamListCount(param);
		
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
	@Cacheable(cacheNames = "guildSiegeHistory", keyGenerator = "stableMapKeyGenerator")
	public List<Map<String, ?>> selectGuildSiegeHistorySimple(Map<String, Object> param) {
		return swMapper.selectGuildSiegeHistorySimple(param);
	}
	
	@Override
	@Cacheable(cacheNames = "guildSiegeHistoryCount", keyGenerator = "stableMapKeyGenerator")
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
		// 몬스터 기본 정보 조회
		Map<String, ?> monsterInfo = swMapper.selectMonsterInfo(monsterId);
		if (monsterInfo == null || monsterInfo.isEmpty()) {
			return new HashMap<>();
		}
		
		// 몬스터 스킬 목록 조회
		List<Map<String, ?>> skills = swMapper.selectMonsterSkills(monsterId);
		
		// 결과에 스킬 정보 추가
		Map<String, Object> result = new HashMap<>(monsterInfo);
		result.put("skills", skills != null ? skills : new java.util.ArrayList<>());
		
		return result;
	}
}
