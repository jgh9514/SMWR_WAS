package com.smw.monster.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;

import org.mybatis.spring.SqlSessionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import com.smw.monster.mapper.summonerswarMapper;
import com.smw.monster.util.MonsterDetailContextBuilder;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.config.RtaRawApplyProperties;
import com.smw.rta.service.ArenaRtaUploadCopyBulkService;
import com.smw.rta.service.RtaBulkRidTempTable;
import com.smw.monster.util.MonsterIdEvolutionUtil;
import com.sysconf.config.MybatisBatchConfig;
import com.sysconf.exception.RtaUploadValidationException;

@Slf4j
@Service
@Primary
public class summonerswarServiceImpl implements summonerswarService {
	
	@Autowired
	summonerswarMapper swMapper;

	@Autowired
	@Qualifier(MybatisBatchConfig.BATCH_SQL_SESSION_TEMPLATE)
	private SqlSessionTemplate rtaBatchSqlSessionTemplate;

	@Autowired
	private PlatformTransactionManager platformTransactionManager;

	/** RTA 정규화 INSERT 묶음마다 별도 커밋({@code REQUIRES_NEW}) — 긴 Job·다른 호출부와 분리 */
	private TransactionTemplate arenaRtaPersistTransactionTemplate;

	@PostConstruct
	void initArenaRtaPersistTransactionTemplate() {
		TransactionTemplate t = new TransactionTemplate(platformTransactionManager);
		t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.arenaRtaPersistTransactionTemplate = t;
	}

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RtaCacheEvictor rtaCacheEvictor;

	@Autowired
	private RtaRawApplyProperties rtaRawApplyProperties;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private ArenaRtaUploadCopyBulkService arenaRtaUploadCopyBulkService;

	@Autowired
	private com.smw.rta.service.RtaBulkRidLookupService bulkRidLookupService;

	/** {@link #getRtaSeasonMappingCache()} — rta_season 전체 스냅샷 TTL */
	private final Object rtaSeasonMappingCacheLock = new Object();
	private volatile RtaSeasonMappingCache rtaSeasonMappingCache;
	private volatile long rtaSeasonMappingCacheLoadedAtMs;
	private static final long RTA_SEASON_CACHE_TTL_MS = 60_000L;

	@Value("${smw.siege.use-defense-deck-stats:false}")
	private boolean useSiegeDefenseDeckStats;

	private void applySiegeDeckStatsQueryFlag(Map<String, Object> param) {
		if (param == null) {
			return;
		}
		param.put("use_siege_defense_deck_stats", useSiegeDefenseDeckStats ? "Y" : "N");
	}

	@Override
	@Cacheable(
		cacheNames = "monsterList",
		cacheManager = "monsterListCacheManager",
		key = "(#param == null || #param.isEmpty()) ? 'all' : #param.toString()"
	)
	public List<Map<String, ?>> selectMonsterList(Map<String, Object> param) {
		return swMapper.selectMonsterList(param);
	}
	
	@Override
	public List<Map<String, ?>> selectEnemyTeamList(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		ensurePagingOffset(param);
		applySiegeDeckStatsQueryFlag(param);
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

	/**
	 * 점령 상세 SQL은 {@code IN (${dm1_list})} 형태 문자열 치환이라 dm1~dm3 중 하나라도 빠지면 {@code IN ()} 로 깨져 500이 난다.
	 */
	private static boolean missingDmIdLists(Map<String, Object> param) {
		for (String k : new String[] { "dm1_list", "dm2_list", "dm3_list" }) {
			Object v = param.get(k);
			if (!(v instanceof String) || ((String) v).trim().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static Map<String, Object> emptyMonsterDetailFullResponse() {
		Map<String, Object> map = new HashMap<>();
		map.put("enemyData", Collections.emptyList());
		map.put("recommendedList", Collections.emptyList());
		map.put("recommendedTotalCount", 0);
		map.put("historyList", Collections.emptyList());
		map.put("historyTotalCount", 0);
		return map;
	}
	
	@Override
	public int selectTotalPageCount(Map<String, Object> param) {
		expandMonsterIdsToIncludeCollaborations(param);
		applySiegeDeckStatsQueryFlag(param);
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
	@Transactional
	public void refreshSiegeDefenseDeckStatsForGuildSeason(String guildId, String seasonYyyymm) {
		if (guildId == null || guildId.isBlank() || seasonYyyymm == null || seasonYyyymm.length() != 6) {
			return;
		}
		Map<String, Object> param = new HashMap<>();
		param.put("guild_id", guildId.trim());
		param.put("season_yyyymm", seasonYyyymm.trim());
		swMapper.deleteSiegeDefenseDeckStatsByGuildSeason(param);
		swMapper.insertSiegeDefenseDeckStatsFromBattleLogs(param);
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
		if (missingDmIdLists(param)) {
			return emptyMonsterDetailFullResponse();
		}

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
		if (missingDmIdLists(param)) {
			Map<String, Object> map = new HashMap<>();
			map.put("enemyData", Collections.emptyList());
			return map;
		}
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
		if (missingDmIdLists(param)) {
			Map<String, Object> map = new HashMap<>();
			map.put("historyList", Collections.emptyList());
			map.put("historyTotalCount", 0);
			return map;
		}
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
		if (missingDmIdLists(param)) {
			return 0;
		}
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
	public List<Map<String, ?>> selectBattleLogKeysForMatch(String matchId) {
		if (matchId == null || matchId.isEmpty()) {
			return java.util.Collections.emptyList();
		}
		return swMapper.selectBattleLogKeysForMatch(matchId);
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
	public int insertGuildSiegeBattleLogBatch(List<Map<String, ?>> rows) {
		if (rows == null || rows.isEmpty()) {
			return 0;
		}
		final int max = 500;
		int total = 0;
		for (int from = 0; from < rows.size(); from += max) {
			int to = Math.min(from + max, rows.size());
			total += swMapper.insertGuildSiegeBattleLogBatch(rows.subList(from, to));
		}
		return total;
	}

	@Override
	public int insertGuildSiegeBattleDeckBatch(List<Map<String, String>> rows) {
		if (rows == null || rows.isEmpty()) {
			return 0;
		}
		final int max = 500;
		int total = 0;
		for (int from = 0; from < rows.size(); from += max) {
			int to = Math.min(from + max, rows.size());
			total += swMapper.insertGuildSiegeBattleDeckBatch(rows.subList(from, to));
		}
		return total;
	}
	
	@Override
	public Set<Long> selectArenaRidsExisting(Collection<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return Collections.emptySet();
		}
		return bulkRidLookupService.selectExistingReplayIds(rids);
	}

	@Override
	public Set<String> selectArenaUserPkKeysExisting(Collection<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return Collections.emptySet();
		}
		return bulkRidLookupService.selectExistingUserPkKeys(rids);
	}

	@Override
	public int deleteArenaRtaOrphanChildrenByRids(Collection<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return 0;
		}
		return bulkRidLookupService.deleteOrphanArenaChildrenByRids(new ArrayList<>(new LinkedHashSet<>(rids)));
	}

	/**
	 * 업로드 트랜잭션과 동일 커넥션 — {@code tmp_bulk_rids} COPY 후 JOIN UPDATE.
	 * 활성 Spring 트랜잭션이 있으면 해당 커넥션 재사용, 없으면 독립 커넥션으로 직접 커밋.
	 * (배치 raw-apply 플로우는 트랜잭션 없이 호출하므로 독립 커넥션 경로 필수)
	 */
	private void updateRankerRtpvpReplayRawAppliedOnTxConnection(Collection<Long> rids) {
		if (rids == null || rids.isEmpty()) {
			return;
		}
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			Connection conn = DataSourceUtils.getConnection(dataSource);
			try {
				RtaBulkRidTempTable.updateRankerRtpvpReplayRawApplied(conn, rids);
			} catch (SQLException | IOException e) {
				throw new IllegalStateException("ranker_rtpvp_replay_raw applied marking failed", e);
			} finally {
				DataSourceUtils.releaseConnection(conn, dataSource);
			}
			return;
		}
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);
			try {
				RtaBulkRidTempTable.updateRankerRtpvpReplayRawApplied(conn, rids);
				conn.commit();
			} catch (SQLException | IOException e) {
				try { conn.rollback(); } catch (SQLException re) { log.warn("applied marking rollback 실패", re); }
				throw new IllegalStateException("ranker_rtpvp_replay_raw applied marking failed", e);
			}
		} catch (IllegalStateException e) {
			throw e;
		} catch (SQLException e) {
			throw new IllegalStateException("ranker_rtpvp_replay_raw applied marking failed", e);
		}
	}

	private void updateRankerRtpvpReplayRawFailedOnTxConnection(Collection<Long> rids, String message) {
		if (rids == null || rids.isEmpty()) {
			return;
		}
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			Connection conn = DataSourceUtils.getConnection(dataSource);
			try {
				RtaBulkRidTempTable.updateRankerRtpvpReplayRawFailed(conn, rids, message);
			} catch (SQLException | IOException e) {
				throw new IllegalStateException("ranker_rtpvp_replay_raw failed marking failed", e);
			} finally {
				DataSourceUtils.releaseConnection(conn, dataSource);
			}
			return;
		}
		try (Connection conn = dataSource.getConnection()) {
			conn.setAutoCommit(false);
			try {
				RtaBulkRidTempTable.updateRankerRtpvpReplayRawFailed(conn, rids, message);
				conn.commit();
			} catch (SQLException | IOException e) {
				try { conn.rollback(); } catch (SQLException re) { log.warn("failed marking rollback 실패", re); }
				throw new IllegalStateException("ranker_rtpvp_replay_raw failed marking failed", e);
			}
		} catch (IllegalStateException e) {
			throw e;
		} catch (SQLException e) {
			throw new IllegalStateException("ranker_rtpvp_replay_raw failed marking failed", e);
		}
	}

