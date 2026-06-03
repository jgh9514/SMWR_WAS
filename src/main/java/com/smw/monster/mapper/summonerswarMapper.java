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

	/** match_id 기준 기존 전투 로그 키 일괄 조회 (중복 검사 N회 → 1회) */
	List<Map<String, ?>> selectBattleLogKeysForMatch(@Param("match_id") String matchId);
	
	public int insertGuildSiegeBattleLog(Map<String, ?> param);
	
	public int insertGuildSiegeBattleDeck(Map<String, ?> param);

	/** battle_log_list 다건 INSERT (점령전 업로드 성능) */
	int insertGuildSiegeBattleLogBatch(@Param("rows") List<Map<String, ?>> rows);

	/** view_battle_deck_info 다건 INSERT */
	int insertGuildSiegeBattleDeckBatch(@Param("rows") List<Map<String, String>> rows);

	/** 길드·시즌(YYYYMM) 단위 방덱 집계 삭제 후 battle_log_list 기준 재적재 */
	int deleteSiegeDefenseDeckStatsByGuildSeason(Map<String, Object> param);

	int insertSiegeDefenseDeckStatsFromBattleLogs(Map<String, Object> param);
	
	/** RTA 시즌 구간 매핑용 전체 행 (행마다 INSERT 서브쿼리 대비) */
	List<Map<String, ?>> selectRtaSeasonsForRtaMatchMapping();

	/** rta-upload 벌크: VALUES 다중 행 + ON CONFLICT DO NOTHING */
	int insertArenaInfoBulk(@Param("rows") List<Map<String, ?>> rows);

	int insertArenaUserInfoBulk(@Param("rows") List<Map<String, ?>> rows);

	int insertArenaUnitInfoBulk(@Param("rows") List<Map<String, ?>> rows);

	/** rta-upload: 메타 벌크 적재 (rid). ON CONFLICT 시 apply_status='pending' 으로 리셋 */
	int insertArenaReplayRawBulk(@Param("rows") List<Map<String, Object>> rows);

	/** rta-upload: 페이로드 벌크 적재 (rid + payload). ON CONFLICT 시 payload 갱신 */
	int insertArenaReplayRawPayloadBulk(@Param("rows") List<Map<String, Object>> rows);

	/** RTA raw: pending/failed 조회 (rid 오름차순, 한 실행당 {@code limit}건 상한). */
	List<Map<String, ?>> selectRtaReplayRawPending(@Param("limit") int limit);

	Long countRtaReplayRawPending();

	/** rta_match 부모 없는 고아 행 전수 삭제 (배치용, unit → user 순) */
	int deleteArenaRtaOrphanUnitsGlobal();

	int deleteArenaRtaOrphanUsersGlobal();

	public List<Map<String, ?>> selectRecordList(Map<String, Object> param);

	public List<Map<String, ?>> selectRecordUserDetail(Map<String, Object> param);

	/** 세션 길드 전투 로그에 대상 wizard_id 존재 여부 (record-detail 접근 검증) */
	boolean existsRecordWizardInGuild(Map<String, Object> param);
	
	public List<Map<String, ?>> selectGuildSiegeHistorySimple(Map<String, Object> param);
	
	public int selectGuildSiegeHistoryCount(Map<String, Object> param);
	
	public String getOriginalMonsterId(String monsterId);
	
	public List<String> getAllRelatedMonsterIds(String monsterId);
	
	public Map<String, ?> selectDeckDetail(Map<String, Object> param);
	
	public int deleteDeckDetail(Map<String, Object> param);

	/** 등록된 공덱(deck_id) 투표 행 삽입 */
	public int insertDeckVoteRegistered(Map<String, Object> param);

	/** 추천 공덱 미등록 조합 투표(deck_id NULL, atk만) */
	public int insertDeckVoteFree(Map<String, Object> param);

	/** 공덱 추천/비추천 삭제(취소) — deck_id 있으면 등록 행, 없으면 atk로 자유 투표 행 */
	public int deleteDeckVote(Map<String, Object> param);
	
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
