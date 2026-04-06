package com.smw.monster.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	/** match_id 기준 기존 전투 로그 키 일괄 조회 */
	List<Map<String, ?>> selectBattleLogKeysForMatch(String matchId);
	
	public int insertGuildSiegeBattleLog(Map<String, ?> param);
	
	public int insertGuildSiegeBattleDeck(Map<String, ?> param);

	/** battle_log_list 다건 INSERT */
	int insertGuildSiegeBattleLogBatch(List<Map<String, ?>> rows);

	/** view_battle_deck_info 다건 INSERT */
	int insertGuildSiegeBattleDeckBatch(List<Map<String, String>> rows);
	
	/** DB에 이미 존하는 rid 집합 (IN 절 분할 조회) */
	Set<Long> selectArenaRidsExisting(Collection<Long> rids);

	/** 이미 저장된 (rid, wizard_id) PK 문자열 rid|wizard_id (user_list 단독 잔존 등 불일치 대비) */
	Set<String> selectArenaUserPkKeysExisting(Collection<Long> rids);

	/**
	 * 업로드 중단 등으로 replay_list 없이 user/pick/unit 만 남은 rid 정리.
	 * 재업로드 시 고아 PK가 ‘이미 존재’로 잡혀 replay 가 안 들어가는 현상 방지.
	 */
	int deleteArenaRtaOrphanChildrenByRids(Collection<Long> rids);

	/**
	 * 부모 {@code replay_list} 행이 없는 user/pick/unit 고아 행 전수 삭제. 배치({@link com.smw.monster.batch.RtaUnifiedPipelineAggJob})에서 호출.
	 */
	int deleteArenaRtaOrphanChildrenGlobal();
	
	public int insertArenaInfo(Map<String, ?> param);
	
	public int insertArenaUserInfo(Map<String, ?> param);
	
	public int insertArenaPickInfo(Map<String, ?> param);
	
	public int insertArenaUnitInfo(Map<String, ?> param);

	/** rta-upload: 레거시 — 단건 INSERT를 최대 200건 단위로 반복 */
	int insertArenaInfoBatch(List<Map<String, ?>> rows);

	int insertArenaUserInfoBatch(List<Map<String, ?>> rows);

	int insertArenaPickInfoBatch(List<Map<String, ?>> rows);

	int insertArenaUnitInfoBatch(List<Map<String, ?>> rows);

	/**
	 * rta-upload: 고아 삭제(짧은 트랜잭션)·필터 후, replay/user/pick/unit 을 VALUES 벌크 INSERT(한 트랜잭션).
	 */
	ArenaRtaUploadApplyResult applyArenaRtaUploadPersistence(
			List<Map<String, ?>> arenaBatch,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch);

	/**
	 * {@link ArenaRtaPersistMode} 에 따라 raw / 정규화 적재 범위를 나눈다.
	 */
	ArenaRtaUploadApplyResult applyArenaRtaUploadPersistence(
			List<Map<String, ?>> arenaBatch,
			List<Map<String, ?>> userBatch,
			List<Map<String, ?>> pickBatch,
			List<Map<String, ?>> unitBatch,
			ArenaRtaPersistMode persistMode);

	/**
	 * rta-upload API 및 로컬 Exporter full_log 수집과 동일한 검증·적재.
	 * @return success / fail 건수
	 */
	Map<String, Integer> applyArenaRtaUploadFromParsedItems(List<Map<String, ?>> log_list);

	/**
	 * Exporter {@code watch-directory} 전용: 검증 후 {@code ranker_rtpvp_replay_raw} 만 적재(pending).
	 * 정규화는 {@link com.smw.monster.batch.RtaUnifiedPipelineAggJob}(bat_id 10001)가 수행한다.
	 */
	Map<String, Integer> applyArenaRtaUploadRawOnlyFromParsedItems(List<Map<String, ?>> log_list);

	/**
	 * NDJSON/API 청크용: 정규화 테이블만 적재 (기존 rid 는 ON CONFLICT 로 스킵).
	 * Spring Batch Step1 에서 chunk 단위로 호출한다.
	 */
	Map<String, Integer> applyArenaRtaNormalizedChunk(List<Map<String, ?>> log_list);

	/**
	 * {@code ranker_rtpvp_replay_raw} 중 미적용 건을 정규화 테이블로 반영하고 applied 로 표시한다.
	 * @return 이번 호출에서 정규화에 성공한 rid 수
	 */
	int applyPendingArenaReplayRawFromDb();

	public List<Map<String, ?>> selectRecordList(Map<String, Object> param);

	public List<Map<String, ?>> selectRecordUserDetail(Map<String, Object> param);
	
	public List<Map<String, ?>> selectGuildSiegeHistorySimple(Map<String, Object> param);
	
	public int selectGuildSiegeHistoryCount(Map<String, Object> param);
	
	public Map<String, ?> selectDeckDetail(Map<String, Object> param);
	
	public int deleteDeckDetail(Map<String, Object> param);

	/**
	 * 공덱 추천/비추천. vote: UP, DOWN, CLEAR(또는 빈값) — 사용자당 deck_id당 1행
	 */
	public int setDeckVote(Map<String, Object> param);

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