	@Override
	public int deleteArenaRtaOrphanChildrenGlobal() {
		int n = swMapper.deleteArenaRtaOrphanUnitsGlobal();
		n += swMapper.deleteArenaRtaOrphanUsersGlobal();
		return n;
	}

	private static Long normalizeLong(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Long) {
			return (Long) o;
		}
		if (o instanceof Number) {
			return ((Number) o).longValue();
		}
		try {
			return Long.parseLong(String.valueOf(o).trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String arenaUserPkKeyString(Long rid, Object wizardId) {
		if (rid == null || wizardId == null) {
			return null;
		}
		return rid + "|" + String.valueOf(wizardId).trim();
	}

	/**
	 * RTA 업로드 JSON {@code win_lose} 의 승패 해석 ({@link #applyArenaRtaUploadFromParsedItemsWithMode}) — participant{@code .is_winner}와 동일 규약.
	 * 승: boolean true, 숫자 1, 문자열 {@code "1"} / {@code "true"} / {@code "t"}; 패: false, 2, {@code "2"} / {@code "false"} / {@code "f"}; 그 외·null: 패.
	 */
	static boolean arenaWinLoseMeansVictory(Object winLose) {
		if (winLose == null) {
			return false;
		}
		if (winLose instanceof Boolean b) {
			return b.booleanValue();
		}
		if (winLose instanceof Number n) {
			return n.intValue() == 1;
		}
		String s = String.valueOf(winLose).trim();
		if (s.isEmpty()) {
			return false;
		}
		if ("1".equals(s)) {
			return true;
		}
		if ("2".equals(s)) {
			return false;
		}
		if ("true".equalsIgnoreCase(s) || "t".equals(s)) {
			return true;
		}
		if ("false".equalsIgnoreCase(s) || "f".equals(s)) {
			return false;
		}
		try {
			return Integer.parseInt(s) == 1;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private static String normalizeWizardIdNullable(Object wizardId) {
		if (wizardId == null) {
			return null;
		}
		String s = String.valueOf(wizardId).trim();
		return s.isEmpty() ? null : s;
	}

	private static String resolveArenaMatchWinnerWizardIdFromParticipants(
			Map<String, Object> userList,
			Object fallbackRootWizardId) {
		if (userList == null || userList.isEmpty()) {
			return normalizeWizardIdNullable(fallbackRootWizardId);
		}
		String winnerWizard = null;
		for (Object val : userList.values()) {
			if (!(val instanceof Map)) {
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> ui = (Map<String, Object>) val;
			if (!Boolean.TRUE.equals(ui.get("is_winner"))) {
				continue;
			}
			String wzs = normalizeWizardIdNullable(ui.get("wizard_id"));
			if (wzs == null) {
				continue;
			}
			if (winnerWizard != null && !winnerWizard.equals(wzs)) {
				log.warn("[rta-upload] 동일 rid 에 is_winner=true 인 소환사가 서로 다릅니다 — 우선 [{}] 유지, [{}] 무시",
						winnerWizard, wzs);
				continue;
			}
			winnerWizard = wzs;
		}
		if (winnerWizard != null) {
			return winnerWizard;
		}
		return normalizeWizardIdNullable(fallbackRootWizardId);
	}

	/**
	 * {@code team_no} 또는 {@code team_side} 필수 — DB {@link com.smw.monster.mapper.summonerswarMapper#insertArenaUserInfoBulk} 의
	 * participant {@code team_side} (1 또는 2). 업로드 객체는 {@link #normalizeJsonKeysToSnakeCase} 적용 후 호출 권장.
	 */
	private static void requireAndNormalizeArenaParticipantTeamNo(Map<String, Object> userInfo, Object ridForMessage)
			throws RtaUploadValidationException {
		if (userInfo == null) {
			throw new RtaUploadValidationException("participant 맵 없음 rid=" + ridForMessage);
		}
		Object raw = userInfo.get("team_no");
		if (raw == null) {
			raw = userInfo.get("team_side");
		}
		Object wid = userInfo.get("wizard_id");
		if (raw == null) {
			throw new RtaUploadValidationException(
					"team_no 없음 rid=" + ridForMessage + " wizard_id=" + wid);
		}
		int side;
		if (raw instanceof Number n) {
			side = n.intValue();
		} else if (raw instanceof Boolean) {
			throw new RtaUploadValidationException(
					"team_no 가 boolean 타입 rid=" + ridForMessage + " wizard_id=" + wid + " raw=" + raw);
		} else {
			String s = String.valueOf(raw).trim();
			if (s.isEmpty()) {
				throw new RtaUploadValidationException(
						"team_no 빈 문자열 rid=" + ridForMessage + " wizard_id=" + wid);
			}
			try {
				side = Integer.parseInt(s);
			} catch (NumberFormatException e) {
				throw new RtaUploadValidationException(
						"team_no 파싱 실패 rid=" + ridForMessage + " wizard_id=" + wid + " raw=" + raw + ": "
								+ e.getMessage());
			}
		}
		if (side != 1 && side != 2) {
			throw new RtaUploadValidationException(
					"team_no 는 1 또는 2 여야 함 rid=" + ridForMessage + " wizard_id=" + wid + " value=" + side);
		}
		userInfo.put("team_no", side);
	}

	private static LinkedHashMap<String, Object> mutableArenaUserRowCopy(Map<String, ?> row) {
		LinkedHashMap<String, Object> m = new LinkedHashMap<>();
		for (Map.Entry<String, ?> e : row.entrySet()) {
			if (e.getKey() != null) {
				m.put(e.getKey(), e.getValue());
			}
		}
		return m;
	}

	@Override
	public int insertArenaInfo(Map<String, ?> param) {
		if (param == null) {
			return 0;
		}
		if (param instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> mo = (Map<String, Object>) param;
			normalizeArenaReplayDateAdd(mo);
		}
		applyRtaSeasonIdsToArenaRows(Collections.singletonList(param));
		return swMapper.insertArenaInfoBulk(Collections.singletonList(param));
	}

	@Override
	public int insertArenaUserInfo(Map<String, ?> param) {
		if (param == null) {
			return 0;
		}
		if (!(param instanceof Map)) {
			throw new RtaUploadValidationException("insertArenaUserInfo: Map 아님");
		}
		LinkedHashMap<String, Object> row = mutableArenaUserRowCopy(param);
		normalizeJsonKeysToSnakeCase(row);
		requireAndNormalizeArenaParticipantTeamNo(row, row.get("rid"));
		return swMapper.insertArenaUserInfoBulk(Collections.singletonList(row));
	}

	@Override
	public int insertArenaPickInfo(Map<String, ?> param) {
		/* v2: 픽 메타는 rta_match_participant / rta_match_unit_pick 에만 적재 */
		return 0;
	}

	@Override
	public int insertArenaUnitInfo(Map<String, ?> param) {
		if (param == null) {
			return 0;
		}
		return swMapper.insertArenaUnitInfoBulk(Collections.singletonList(param));
	}

	/** {@link RtaRawApplyProperties#getBulkInsertChunkSize()} — 최소 1 */
	private int arenaRtaBulkChunkSize() {
		int n = rtaRawApplyProperties.getBulkInsertChunkSize();
		return Math.max(1, Math.min(n, 500));
	}

	@Override
	public int insertArenaInfoBatch(List<Map<String, ?>> rows) {
		if (rows == null || rows.isEmpty()) {
			return 0;
		}
		int total = 0;
		int step = arenaRtaBulkChunkSize();
		for (int from = 0; from < rows.size(); from += step) {
			int to = Math.min(from + step, rows.size());
			List<Map<String, ?>> chunk = rows.subList(from, to);
			for (Map<String, ?> row : chunk) {
				if (row instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> mo = (Map<String, Object>) row;
					normalizeArenaReplayDateAdd(mo);
				}
			}
			applyRtaSeasonIdsToArenaRows(chunk);
			total += swMapper.insertArenaInfoBulk(chunk);
		}
		return total;
	}

	@Override
	public int insertArenaUserInfoBatch(List<Map<String, ?>> rows) {
		if (rows == null || rows.isEmpty()) {
			return 0;
		}
		int total = 0;
		int step = arenaRtaBulkChunkSize();
		for (int from = 0; from < rows.size(); from += step) {
			int to = Math.min(from + step, rows.size());
			List<Map<String, ?>> validated = new ArrayList<>(to - from);
			for (int i = from; i < to; i++) {
				Map<String, ?> src = rows.get(i);
				if (!(src instanceof Map)) {
					throw new RtaUploadValidationException(
							"insertArenaUserInfoBatch: Map 아님 index=" + i);
				}
				LinkedHashMap<String, Object> row = mutableArenaUserRowCopy(src);
				normalizeJsonKeysToSnakeCase(row);
				requireAndNormalizeArenaParticipantTeamNo(row, row.get("rid"));
				validated.add(row);
			}
			total += swMapper.insertArenaUserInfoBulk(validated);
		}
		return total;
	}

	@Override
	public int insertArenaPickInfoBatch(List<Map<String, ?>> rows) {
		return 0;
	}

	@Override
	public int insertArenaUnitInfoBatch(List<Map<String, ?>> rows) {
		if (rows == null || rows.isEmpty()) {
			return 0;
		}
		int total = 0;
		int step = arenaRtaBulkChunkSize();
		for (int from = 0; from < rows.size(); from += step) {
			int to = Math.min(from + step, rows.size());
			List<Map<String, ?>> chunk = rows.subList(from, to);
			total += swMapper.insertArenaUnitInfoBulk(chunk);
		}
		return total;
	}

	private static void filterArenaRtaRowsAlreadyInDb(
			Set<String> existingPk,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch) {
		if (existingPk == null || existingPk.isEmpty()) {
			return;
		}
		List<Map<String, ?>> u2 = new ArrayList<>();
		List<Map<String, ?>> p2 = new ArrayList<>();
		for (int i = 0; i < userBatch.size(); i++) {
			Map<String, ?> u = userBatch.get(i);
			Long rid = normalizeLong(u.get("rid"));
			String pk = arenaUserPkKeyString(rid, u.get("wizard_id"));
			if (pk != null && existingPk.contains(pk)) {
				log.debug("[rta-upload] DB에 이미 있는 user_list 행 제외 {}", pk);
				continue;
			}
			u2.add(u);
			p2.add(pickBatch.get(i));
		}
		userBatch.clear();
		userBatch.addAll(u2);
		pickBatch.clear();
		pickBatch.addAll(p2);
		Set<String> kept = new HashSet<>();
		for (Map<String, ?> u : u2) {
			String pk = arenaUserPkKeyString(normalizeLong(u.get("rid")), u.get("wizard_id"));
			if (pk != null) {
				kept.add(pk);
			}
		}
		List<Map<String, ?>> units2 = new ArrayList<>();
		for (Map<String, ?> row : unitBatch) {
			String pk = arenaUserPkKeyString(normalizeLong(row.get("rid")), row.get("wizard_id"));
			if (pk != null && kept.contains(pk)) {
				units2.add(row);
			}
		}
		unitBatch.clear();
		unitBatch.addAll(units2);
	}

	private static void pruneArenaBatchWithoutUserRows(
			List<Map<String, ?>> arenaBatch,
			List<Map<String, ?>> userBatch) {
		if (arenaBatch.isEmpty() || userBatch.isEmpty()) {
			if (userBatch.isEmpty()) {
				arenaBatch.clear();
			}
			return;
		}
		Set<Long> ridsWithUser = new HashSet<>();
		for (Map<String, ?> u : userBatch) {
			Long rid = normalizeLong(u.get("rid"));
			if (rid != null) {
				ridsWithUser.add(rid);
			}
		}
		arenaBatch.removeIf((Map<String, ?> a) -> {
			Long rid = normalizeLong(a.get("rid"));
			return rid == null || !ridsWithUser.contains(rid);
		});
	}

	/**
	 * RTA JSON 키를 DB/MyBatis snake_case 컬럼명과 맞춘다 (camelCase, {@code channeluid} 등).
	 * {@code user_info}·{@code pick_info}·{@code unit_list} 행에 적용.
	 */
	private static void normalizeArenaUserInfoJsonKeysForDb(Map<String, Object> userInfo) {
		if (userInfo == null) {
			return;
		}
		normalizeJsonKeysToSnakeCase(userInfo);
		Object pickObj = userInfo.get("pick_info");
		if (pickObj instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> pick = (Map<String, Object>) pickObj;
			normalizeJsonKeysToSnakeCase(pick);
			Object unitsObj = pick.get("unit_list");
			if (unitsObj instanceof List<?> ul) {
				for (Object row : ul) {
					if (row instanceof Map) {
						@SuppressWarnings("unchecked")
						Map<String, Object> unit = (Map<String, Object>) row;
						normalizeJsonKeysToSnakeCase(unit);
					}
				}
			}
		}
	}

	/**
	 * '_' 가 없는 camelCase 키를 snake_case 로 옮기고, 원 키는 제거한다.
	 * 이미 snake_case 인 키( '_' 포함)는 그대로 둔다.
	 */
	private static void normalizeJsonKeysToSnakeCase(Map<String, Object> map) {
		if (map == null || map.isEmpty()) {
			return;
		}
		List<String> removeKeys = new ArrayList<>();
		for (Map.Entry<String, Object> e : new ArrayList<>(map.entrySet())) {
			String k = e.getKey();
			if (k == null) {
				continue;
			}
			String snake = jsonKeyToSnakeColumn(k);
			if (snake.equals(k)) {
				continue;
			}
			Object v = e.getValue();
			if (v == null) {
				continue;
			}
			Object cur = map.get(snake);
			if (cur != null && !String.valueOf(cur).trim().isEmpty()) {
				removeKeys.add(k);
				continue;
			}
			map.put(snake, v);
			removeKeys.add(k);
		}
		for (String k : removeKeys) {
			map.remove(k);
		}
	}

	/**
	 * 리플레이 일시 — 게임/JSON이 epoch(ms·초), YYYYMMDD 정수, 날짜만 문자열 등 혼재.
	 * 정규화 적재 시 {@code rta_match.played_at} 등에 쓰기 위해,
	 * 한국 서비스 기준으로 해석한 절대시각을 {@link Timestamp} 로 넣어 JDBC/PostgreSQL 오인식을 줄인다.
	 */
	private static final ZoneId RTA_REPLAY_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter RTA_DT_SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter RTA_DT_T = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

	private static void normalizeArenaReplayDateAdd(Map<String, Object> arena) {
		if (arena == null) {
			return;
		}
		Object d = arena.get("date_add");
		if (d == null) {
			return;
		}
		if (d instanceof String && ((String) d).trim().isEmpty()) {
			arena.remove("date_add");
			return;
		}
		try {
			Timestamp ts = parseArenaDateAddToSqlTimestamp(d);
			if (ts != null) {
				arena.put("date_add", ts);
			}
		} catch (Exception e) {
			log.warn("[rta-upload] date_add 정규화 실패, 원본 유지: {} — {}", d, e.toString());
		}
	}

	/**
	 * @return null 이면 변환 불가(원본 유지). 파싱 성공 시 항상 non-null.
	 */
	private static Timestamp parseArenaDateAddToSqlTimestamp(Object d) {
		if (d == null) {
			return null;
		}
		if (d instanceof Timestamp) {
			return (Timestamp) d;
		}
		if (d instanceof java.util.Date) {
			return new Timestamp(((java.util.Date) d).getTime());
		}
		if (d instanceof BigDecimal) {
			return parseArenaDateAddToSqlTimestamp(((BigDecimal) d).longValue());
		}
		if (d instanceof Number) {
			long n = ((Number) d).longValue();
			double dn = ((Number) d).doubleValue();
			String ns = String.valueOf(n);
			// YYYYMMDD (게임이 날짜만 숫자로 줄 때)
			if (n >= 19000101L && n <= 21001231L && n == (long) dn && ns.length() == 8) {
				int y = (int) (n / 10000L);
				int m = (int) ((n % 10000L) / 100L);
				int day = (int) (n % 100L);
				LocalDate ld = LocalDate.of(y, m, day);
				return Timestamp.from(ld.atStartOfDay(RTA_REPLAY_ZONE).toInstant());
			}
			if (n >= 1_000_000_000_000L) {
				return new Timestamp(n);
			}
			if (n >= 1_000_000_000L) {
				return new Timestamp(n * 1000L);
			}
		}
		if (d instanceof String) {
			String s = ((String) d).trim();
			if (s.isEmpty()) {
				return null;
			}
			try {
				return Timestamp.from(OffsetDateTime.parse(s).toInstant());
			} catch (DateTimeParseException ignored) {
			}
			try {
				return Timestamp.from(Instant.parse(s));
			} catch (DateTimeParseException ignored) {
			}
			if (s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
				LocalDate ld = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
				return Timestamp.from(ld.atStartOfDay(RTA_REPLAY_ZONE).toInstant());
			}
			try {
				LocalDateTime ldt = LocalDateTime.parse(s, RTA_DT_SPACE);
				return Timestamp.from(ldt.atZone(RTA_REPLAY_ZONE).toInstant());
			} catch (DateTimeParseException ignored) {
			}
			try {
				LocalDateTime ldt = LocalDateTime.parse(s, RTA_DT_T);
				return Timestamp.from(ldt.atZone(RTA_REPLAY_ZONE).toInstant());
			} catch (DateTimeParseException ignored) {
			}
		}
		return null;
	}

	/** '_' 없음 + 대문자 → camelToSnake; '_' 없음 + channeluid → channel_uid */
	private static String jsonKeyToSnakeColumn(String key) {
		if (key == null) {
			return "";
		}
		if (key.indexOf('_') >= 0) {
			return mapReplayListJsonKeyAliases(key);
		}
		if ("channeluid".equalsIgnoreCase(key)) {
			return "channel_uid";
		}
		for (int i = 0; i < key.length(); i++) {
			if (Character.isUpperCase(key.charAt(i))) {
				return mapReplayListJsonKeyAliases(camelToSnake(key));
			}
		}
		return mapReplayListJsonKeyAliases(key);
	}

	/**
	 * camelCase → snake 과정에서 {@code infoCsv} → {@code info_csv} 가 되나 DB 컬럼은 {@code infocsv}.
	 */
	private static String mapReplayListJsonKeyAliases(String snakeOrKey) {
		if (snakeOrKey == null) {
			return "";
		}
		if ("info_csv".equals(snakeOrKey)) {
			return "infocsv";
		}
		return snakeOrKey;
	}

	private static String camelToSnake(String s) {
		StringBuilder b = new StringBuilder();
		b.append(Character.toLowerCase(s.charAt(0)));
		for (int i = 1; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isUpperCase(c)) {
				b.append('_').append(Character.toLowerCase(c));
			} else {
				b.append(c);
			}
		}
		return b.toString();
	}

	/**
	 * 누적 로그 등으로 동일 rid 전투가 여러 번 들어온 경우 첫 행만 유지.
	 * user/pick 은 (rid, wizard_id), unit 은 동일 (rid, wizard_id) 및 pick_slot 기준 중복 제거.
	 */
	private void dedupeArenaRtaUploadBatches(
			List<Map<String, ?>> arenaBatch,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch) {
		if (arenaBatch == null || arenaBatch.isEmpty()) {
			return;
		}
		LinkedHashMap<Long, Map<String, ?>> arenaByRid = new LinkedHashMap<>();
		for (Map<String, ?> row : arenaBatch) {
			Long rid = normalizeLong(row != null ? row.get("rid") : null);
			if (rid == null) {
				continue;
			}
			arenaByRid.putIfAbsent(rid, row);
		}
		if (arenaByRid.size() < arenaBatch.size()) {
			log.info("[rta-upload] 요청 내 동일 rid 전투 중복 {}건 제거 (첫 행만 유지)", arenaBatch.size() - arenaByRid.size());
		}
		arenaBatch.clear();
		arenaBatch.addAll(arenaByRid.values());

		LinkedHashSet<String> seenUserPk = new LinkedHashSet<>();
		List<Map<String, ?>> u2 = new ArrayList<>();
		List<Map<String, ?>> p2 = new ArrayList<>();
		for (int i = 0; i < userBatch.size(); i++) {
			Map<String, ?> u = userBatch.get(i);
			Long rid = normalizeLong(u.get("rid"));
			String pk = arenaUserPkKeyString(rid, u.get("wizard_id"));
			if (pk == null || !seenUserPk.add(pk)) {
				continue;
			}
			u2.add(u);
			p2.add(pickBatch.get(i));
		}
		if (u2.size() < userBatch.size()) {
			log.debug("[rta-upload] 요청 내 (rid, wizard_id) 중복 user/pick 행 제거");
		}
		userBatch.clear();
		userBatch.addAll(u2);
		pickBatch.clear();
		pickBatch.addAll(p2);

		Set<String> keptUserPk = new HashSet<>();
		for (Map<String, ?> u : u2) {
			String pk = arenaUserPkKeyString(normalizeLong(u.get("rid")), u.get("wizard_id"));
			if (pk != null) {
				keptUserPk.add(pk);
			}
		}
		List<Map<String, ?>> unitsFiltered = new ArrayList<>();
		for (Map<String, ?> row : unitBatch) {
			String pk = arenaUserPkKeyString(normalizeLong(row.get("rid")), row.get("wizard_id"));
			if (pk != null && keptUserPk.contains(pk)) {
				unitsFiltered.add(row);
			}
		}
		LinkedHashSet<String> seenUnitKey = new LinkedHashSet<>();
		List<Map<String, ?>> units2 = new ArrayList<>();
		for (Map<String, ?> row : unitsFiltered) {
			Long rid = normalizeLong(row.get("rid"));
			Object w = row.get("wizard_id");
			Object slot = row.get("pick_slot_id");
			String uk = rid + "|" + String.valueOf(w).trim() + "|" + String.valueOf(slot);
			if (seenUnitKey.add(uk)) {
				units2.add(row);
			}
		}
		unitBatch.clear();
		unitBatch.addAll(units2);
	}

	private static void removeArenaRtaRowsByRids(
			Set<Long> dropRids,
			List<Map<String, ?>> arenaBatch,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch) {
		if (dropRids == null || dropRids.isEmpty()) {
			return;
		}
		arenaBatch.removeIf((Map<String, ?> a) -> {
			Long rid = normalizeLong(a.get("rid"));
			return rid != null && dropRids.contains(rid);
		});
		List<Map<String, ?>> u2 = new ArrayList<>();
		List<Map<String, ?>> p2 = new ArrayList<>();
		for (int i = 0; i < userBatch.size(); i++) {
			Long rid = normalizeLong(userBatch.get(i).get("rid"));
			if (rid != null && dropRids.contains(rid)) {
				continue;
			}
			u2.add(userBatch.get(i));
			p2.add(pickBatch.get(i));
		}
		userBatch.clear();
		userBatch.addAll(u2);
		pickBatch.clear();
		pickBatch.addAll(p2);
		unitBatch.removeIf((Map<String, ?> row) -> {
			Long rid = normalizeLong(row.get("rid"));
			return rid != null && dropRids.contains(rid);
		});
	}

	/**
	 * rta-upload: 매치 원본 JSON(rid) → 원본 스테이징, 이후 정규화 테이블 벌크.
	 */
	private List<Map<String, Object>> buildArenaReplayRawRows(List<Map<String, ?>> arenaRows) {
		List<Map<String, Object>> out = new ArrayList<>();
		if (arenaRows == null) {
			return out;
		}
		for (Map<String, ?> row : arenaRows) {
			Long rid = normalizeLong(row != null ? row.get("rid") : null);
			if (rid == null) {
				continue;
			}
			try {
				Map<String, Object> m = new HashMap<>();
				m.put("rid", rid);
				m.put("payload", objectMapper.writeValueAsString(row));
				out.add(m);
			} catch (JsonProcessingException e) {
				throw new IllegalStateException("RTA 원본 JSON 직렬화 실패 rid=" + rid, e);
			}
		}
		out.sort(Comparator.comparingLong(m -> (Long) m.get("rid")));
		return out;
	}

	/**
	 * ranker_rtpvp_replay_raw 갱신 시 세션 간 행 잠금 순서를 맞추기 위해 rid 오름차순·유일화.
	 */
	private List<Long> sortedUniqueRidsFromArenaRows(List<Map<String, ?>> arenaRows) {
		if (arenaRows == null || arenaRows.isEmpty()) {
			return Collections.emptyList();
		}
		TreeSet<Long> unique = new TreeSet<>();
		for (Map<String, ?> row : arenaRows) {
			Long rid = normalizeLong(row != null ? row.get("rid") : null);
			if (rid != null) {
				unique.add(rid);
			}
		}
		return new ArrayList<>(unique);
	}

	/**
	 * rta-upload: 우선 {@code COPY FROM STDIN} + TEMP → 본 테이블(설정 시), 실패 시 VALUES 다중행 + ON CONFLICT.
	 */
	private void insertArenaRtaBulkInChunks(
			List<Map<String, ?>> arenaRows,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch,
			ArenaRtaPersistMode persistMode) {
		if (arenaRows == null || arenaRows.isEmpty()) {
			return;
		}
		for (Map<String, ?> row : arenaRows) {
			if (row != null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> mo = (Map<String, Object>) (Map<?, ?>) row;
				normalizeArenaReplayDateAdd(mo);
			}
		}
		applyRtaSeasonIdsToArenaRows(arenaRows);

		if (rtaRawApplyProperties.isCopyBulkInsertEnabled()) {
			Connection conn = null;
			try {
				conn = DataSourceUtils.getConnection(dataSource);
				ArenaRtaPersistMode mode = persistMode != null ? persistMode : ArenaRtaPersistMode.FULL;
			boolean writeRaw = mode == ArenaRtaPersistMode.FULL;
			List<Map<String, Object>> rawForCopy = Collections.emptyList();
				if (writeRaw) {
					rawForCopy = buildArenaReplayRawRows(arenaRows);
				}
				List<Long> appliedRids = Collections.emptyList();
				boolean markApplied = mode == ArenaRtaPersistMode.FULL || mode == ArenaRtaPersistMode.NORMALIZED_ONLY;
				if (markApplied) {
					appliedRids = sortedUniqueRidsFromArenaRows(arenaRows);
				}
				if (arenaRtaUploadCopyBulkService.flushViaCopy(conn, rawForCopy, arenaRows, userBatch, unitBatch,
						appliedRids, mode)) {
					return;
				}
			} catch (Exception e) {
				log.warn("[rta-upload] COPY 벌크 예외 — VALUES 다중행으로 폴백: {}", e.toString());
			} finally {
				if (conn != null) {
					DataSourceUtils.releaseConnection(conn, dataSource);
				}
			}
		}

		insertArenaRtaBulkInChunksLegacy(arenaRows, userBatch, pickBatch, unitBatch, persistMode);
	}

	/**
	 * {@link #insertArenaRtaBulkInChunks} 폴백: MyBatis 다중 행 INSERT (이미 date_add·season_id 정규화됨).
	 */
	private void insertArenaRtaBulkInChunksLegacy(
			List<Map<String, ?>> arenaRows,
			List<Map<String, ?>> userBatch,
			@SuppressWarnings("unused") List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch,
			ArenaRtaPersistMode persistMode) {
		ArenaRtaPersistMode mode = persistMode != null ? persistMode : ArenaRtaPersistMode.FULL;
		boolean writeRaw = mode == ArenaRtaPersistMode.FULL;
		boolean writeNormalized = mode == ArenaRtaPersistMode.FULL || mode == ArenaRtaPersistMode.NORMALIZED_ONLY;
		boolean markRawApplied = mode == ArenaRtaPersistMode.FULL || mode == ArenaRtaPersistMode.NORMALIZED_ONLY;

		summonerswarMapper batchMapper = rtaBatchSqlSessionTemplate.getMapper(summonerswarMapper.class);
		int step = arenaRtaBulkChunkSize();
		try {
			if (writeRaw) {
				List<Map<String, Object>> rawRows = buildArenaReplayRawRows(arenaRows);
				if (!rawRows.isEmpty()) {
					for (int from = 0; from < rawRows.size(); from += step) {
						int to = Math.min(from + step, rawRows.size());
						List<Map<String, Object>> chunk = rawRows.subList(from, to);
						batchMapper.insertArenaReplayRawBulk(chunk);
						batchMapper.insertArenaReplayRawPayloadBulk(chunk);
					}
				}
			}
			if (writeNormalized) {
				for (int from = 0; from < arenaRows.size(); from += step) {
					int to = Math.min(from + step, arenaRows.size());
					List<Map<String, ?>> chunk = arenaRows.subList(from, to);
					batchMapper.insertArenaInfoBulk(chunk);
				}
				if (userBatch != null && !userBatch.isEmpty()) {
					// INSERT...SELECT FROM VALUES — reWriteBatchedInserts 대상 아님, BATCH executor 불필요
					for (int from = 0; from < userBatch.size(); from += step) {
						int to = Math.min(from + step, userBatch.size());
						swMapper.insertArenaUserInfoBulk(userBatch.subList(from, to));
					}
				}
				if (unitBatch != null && !unitBatch.isEmpty()) {
					for (int from = 0; from < unitBatch.size(); from += step) {
						int to = Math.min(from + step, unitBatch.size());
						batchMapper.insertArenaUnitInfoBulk(unitBatch.subList(from, to));
					}
				}
			}
			if (markRawApplied) {
				List<Long> appliedRids = sortedUniqueRidsFromArenaRows(arenaRows);
				if (!appliedRids.isEmpty()) {
					updateRankerRtpvpReplayRawAppliedOnTxConnection(appliedRids);
				}
			}
		} finally {
			rtaBatchSqlSessionTemplate.flushStatements();
		}
	}

	/**
	 * rta-upload: 선행 필터 후 한 트랜잭션에서 원본 JSON(rid) → replay → user → pick → unit 순 벌크 INSERT.
	 * rta_match 없이 남은 고아 행은 {@link #deleteArenaRtaOrphanChildrenGlobal()} 배치에서 정리.
	 */
	@Override
	public ArenaRtaUploadApplyResult applyArenaRtaUploadPersistence(
			List<Map<String, ?>> arenaBatch,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch) {
		return applyArenaRtaUploadPersistence(arenaBatch, userBatch, pickBatch, unitBatch, ArenaRtaPersistMode.FULL);
	}

	@Override
	public ArenaRtaUploadApplyResult applyArenaRtaUploadPersistence(
			List<Map<String, ?>> arenaBatch,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch,
			ArenaRtaPersistMode persistMode) {
		ArenaRtaPersistMode mode = persistMode != null ? persistMode : ArenaRtaPersistMode.FULL;
		if (arenaBatch == null || arenaBatch.isEmpty()) {
			return new ArenaRtaUploadApplyResult(0, 0);
		}
		dedupeArenaRtaUploadBatches(arenaBatch, userBatch, pickBatch, unitBatch);
		if (arenaBatch.isEmpty()) {
			return new ArenaRtaUploadApplyResult(0, 0);
		}

		int dupSkipped = 0;
		LinkedHashSet<Long> candidateRids = new LinkedHashSet<>();
		for (Map<String, ?> row : arenaBatch) {
			Long r = normalizeLong(row != null ? row.get("rid") : null);
			if (r != null) {
				candidateRids.add(r);
			}
		}
		Set<Long> alreadyInReplay = selectArenaRidsExisting(candidateRids);
		if (!alreadyInReplay.isEmpty()) {
			log.warn("[rta-upload] 이미 rta_match 에 있는 rid 제외 (누적 로그·재업로드 등): {}", alreadyInReplay);
			removeArenaRtaRowsByRids(alreadyInReplay, arenaBatch, userBatch, pickBatch, unitBatch);
			pruneArenaBatchWithoutUserRows(arenaBatch, userBatch);
			dupSkipped += alreadyInReplay.size();
		}
		if (arenaBatch.isEmpty()) {
			return new ArenaRtaUploadApplyResult(0, dupSkipped);
		}

		LinkedHashSet<Long> newArenaRids = new LinkedHashSet<>();
		for (Map<String, ?> row : arenaBatch) {
			Long r = normalizeLong(row != null ? row.get("rid") : null);
			if (r != null) {
				newArenaRids.add(r);
			}
		}
		Set<String> existingUserPkInDb = selectArenaUserPkKeysExisting(newArenaRids);
		filterArenaRtaRowsAlreadyInDb(existingUserPkInDb, userBatch, pickBatch, unitBatch);
		pruneArenaBatchWithoutUserRows(arenaBatch, userBatch);
		if (!arenaBatch.isEmpty()) {
			LinkedHashSet<Long> ridsToInsert = new LinkedHashSet<>();
			for (Map<String, ?> row : arenaBatch) {
				Long r = normalizeLong(row != null ? row.get("rid") : null);
				if (r != null) {
					ridsToInsert.add(r);
				}
			}
			Set<Long> replayExistsNow = selectArenaRidsExisting(ridsToInsert);
			if (!replayExistsNow.isEmpty()) {
				log.warn("[rta-upload] INSERT 직전 rta_match 에 이미 존재하는 rid 제외 (동시 업로드 등): {}", replayExistsNow);
				removeArenaRtaRowsByRids(replayExistsNow, arenaBatch, userBatch, pickBatch, unitBatch);
				pruneArenaBatchWithoutUserRows(arenaBatch, userBatch);
				dupSkipped += replayExistsNow.size();
			}
		}
		if (!arenaBatch.isEmpty()) {
			LinkedHashMap<Long, Map<String, ?>> arenaByRid = new LinkedHashMap<>();
			for (Map<String, ?> row : arenaBatch) {
				Long r = normalizeLong(row != null ? row.get("rid") : null);
				if (r != null) {
					arenaByRid.put(r, row);
				}
			}
			List<Map<String, ?>> arenaRows = new ArrayList<>(arenaByRid.values());
			arenaRtaPersistTransactionTemplate.executeWithoutResult(status ->
					insertArenaRtaBulkInChunks(arenaRows, userBatch, pickBatch, unitBatch, mode));
		}
		return new ArenaRtaUploadApplyResult(0, dupSkipped);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Integer> applyArenaRtaUploadFromParsedItems(List<Map<String, ?>> log_list) {
		return applyArenaRtaUploadFromParsedItemsWithMode(log_list, ArenaRtaPersistMode.FULL);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Integer> applyArenaRtaNormalizedChunk(List<Map<String, ?>> log_list) {
		return applyArenaRtaUploadFromParsedItemsWithMode(log_list, ArenaRtaPersistMode.NORMALIZED_ONLY);
	}

	/** API 공통: 파싱 로그 → 배치 적재 (FULL 또는 NORMALIZED_ONLY). */
	@SuppressWarnings("unchecked")
	private Map<String, Integer> applyArenaRtaUploadFromParsedItemsWithMode(
			List<Map<String, ?>> log_list,
			ArenaRtaPersistMode persistMode) {
		Map<String, Integer> empty = new HashMap<>();
		empty.put("success", 0);
		empty.put("fail", 0);
		if (log_list == null || log_list.isEmpty()) {
			return empty;
		}
		int success = 0;
		int fail = 0;
		LinkedHashSet<Long> distinctRids = new LinkedHashSet<>();
		for (Map<String, ?> row : log_list) {
			Long rid = normalizeLong(row != null ? row.get("rid") : null);
			if (rid != null) {
				distinctRids.add(rid);
			}
		}
		Set<Long> existingInDb = selectArenaRidsExisting(distinctRids);
		Set<Long> seenInRequest = new HashSet<>();
		List<Map<String, ?>> arenaBatch = new ArrayList<>();
		List<Map<String, ?>> userBatch = new ArrayList<>();
		List<Map<String, ?>> pickBatch = new ArrayList<>();
		List<Map<String, ?>> unitBatch = new ArrayList<>();
		Set<String> seenArenaUserPk = new HashSet<>();
		for (Map<String, ?> list : log_list) {
			Long rid = normalizeLong(list.get("rid"));
			if (rid == null) {
				log.warn("[rta-upload] rid 없음·파싱 불가");
				fail += 1;
				continue;
			}
			if (existingInDb.contains(rid)) {
				fail += 1;
				continue;
			}
			if (seenInRequest.contains(rid)) {
				fail += 1;
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> arenaRow = (Map<String, Object>) list;
			normalizeJsonKeysToSnakeCase(arenaRow);
			Object userListObj = arenaRow.get("user_list");
			if (userListObj == null || !(userListObj instanceof Map)) {
				log.warn("[rta-upload] user_list 없음·형식 오류 rid={}", arenaRow.get("rid"));
				fail += 1;
				continue;
			}
			Map<String, Object> user_list = (Map<String, Object>) userListObj;
			for (Map.Entry<String, Object> entry : user_list.entrySet()) {
				Object val = entry.getValue();
				if (!(val instanceof Map)) {
					continue;
				}
				Map<String, Object> user_info = (Map<String, Object>) val;
				normalizeArenaUserInfoJsonKeysForDb(user_info);
				if (user_info.get("pick_info") == null) {
					String msg = "pick_info 없음 rid=" + list.get("rid") + " wizard_id=" + user_info.get("wizard_id");
					log.warn("[rta-upload] {}", msg);
					throw new RtaUploadValidationException(msg);
				}
				requireAndNormalizeArenaParticipantTeamNo(user_info, list.get("rid"));
			}
			seenInRequest.add(rid);
			arenaBatch.add(list);
			success += 1;
			for (Map.Entry<String, Object> entry : user_list.entrySet()) {
				Object val = entry.getValue();
				if (!(val instanceof Map)) {
					continue;
				}
				Map<String, Object> user_info = (Map<String, Object>) val;
				user_info.put("rid", list.get("rid"));
				if (user_info.containsKey("rank")) {
					if (user_info.get("rank_no") == null) {
						user_info.put("rank_no", user_info.get("rank"));
					}
					user_info.remove("rank");
				}
				String userPk = arenaUserPkKeyString(rid, user_info.get("wizard_id"));
				if (userPk == null) {
					String msg = "wizard_id 없음 rid=" + list.get("rid");
					log.warn("[rta-upload] {}", msg);
					throw new RtaUploadValidationException(msg);
				}
				if (!seenArenaUserPk.add(userPk)) {
					log.debug("[rta-upload] 동일 (rid, wizard_id) 중복 건너뜀 {}", userPk);
					continue;
				}
				userBatch.add(user_info);

				Map<String, Object> pick_info = (Map<String, Object>) user_info.get("pick_info");
				if (pick_info == null) {
					throw new RtaUploadValidationException("pick_info 없음 rid=" + list.get("rid"));
				}
				user_info.put("leader_pick_slot", pick_info.get("leader_slot_id"));
				user_info.put("is_winner", arenaWinLoseMeansVictory(user_info.get("win_lose")));
				pick_info.put("rid", list.get("rid"));
				pick_info.put("wizard_id", user_info.get("wizard_id"));
				List<?> banList = (List<?>) pick_info.get("banned_slot_ids");
				if (banList != null && !banList.isEmpty()) {
					pick_info.put("banned_slot_id", banList.get(0));
				} else {
					pick_info.put("banned_slot_id", null);
				}
				pickBatch.add(pick_info);

				List<Map<String, ?>> unit_list = (List<Map<String, ?>>) pick_info.get("unit_list");
				if (unit_list == null) {
					unit_list = Collections.emptyList();
				}
				for (int k = 0; k < unit_list.size(); k++) {
					Map<String, Object> unit = (Map<String, Object>) unit_list.get(k);
					unit.put("rid", list.get("rid"));
					unit.put("wizard_id", user_info.get("wizard_id"));
					Object banSlot = pick_info != null ? pick_info.get("banned_slot_id") : null;
					Object slot = unit.get("pick_slot_id");
					boolean banned = banSlot != null && slot != null
							&& banSlot.toString().trim().equals(slot.toString().trim());
					unit.put("is_banned", banned);
					unitBatch.add(unit);
				}
			}
			String matchWinnerWizardId = resolveArenaMatchWinnerWizardIdFromParticipants(user_list, arenaRow.get("wizard_id"));
			arenaRow.put("winner_wizard_id", matchWinnerWizardId);
		}
		if (!arenaBatch.isEmpty()) {
			ArenaRtaUploadApplyResult applied = applyArenaRtaUploadPersistence(arenaBatch, userBatch, pickBatch, unitBatch, persistMode);
			int dup = applied.getDuplicateReplaySkippedCount();
			fail += dup;
			success = Math.max(0, success - dup);
		}
		if ((persistMode == ArenaRtaPersistMode.FULL || persistMode == ArenaRtaPersistMode.NORMALIZED_ONLY) && success > 0) {
			rtaCacheEvictor.evictAllRtaCaches();
		}
		Map<String, Integer> result = new HashMap<>();
		result.put("success", success);
		result.put("fail", fail);
		return result;
	}

	@Override
	public int applyPendingArenaReplayRawFromDb() {
		int maxRows = Math.max(1, rtaRawApplyProperties.getMaxRowsPerRun());
		int maxBatches = Math.max(1, rtaRawApplyProperties.getMaxBatchesPerJob());

		long t0 = System.currentTimeMillis();
		int totalApplied = 0;

		for (int batchIdx = 1; batchIdx <= maxBatches; batchIdx++) {
			long ts = System.currentTimeMillis();
			List<Map<String, ?>> pending = swMapper.selectRtaReplayRawPending(maxRows);
			long selectMs = System.currentTimeMillis() - ts;

			if (pending.isEmpty()) {
				log.info("[rta-raw-apply] 종료: 배치#{}/{}, 미처리 조회 0건 (select {}ms), 누적 적용 {}건, 경과 {}ms",
						batchIdx, maxBatches, selectMs, totalApplied, System.currentTimeMillis() - t0);
				return totalApplied;
			}

			log.info("[rta-raw-apply] 배치#{}/{}: 조회 {}건 (select {}ms), 누적 적용(이전까지) {}건",
					batchIdx, maxBatches, pending.size(), selectMs, totalApplied);
			int applied = applyPendingArenaReplayRawRows(pending);
			totalApplied += applied;
			log.info("[rta-raw-apply] 배치#{}/{}: 이번 적용 {}건, 누적 {}건, 경과 {}ms",
					batchIdx, maxBatches, applied, totalApplied, System.currentTimeMillis() - t0);

			if (applied == 0 && !pending.isEmpty()) {
				// 동일 행만 반복 선택되는 비정상 시 무한 라운드를 막음 (다음 스케줄에서 재시도)
				log.warn("[rta-raw-apply] 배치#{} 적용 0건·조회>0 → 라운드 중단", batchIdx);
				break;
			}
		}

		log.info("[rta-raw-apply] 라운드 상한 종료: maxBatches={} — 잔여 raw 는 다음 스케줄·수동 재실행에서 계속",
				maxBatches);
		return totalApplied;
	}

	/** pending/failed 행 목록에 대해 파싱·이미 replay 존재 시 bulk 적용·정규화 청크 처리. */
	private int applyPendingArenaReplayRawRows(List<Map<String, ?>> pending) {
		if (pending.isEmpty()) {
			return 0;
		}
		int chunk = Math.max(1, rtaRawApplyProperties.getApplyChunkSize());
		int applied = 0;

		// [단계 1] payload 파싱
		long t1 = System.currentTimeMillis();
		List<Map<String, ?>> parsed = new ArrayList<>();
		for (Map<String, ?> row : pending) {
			Long rid = normalizeLong(row.get("rid"));
			Object payloadObj = row.get("payload");
			if (rid == null || payloadObj == null) {
				log.warn("[rta-raw-apply] rid/payload 없음 row={}", row);
				if (rid != null) {
					updateRankerRtpvpReplayRawFailedOnTxConnection(Collections.singletonList(rid), "payload 없음");
					if (rtaRawApplyProperties.isFailFastOnError()) {
						throw new IllegalStateException("rta raw apply rid=" + rid + ": missing payload");
					}
				}
				continue;
			}
			try {
				parsed.add(parseReplayPayloadToMap(payloadObj));
			} catch (Exception e) {
				log.warn("[rta-raw-apply] rid={} payload 파싱 실패", rid, e);
				updateRankerRtpvpReplayRawFailedOnTxConnection(Collections.singletonList(rid), String.valueOf(e.getMessage()));
				if (rtaRawApplyProperties.isFailFastOnError()) {
					throw new IllegalStateException("rta raw apply rid=" + rid + " payload parse failed", e);
				}
			}
		}
		log.info("[rta-raw-apply] [1] payload 파싱: {}건 → {}건 성공 ({}ms)",
				pending.size(), parsed.size(), System.currentTimeMillis() - t1);

		if (parsed.isEmpty()) {
			return 0;
		}

		// [단계 2] bulk rid 중복 조회 (이미 rta_match 에 있는 rid)
		LinkedHashSet<Long> candidateRids = new LinkedHashSet<>();
		for (Map<String, ?> m : parsed) {
			Long rid = normalizeLong(m.get("rid"));
			if (rid != null) {
				candidateRids.add(rid);
			}
		}
		long t2 = System.currentTimeMillis();
		// 대량 rid: COPY + temp table JOIN — IN 청킹 다회 왕복보다 단일 왕복으로 빠름
		Set<Long> alreadyInReplay = bulkRidLookupService.selectExistingReplayIds(candidateRids);
		log.info("[rta-raw-apply] [2] bulk rid 중복조회: 후보 {}건 → 기존 {}건 ({}ms)",
				candidateRids.size(), alreadyInReplay.size(), System.currentTimeMillis() - t2);

		if (!alreadyInReplay.isEmpty()) {
			long t2m = System.currentTimeMillis();
			List<Long> toMarkApplied = new ArrayList<>(alreadyInReplay);
			Collections.sort(toMarkApplied);
			updateRankerRtpvpReplayRawAppliedOnTxConnection(toMarkApplied);
			applied += alreadyInReplay.size();
			log.info("[rta-raw-apply] [2] 기존 rid applied 마킹: {}건 ({}ms)",
					alreadyInReplay.size(), System.currentTimeMillis() - t2m);
		}

		// [단계 3] 신규 rid 정규화 청크 처리
		List<Map<String, ?>> toNormalize = new ArrayList<>();
		for (Map<String, ?> one : parsed) {
			Long rid = normalizeLong(one.get("rid"));
			if (rid != null && !alreadyInReplay.contains(rid)) {
				toNormalize.add(one);
			}
		}
		if (toNormalize.isEmpty()) {
			return applied;
		}

		log.info("[rta-raw-apply] [3] 정규화 대상: {}건, 청크 크기: {}", toNormalize.size(), chunk);
		int chunkIdx = 0;
		for (int from = 0; from < toNormalize.size(); from += chunk) {
			int to = Math.min(from + chunk, toNormalize.size());
			List<Map<String, ?>> sub = new ArrayList<>(toNormalize.subList(from, to));
			long tc = System.currentTimeMillis();
			chunkIdx++;
			try {
				Map<String, Integer> counts = applyArenaRtaUploadFromParsedItemsWithMode(sub, ArenaRtaPersistMode.NORMALIZED_ONLY);
				int ok = counts.getOrDefault("success", 0);
				applied += ok;
				log.info("[rta-raw-apply] [3] 청크 #{}: {}건 → ok={} ({}ms)",
						chunkIdx, sub.size(), ok, System.currentTimeMillis() - tc);
			} catch (RtaUploadValidationException e) {
				if (rtaRawApplyProperties.isFailFastOnError()) {
					log.error("[rta-raw-apply] [3] 청크 #{} 검증 실패 — fail-fast ({}ms)",
							chunkIdx, System.currentTimeMillis() - tc, e);
					throw new IllegalStateException("rta raw apply chunk validation: " + e.getMessage(), e);
				}
				log.warn("[rta-raw-apply] [3] 청크 #{} 검증 실패, 건별 재시도 ({}ms): {}",
						chunkIdx, System.currentTimeMillis() - tc, e.getMessage());
				applied += applyPendingArenaReplayRawFromDbOneByOne(sub);
			} catch (Exception e) {
				if (rtaRawApplyProperties.isFailFastOnError()) {
					log.error("[rta-raw-apply] [3] 청크 #{} 처리 실패 — fail-fast ({}ms)",
							chunkIdx, System.currentTimeMillis() - tc, e);
					throw new IllegalStateException("rta raw apply chunk failed: " + e.getMessage(), e);
				}
				log.warn("[rta-raw-apply] [3] 청크 #{} 처리 실패, 건별 재시도 ({}ms)",
						chunkIdx, System.currentTimeMillis() - tc, e);
				applied += applyPendingArenaReplayRawFromDbOneByOne(sub);
			}
		}
		return applied;
	}

	/**
	 * 청크 단위 정규화가 예외로 실패했을 때만 사용. 기존 rid 단건 로직과 동일.
	 */
	private int applyPendingArenaReplayRawFromDbOneByOne(List<Map<String, ?>> rows) {
		int applied = 0;
		for (Map<String, ?> one : rows) {
			Long rid = normalizeLong(one.get("rid"));
			if (rid == null) {
				continue;
			}
			try {
				if (selectArenaRidsExisting(Collections.singleton(rid)).contains(rid)) {
					updateRankerRtpvpReplayRawAppliedOnTxConnection(Collections.singletonList(rid));
					applied++;
					continue;
				}
				List<Map<String, ?>> logList = Collections.singletonList(one);
				Map<String, Integer> counts = applyArenaRtaUploadFromParsedItemsWithMode(logList, ArenaRtaPersistMode.NORMALIZED_ONLY);
				int ok = counts.getOrDefault("success", 0);
				if (ok > 0) {
					applied++;
				} else if (selectArenaRidsExisting(Collections.singleton(rid)).contains(rid)) {
					updateRankerRtpvpReplayRawAppliedOnTxConnection(Collections.singletonList(rid));
					applied++;
				} else {
					updateRankerRtpvpReplayRawFailedOnTxConnection(Collections.singletonList(rid),
							"정규화 스킵 또는 실패 (success=0)");
					if (rtaRawApplyProperties.isFailFastOnError()) {
						throw new IllegalStateException("rta raw apply rid=" + rid + " normalized success=0");
					}
				}
			} catch (RtaUploadValidationException e) {
				log.warn("[rta-raw-apply] 검증 실패 rid={}", rid, e);
				updateRankerRtpvpReplayRawFailedOnTxConnection(Collections.singletonList(rid), e.getMessage());
				if (rtaRawApplyProperties.isFailFastOnError()) {
					throw new IllegalStateException("rta raw apply rid=" + rid + " validation failed", e);
				}
			} catch (Exception e) {
				log.warn("[rta-raw-apply] 처리 실패 rid={}", rid, e);
				updateRankerRtpvpReplayRawFailedOnTxConnection(Collections.singletonList(rid), String.valueOf(e.getMessage()));
				if (rtaRawApplyProperties.isFailFastOnError()) {
					throw new IllegalStateException("rta raw apply rid=" + rid + " failed: " + e.getMessage(), e);
				}
			}
		}
		return applied;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseReplayPayloadToMap(Object payloadObj) throws JsonProcessingException {
		if (payloadObj instanceof String) {
			return objectMapper.readValue((String) payloadObj, new TypeReference<Map<String, Object>>() { });
		}
		if (payloadObj instanceof Map) {
			return (Map<String, Object>) payloadObj;
		}
		return objectMapper.convertValue(payloadObj, new TypeReference<Map<String, Object>>() { });
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
	@Transactional
	public int setDeckVote(Map<String, Object> param) {
		if (param == null) {
			return 0;
		}
		normalizeDeckVoteDefParams(param);
		normalizeDeckVoteAtkParams(param);

		String deckIdStr = deckIdToString(param.get("deck_id"));
		if (deckIdStr.isEmpty()) {
			Map<String, ?> resolved = swMapper.selectDeckDetail(param);
			if (resolved != null && !resolved.isEmpty() && resolved.get("deck_id") != null) {
				Object did = resolved.get("deck_id");
				deckIdStr = String.valueOf(did).trim();
				param.put("deck_id", did);
			}
		}

		Object v = param.get("vote");
		String vote = v != null ? String.valueOf(v).trim().toUpperCase() : "";
		if ("CLEAR".equals(vote) || vote.isEmpty()) {
			deleteExistingVotesForCombo(param, deckIdStr);
			return 1;
		}
		if (!"UP".equals(vote) && !"DOWN".equals(vote)) {
			throw new IllegalArgumentException("vote는 UP, DOWN, CLEAR만 허용됩니다.");
		}
		param.put("vote_type", vote);

		if (!deckIdStr.isEmpty()) {
			validateDeckVoteAgainstRow(param);
			deleteExistingVotesForCombo(param, deckIdStr);
			return swMapper.insertDeckVoteRegistered(param);
		}
		validateFreeVoteAtk(param);
		deleteExistingVotesForCombo(param, "");
		return swMapper.insertDeckVoteFree(param);
	}

	/** 등록 행 + 동일 조합 자유 투표 행(있으면) 제거 후 재삽입용 */
	private void deleteExistingVotesForCombo(Map<String, Object> param, String deckIdStr) {
		Object a1 = param.get("atk_monster_1");
		boolean hasAtk = a1 != null && !String.valueOf(a1).trim().isEmpty();
		if (deckIdStr != null && !deckIdStr.isEmpty()) {
			Map<String, Object> p = new HashMap<>(param);
			p.put("deck_id", deckIdStr);
			swMapper.deleteDeckVote(p);
		}
		if (hasAtk) {
			Map<String, Object> p = new HashMap<>(param);
			p.remove("deck_id");
			swMapper.deleteDeckVote(p);
		}
	}

	private static String deckIdToString(Object o) {
		if (o == null) {
			return "";
		}
		String s = String.valueOf(o).trim();
		if (s.isEmpty() || "0".equals(s)) {
			return "";
		}
		return s;
	}

	private void validateFreeVoteAtk(Map<String, Object> param) {
		String a1 = String.valueOf(param.get("atk_monster_1") != null ? param.get("atk_monster_1") : "").trim();
		String a2 = String.valueOf(param.get("atk_monster_2") != null ? param.get("atk_monster_2") : "").trim();
		String a3 = String.valueOf(param.get("atk_monster_3") != null ? param.get("atk_monster_3") : "").trim();
		if (a1.isEmpty() || a2.isEmpty() || a3.isEmpty()) {
			throw new IllegalArgumentException("atk_monster_1, atk_monster_2, atk_monster_3가 필요합니다.");
		}
	}

	/** 요청 JSON camelCase → MyBatis용 snake_case */
	private void normalizeDeckVoteDefParams(Map<String, Object> param) {
		if (param.get("def_monster_1") == null && param.get("defMonster1") != null) {
			param.put("def_monster_1", param.get("defMonster1"));
		}
		if (param.get("def_monster_2") == null && param.get("defMonster2") != null) {
			param.put("def_monster_2", param.get("defMonster2"));
		}
		if (param.get("def_monster_3") == null && param.get("defMonster3") != null) {
			param.put("def_monster_3", param.get("defMonster3"));
		}
	}

	private void normalizeDeckVoteAtkParams(Map<String, Object> param) {
		if (param.get("atk_monster_1") == null && param.get("atkMonster1") != null) {
			param.put("atk_monster_1", param.get("atkMonster1"));
		}
		if (param.get("atk_monster_2") == null && param.get("atkMonster2") != null) {
			param.put("atk_monster_2", param.get("atkMonster2"));
		}
		if (param.get("atk_monster_3") == null && param.get("atkMonster3") != null) {
			param.put("atk_monster_3", param.get("atkMonster3"));
		}
	}

	/** deck_id 행의 방덱(def)과 요청 방덱이 일치하는지 검증 (특정 방덱의 특정 공덱만 투표) */
	private void validateDeckVoteAgainstRow(Map<String, Object> param) {
		Map<String, Object> q = new HashMap<>();
		q.put("deck_id", param.get("deck_id"));
		Map<String, ?> deck = swMapper.selectDeckDetail(q);
		if (deck == null || deck.isEmpty()) {
			throw new IllegalArgumentException("공덱을 찾을 수 없습니다.");
		}
		String e1 = mapStr(deck, "def_monster_1", "defMonster1");
		String e2 = mapStr(deck, "def_monster_2", "defMonster2");
		String e3 = mapStr(deck, "def_monster_3", "defMonster3");
		String p1 = String.valueOf(param.get("def_monster_1") != null ? param.get("def_monster_1") : "");
		String p2 = String.valueOf(param.get("def_monster_2") != null ? param.get("def_monster_2") : "");
		String p3 = String.valueOf(param.get("def_monster_3") != null ? param.get("def_monster_3") : "");
		if (!e1.equals(p1) || !e2.equals(p2) || !e3.equals(p3)) {
			throw new IllegalArgumentException("방덱(수비) 정보가 해당 공덱과 일치하지 않습니다.");
		}
	}

	private static String mapStr(Map<String, ?> m, String snake, String camel) {
		Object v = m.get(snake);
		if (v == null) {
			v = m.get(camel);
		}
		return v != null ? String.valueOf(v) : "";
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
	@Cacheable(cacheNames = "monsterInfo", cacheManager = "monsterInfoCacheManager", key = "#monsterId")
	public Map<String, ?> selectMonsterInfo(String monsterId) {
		// 몬스터 기본 정보 조회 (family_id, un_name, skill_group_id 추출을 위해 선행 필요)
		Map<String, ?> monsterInfo = swMapper.selectMonsterInfo(monsterId);
		if (monsterInfo == null || monsterInfo.isEmpty()) {
			return new HashMap<>();
		}

		// 기본 정보에서 패밀리/스킬그룹 키 추출
		Object fidObj = monsterInfo.get("family_id");
		Long familyId = null;
		if (fidObj instanceof Number) {
			familyId = ((Number) fidObj).longValue();
		} else if (fidObj != null) {
			try { familyId = Long.parseLong(fidObj.toString().trim()); } catch (NumberFormatException ignored) {}
		}
		Object unObj = monsterInfo.get("un_name");
		final String unName = (unObj != null && !unObj.toString().trim().isEmpty()) ? unObj.toString().trim() : null;
		final String evoGroupKey = MonsterIdEvolutionUtil.evolutionGroupKey(monsterId);
		Object sgObj = monsterInfo.get("skill_group_id");
		long skillGroupId = 0L;
		if (sgObj instanceof Number) {
			skillGroupId = ((Number) sgObj).longValue();
		} else if (sgObj != null) {
			try { skillGroupId = Long.parseLong(sgObj.toString().trim()); } catch (NumberFormatException ignored) {}
		}
		final Long finalFamilyId = familyId;
		final long finalSkillGroupId = skillGroupId;

		// 독립적인 6개 쿼리 병렬 실행
		CompletableFuture<List<Map<String, ?>>> skillsFuture =
			CompletableFuture.supplyAsync(() -> dedupeMonsterSkillsBySkillId(swMapper.selectMonsterSkills(monsterId)));
		CompletableFuture<List<Map<String, ?>>> effectsFuture =
			CompletableFuture.supplyAsync(() -> swMapper.selectMonsterSkillEffects(monsterId));
		CompletableFuture<List<Map<String, ?>>> familyFuture =
			CompletableFuture.supplyAsync(() -> (finalFamilyId != null && finalFamilyId > 0)
				? swMapper.selectMonstersByFamilyId(finalFamilyId) : Collections.emptyList());
		CompletableFuture<List<Map<String, ?>>> unNameFuture =
			CompletableFuture.supplyAsync(() -> (unName != null)
				? swMapper.selectMonstersByUnName(unName) : Collections.emptyList());
		CompletableFuture<List<Map<String, ?>>> evoFuture =
			CompletableFuture.supplyAsync(() -> (evoGroupKey != null && !evoGroupKey.isEmpty() && !evoGroupKey.startsWith("solo:"))
				? swMapper.selectMonstersByEvolutionGroupKey(evoGroupKey) : Collections.emptyList());
		CompletableFuture<List<Map<String, ?>>> skillGroupFuture =
			CompletableFuture.supplyAsync(() -> (finalSkillGroupId > 0)
				? swMapper.selectMonstersBySkillGroupId(finalSkillGroupId) : Collections.emptyList());

		CompletableFuture.allOf(skillsFuture, effectsFuture, familyFuture, unNameFuture, evoFuture, skillGroupFuture).join();

		List<Map<String, ?>> skills = skillsFuture.join();
		List<Map<String, ?>> skillEffectRows = effectsFuture.join();

		// 스킬 + 이펙트 조립
		Map<Object, List<Map<String, ?>>> effectsBySkillId = new LinkedHashMap<>();
		if (skillEffectRows != null) {
			for (Map<String, ?> row : skillEffectRows) {
				Object skid = row.get("skill_id");
				if (skid == null) continue;
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

		Map<String, Object> result = new HashMap<>(monsterInfo);
		result.put("skills", skillsWithEffects);

		// 패밀리/진화/스킬그룹 병합 → detail_context 구성
		List<Map<String, ?>> familyRows = new ArrayList<>();
		Set<String> seenMonsterIds = new LinkedHashSet<>();
		mergeMonsterRows(familyRows, familyFuture.join(), seenMonsterIds);
		mergeMonsterRows(familyRows, unNameFuture.join(), seenMonsterIds);
		mergeMonsterRows(familyRows, evoFuture.join(), seenMonsterIds);
		mergeMonsterRows(familyRows, skillGroupFuture.join(), seenMonsterIds);

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

	/**
	 * DB 의 {@code rta_season} 전체를 짧은 TTL 로 캐시한 뒤, 기존 INSERT 서브쿼리와 동일 규칙으로 {@code season_id} 를 산출한다.
	 */
	private RtaSeasonMappingCache getRtaSeasonMappingCache() {
		long now = System.currentTimeMillis();
		RtaSeasonMappingCache c = rtaSeasonMappingCache;
		if (c != null && (now - rtaSeasonMappingCacheLoadedAtMs) < RTA_SEASON_CACHE_TTL_MS) {
			return c;
		}
		synchronized (rtaSeasonMappingCacheLock) {
			now = System.currentTimeMillis();
			if (rtaSeasonMappingCache != null && (now - rtaSeasonMappingCacheLoadedAtMs) < RTA_SEASON_CACHE_TTL_MS) {
				return rtaSeasonMappingCache;
			}
			List<Map<String, ?>> dbRows = swMapper.selectRtaSeasonsForRtaMatchMapping();
			if (dbRows == null || dbRows.isEmpty()) {
				throw new IllegalStateException("rta_season 테이블에 시즌 행이 없습니다.");
			}
			RtaSeasonMappingCache loaded = RtaSeasonMappingCache.fromRows(dbRows);
			rtaSeasonMappingCache = loaded;
			rtaSeasonMappingCacheLoadedAtMs = System.currentTimeMillis();
			log.debug("[rta-upload] rta_season 매핑 캐시 갱신: rows={}, fallbackSeasonId={}", dbRows.size(),
					loaded.getFallbackSeasonId());
			return loaded;
		}
	}

	/** {@code date_add}(played_at) 기준 — INSERT 직전에 호출(이미 {@link #normalizeArenaReplayDateAdd} 된 행) */
	private void applyRtaSeasonIdsToArenaRows(List<Map<String, ?>> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		RtaSeasonMappingCache cache = getRtaSeasonMappingCache();
		for (Map<String, ?> row : rows) {
			if (!(row instanceof Map)) {
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>) row;
			Object da = m.get("date_add");
			Timestamp ts = da instanceof Timestamp ? (Timestamp) da : null;
			int sid = cache.resolveSeasonId(ts);
			m.put("season_id", sid);
		}
	}

	private static final class RtaSeasonMappingCache {
		private final int fallbackSeasonId;
		private final List<SeasonSlice> slices;

		private RtaSeasonMappingCache(int fallbackSeasonId, List<SeasonSlice> slices) {
			this.fallbackSeasonId = fallbackSeasonId;
			this.slices = slices;
		}

		int getFallbackSeasonId() {
			return fallbackSeasonId;
		}

		static RtaSeasonMappingCache fromRows(List<Map<String, ?>> dbRows) {
			Integer fb = null;
			List<SeasonSlice> list = new ArrayList<>(dbRows.size());
			for (Map<String, ?> raw : dbRows) {
				if (raw == null) {
					continue;
				}
				Integer sid = parseIntFlexible(raw.get("season_id"));
				if (sid == null) {
					continue;
				}
				String code = raw.get("season_code") != null ? String.valueOf(raw.get("season_code")) : null;
				if ("_FALLBACK".equals(code)) {
					fb = sid;
				}
				Integer so = parseIntFlexible(raw.get("sort_order"));
				Integer sn = parseIntFlexible(raw.get("season_no"));
				Timestamp sta = toTimestampFlexible(raw.get("start_at"));
				Timestamp ena = toTimestampFlexible(raw.get("end_at"));
				list.add(new SeasonSlice(sid, so, sn, sta, ena));
			}
			if (list.isEmpty()) {
				throw new IllegalStateException("rta_season 파싱 결과가 비어 있습니다.");
			}
			int fbFinal = fb != null ? fb : list.get(0).seasonId;
			return new RtaSeasonMappingCache(fbFinal, list);
		}

		int resolveSeasonId(Timestamp playedAt) {
			if (playedAt == null) {
				return fallbackSeasonId;
			}
			final long t = playedAt.getTime();
			List<SeasonSlice> hit = new ArrayList<>();
			for (SeasonSlice s : slices) {
				if (s.startAt == null || s.endAt == null) {
					continue;
				}
				if (s.startAt.getTime() <= t && t < s.endAt.getTime()) {
					hit.add(s);
				}
			}
			if (hit.isEmpty()) {
				return fallbackSeasonId;
			}
			hit.sort(Comparator
					.comparing((SeasonSlice s) -> s.sortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
					.thenComparing((SeasonSlice s) -> s.seasonNo, Comparator.nullsLast(Comparator.reverseOrder())));
			return hit.get(0).seasonId;
		}
	}

	private static final class SeasonSlice {
		final int seasonId;
		final Integer sortOrder;
		final Integer seasonNo;
		final Timestamp startAt;
		final Timestamp endAt;

		SeasonSlice(int seasonId, Integer sortOrder, Integer seasonNo, Timestamp startAt, Timestamp endAt) {
			this.seasonId = seasonId;
			this.sortOrder = sortOrder;
			this.seasonNo = seasonNo;
			this.startAt = startAt;
			this.endAt = endAt;
		}
	}

	private static Integer parseIntFlexible(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Number) {
			return ((Number) o).intValue();
		}
		try {
			return Integer.parseInt(o.toString().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Timestamp toTimestampFlexible(Object o) {
		if (o == null) {
			return null;
		}
		if (o instanceof Timestamp) {
			return (Timestamp) o;
		}
		if (o instanceof java.util.Date) {
			return new Timestamp(((java.util.Date) o).getTime());
		}
		return null;
	}
}
