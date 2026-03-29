package com.smw.monster.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.monster.mapper.summonerswarMapper;
import com.smw.monster.util.MonsterDetailContextBuilder;
import com.smw.monster.util.MonsterIdEvolutionUtil;

@Service
@Primary
public class summonerswarServiceImpl implements summonerswarService {
	
	@Autowired
	summonerswarMapper swMapper;
	
	@Override
	@Cacheable(
		cacheNames = "monsterList",
		cacheManager = "shortLivedCacheManager",
		key = "'all'",
		condition = "#root.args[0] == null || #root.args[0].isEmpty()"
	)
	public List<Map<String, ?>> selectMonsterList(Map<String, Object> param) {
		return swMapper.selectMonsterList(param);
	}
	
	@Override
	public List<Map<String, ?>> selectEnemyTeamList(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		ensurePagingOffset(param);
		return swMapper.selectEnemyTeamList(param);
	}

	/** paging/offset이 숫자가 아니면 기본값 설정 (NumberFormatException 방지) */
	private void ensurePagingOffset(Map<String, Object> param) {
		param.put("paging", parsePositiveInt(param.get("paging"), 10));
		param.put("offset", parsePositiveInt(param.get("offset"), 1));
	}
	private int parsePositiveInt(Object v, int defaultVal) {
		if (v == null) return defaultVal;
		if (v instanceof Number) {
			int n = ((Number) v).intValue();
			return n >= 1 ? n : defaultVal;
		}
		try {
			int n = Integer.parseInt(v.toString().trim());
			return n >= 1 ? n : defaultVal;
		} catch (NumberFormatException e) {
			return defaultVal;
		}
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
	public int upsertSiegeDefenseDeckManual(Map<String, Object> param) {
		return swMapper.upsertSiegeDefenseDeckManual(param);
	}
	
	@Override
	public int insertFriendlyteamTeamSave(Map<String, Object> param) {
		if (param == null) return 0;
		int n = swMapper.insertFriendlyteamTeamSave(param);
		if (n <= 0) return n;

		// siege_defense_deck_stats(전투 집계)에 없는 방덱을 목록에도 보이도록 별도 등록
		Object d1 = param.get("def_monster_1");
		Object d2 = param.get("def_monster_2");
		Object d3 = param.get("def_monster_3");
		if (d1 != null && !String.valueOf(d1).isBlank()
				&& d2 != null && !String.valueOf(d2).isBlank()
				&& d3 != null && !String.valueOf(d3).isBlank()) {
			swMapper.upsertSiegeDefenseDeckManual(param);
		}
		
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
	public Map<String, ?> selectMonsterDetailBasic(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		List<Map<String, ?>> enemyDataList = swMapper.selectMonsterDetailList(param);
		Map<String, Object> map = new HashMap<>();
		map.put("enemyData", enemyDataList);
		return map;
	}

	@Override
	public Map<String, ?> selectMonsterDetailRecommended(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		List<Map<String, ?>> recommendedList = swMapper.selectRecommendedAttackDeckList(param);
		int recommendedTotalCount = swMapper.selectRecommendedAttackDeckListCount(param);
		Map<String, Object> map = new HashMap<>();
		map.put("recommendedList", recommendedList);
		map.put("recommendedTotalCount", recommendedTotalCount);
		return map;
	}

	@Override
	public Map<String, ?> selectMonsterDetailHistory(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		List<Map<String, ?>> historyList = swMapper.selectMonsterDetailTeamList(param);
		int historyTotalCount = swMapper.selectMonsterDetailTeamListCount(param);
		Map<String, Object> map = new HashMap<>();
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
	@Cacheable(cacheNames = "guildSiegeHistory", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
	public List<Map<String, ?>> selectGuildSiegeHistorySimple(Map<String, Object> param) {
		return swMapper.selectGuildSiegeHistorySimple(param);
	}
	
	@Override
	@Cacheable(cacheNames = "guildSiegeHistoryCount", cacheManager = "shortLivedCacheManager", keyGenerator = "stableMapKeyGenerator")
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
		try {
			return swMapper.selectCurrentSeason(param);
		} catch (org.springframework.dao.EmptyResultDataAccessException e) {
			return java.util.Collections.emptyMap();
		}
	}

	@Override
	public List<Map<String, ?>> selectSeasonList(Map<String, Object> param) {
		return swMapper.selectSeasonList(param);
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
	@Cacheable(cacheNames = "monsterInfo", cacheManager = "shortLivedCacheManager", key = "#monsterId")
	public Map<String, ?> selectMonsterInfo(String monsterId) {
		// 몬스터 기본 정보 조회
		Map<String, ?> monsterInfo = swMapper.selectMonsterInfo(monsterId);
		if (monsterInfo == null || monsterInfo.isEmpty()) {
			return new HashMap<>();
		}
		
		// 몬스터 스킬 목록 + 이펙트(아이콘은 skill_effect_master 조인)
		List<Map<String, ?>> skills = dedupeMonsterSkillsBySkillId(swMapper.selectMonsterSkills(monsterId));
		List<Map<String, ?>> skillEffectRows = swMapper.selectMonsterSkillEffects(monsterId);
		Map<Object, List<Map<String, ?>>> effectsBySkillId = new LinkedHashMap<>();
		if (skillEffectRows != null) {
			for (Map<String, ?> row : skillEffectRows) {
				Object skid = row.get("skill_id");
				if (skid == null) {
					continue;
				}
				effectsBySkillId.computeIfAbsent(skid, k -> new ArrayList<>()).add(row);
			}
		}
		List<Map<String, Object>> skillsWithEffects = new ArrayList<>();
		if (skills != null) {
			for (Map<String, ?> s : skills) {
				Map<String, Object> m = new HashMap<>();
				for (Map.Entry<String, ?> e : s.entrySet()) {
					m.put(e.getKey(), e.getValue());
				}
				Object skid = m.get("skill_id");
				m.put("effects", effectsBySkillId.getOrDefault(skid, Collections.emptyList()));
				skillsWithEffects.add(m);
			}
		}

		// 결과에 스킬 정보 추가
		Map<String, Object> result = new HashMap<>(monsterInfo);
		result.put("skills", skillsWithEffects);

		// 상세 전용: 패밀리 + 진화 라인 + 스킬 그룹을 합쳐 detail_context 구성 (중복 monster_id 제거)
		List<Map<String, ?>> familyRows = new ArrayList<>();
		Set<String> seenMonsterIds = new LinkedHashSet<>();
		Object fidObj = monsterInfo.get("family_id");
		Long familyId = null;
		if (fidObj instanceof Number) {
			familyId = ((Number) fidObj).longValue();
		} else if (fidObj != null) {
			try {
				familyId = Long.parseLong(fidObj.toString().trim());
			} catch (NumberFormatException ignored) {
				familyId = null;
			}
		}
		if (familyId != null && familyId > 0) {
			mergeMonsterRows(familyRows, swMapper.selectMonstersByFamilyId(familyId.longValue()), seenMonsterIds);
		}
		Object unObj = monsterInfo.get("un_name");
		if (unObj != null && !unObj.toString().trim().isEmpty()) {
			mergeMonsterRows(familyRows, swMapper.selectMonstersByUnName(unObj.toString().trim()), seenMonsterIds);
		}
		String evoGroupKey = MonsterIdEvolutionUtil.evolutionGroupKey(monsterId);
		if (evoGroupKey != null && !evoGroupKey.isEmpty() && !evoGroupKey.startsWith("solo:")) {
			mergeMonsterRows(familyRows, swMapper.selectMonstersByEvolutionGroupKey(evoGroupKey), seenMonsterIds);
		}
		long skillGroupId = 0L;
		Object sgObj = monsterInfo.get("skill_group_id");
		if (sgObj instanceof Number) {
			skillGroupId = ((Number) sgObj).longValue();
		} else if (sgObj != null) {
			try {
				skillGroupId = Long.parseLong(sgObj.toString().trim());
			} catch (NumberFormatException ignored) {
				skillGroupId = 0L;
			}
		}
		if (skillGroupId > 0) {
			mergeMonsterRows(familyRows, swMapper.selectMonstersBySkillGroupId(skillGroupId), seenMonsterIds);
		}
		Object elemental = monsterInfo.get("monster_elemental");
		Map<String, Object> detailContext = MonsterDetailContextBuilder.build(monsterId,
				elemental != null ? elemental.toString() : null, familyRows);

		int starCount = 0;
		Object starObj = monsterInfo.get("star");
		if (starObj instanceof Number) {
			starCount = ((Number) starObj).intValue();
		} else if (starObj != null) {
			try {
				starCount = Integer.parseInt(starObj.toString().trim());
			} catch (NumberFormatException ignored) {
				starCount = 0;
			}
		}
		int awakenDigit = MonsterIdEvolutionUtil.awakenStepDigit(monsterId);
		if (awakenDigit < 0) {
			Object al = monsterInfo.get("awaken_level");
			if (al instanceof Number) {
				awakenDigit = ((Number) al).intValue();
			} else if (al != null) {
				try {
					awakenDigit = Integer.parseInt(al.toString().trim());
				} catch (NumberFormatException e) {
					awakenDigit = 0;
				}
			} else {
				awakenDigit = 0;
			}
		}
		Map<String, ?> cohortBounds = swMapper.selectMonsterStatCohortBounds(starCount, awakenDigit);
		if (cohortBounds != null && !cohortBounds.isEmpty()) {
			Map<String, Object> cohortCopy = new HashMap<>();
			for (Map.Entry<String, ?> e : cohortBounds.entrySet()) {
				if (e.getValue() != null) {
					cohortCopy.put(e.getKey(), e.getValue());
				}
			}
			if (!cohortCopy.isEmpty()) {
				detailContext.put("stat_cohort", cohortCopy);
			}
		}

		result.put("detail_context", detailContext);

		return result;
	}

	/**
	 * PK가 (monster_id, skill_id, skill_order)라 동일 skill_id가 여러 행일 수 있음.
	 * slot → skill_order 순으로 정렬 후 skill_id당 첫 행만 유지.
	 */
	private List<Map<String, ?>> dedupeMonsterSkillsBySkillId(List<Map<String, ?>> rows) {
		if (rows == null || rows.isEmpty()) {
			return rows == null ? Collections.emptyList() : new ArrayList<>(rows);
		}
		List<Map<String, ?>> sorted = new ArrayList<>(rows);
		sorted.sort(this::compareMonsterSkillLinkRows);
		Map<Object, Map<String, ?>> distinct = new LinkedHashMap<>();
		for (Map<String, ?> row : sorted) {
			Object skillId = row.get("skill_id");
			if (skillId == null) {
				continue;
			}
			distinct.putIfAbsent(skillId, row);
		}
		return new ArrayList<>(distinct.values());
	}

	private int compareMonsterSkillLinkRows(Map<String, ?> a, Map<String, ?> b) {
		int c = Integer.compare(slotFromSkillRow(a), slotFromSkillRow(b));
		if (c != 0) {
			return c;
		}
		return Integer.compare(skillOrderFromSkillRow(a), skillOrderFromSkillRow(b));
	}

	private static int slotFromSkillRow(Map<String, ?> m) {
		Object v = m.get("slot");
		if (v instanceof Number n) {
			return n.intValue();
		}
		if (v != null) {
			try {
				return Integer.parseInt(v.toString().trim());
			} catch (NumberFormatException e) {
				return Integer.MAX_VALUE;
			}
		}
		return Integer.MAX_VALUE;
	}

	private static int skillOrderFromSkillRow(Map<String, ?> m) {
		Object v = m.get("skill_order");
		if (v == null) {
			v = m.get("skillOrder");
		}
		if (v instanceof Number n) {
			return n.intValue();
		}
		if (v != null) {
			try {
				return Integer.parseInt(v.toString().trim());
			} catch (NumberFormatException e) {
				return Integer.MAX_VALUE;
			}
		}
		return Integer.MAX_VALUE;
	}

	private static void mergeMonsterRows(List<Map<String, ?>> dest, List<Map<String, ?>> more, Set<String> seen) {
		if (more == null) {
			return;
		}
		for (Map<String, ?> r : more) {
			Object idObj = r.get("monster_id");
			if (idObj == null) {
				continue;
			}
			String sid = idObj.toString().trim();
			if (sid.isEmpty() || seen.contains(sid)) {
				continue;
			}
			seen.add(sid);
			dest.add(r);
		}
	}
}
