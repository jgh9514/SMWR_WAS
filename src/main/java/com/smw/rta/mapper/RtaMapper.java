package com.smw.rta.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.smw.rta.model.RtaCounterMatchupUpsertRow;
import com.smw.rta.model.RtaSynergyAggUpsertRow;

@Mapper
public interface RtaMapper {

    List<Map<String, Object>> getRtaMatches(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("ratingId") Integer ratingId,
            @Param("ratingIds") List<Integer> ratingIds);

    List<Map<String, Object>> getPlayerRtaMatches(@Param("wizardId") String wizardId, @Param("limit") int limit,
            @Param("offset") int offset, @Param("seasonId") Long seasonId);

    List<Map<String, Object>> getPlayerVsOpponentMatches(@Param("wizardId") String wizardId,
            @Param("opponentWizardId") String opponentWizardId, @Param("limit") int limit,
            @Param("offset") int offset, @Param("seasonId") Long seasonId);

    /** 특정 몬스터가 픽·밴된 최근 N경기 */
    List<Map<String, Object>> getMonsterRecentMatches(@Param("monsterId") int monsterId,
            @Param("seasonId") Long seasonId, @Param("limit") int limit,
            @Param("ratingId") Long ratingId, @Param("ratingIds") List<Long> ratingIds);

    /**
     * 시즌 전체 H2H 읽기 — {@code rta_agg_summoner_opponent_h2h_snap} (배치 적재, 라이브 집계 없음).
     */
    List<Map<String, Object>> listRtaPlayerOpponentHeadToHead(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId, @Param("limit") int limit, @Param("offset") int offset);

    int getTodayRtaMatches(@Param("seasonId") Long seasonId, @Param("ratingId") Integer ratingId,
            @Param("ratingIds") List<Integer> ratingIds);

    int getWeeklyRtaMatches(@Param("seasonId") Long seasonId, @Param("ratingId") Integer ratingId,
            @Param("ratingIds") List<Integer> ratingIds);

    /** (호환) 예전 빌드에서 참조할 수 있음 — 현재 서비스 경로에서는 미사용 가능 */
    List<Map<String, Object>> selectMonsterPortraitMetaByIds(@Param("ids") List<String> ids);

    Map<String, Object> testRtaData();

    List<Map<String, Object>> debugRtaData();

    List<Map<String, Object>> debugMatchDetail(@Param("rid") String rid);

    /** 시즌별 {@code rta_agg_tier_daily} 전량 삭제 (배치 재적재 전). */
    int deleteRtaAggTierDailyForSeason(@Param("seasonId") long seasonId);

    /** 시즌별 일자×티어 집계 INSERT (배치 전용). {@code playedBeforeExclusive} 로 participant 상한. */
    int insertRtaTierAggDailyForSeason(@Param("seasonId") long seasonId,
            @Param("playedBeforeExclusive") java.time.Instant playedBeforeExclusive);

    /** 시즌별 {@code rta_agg_tier_daily} 적재 행 수 — JDBC updateCount 대신 로그·검증용 */
    long countRtaAggTierDailyForSeason(@Param("seasonId") long seasonId);

    /** 시즌 내 {@code rta_match.played_at} 최대값 — 티어 일별 집계 상한 계산용 */
    java.time.Instant selectMaxRtaMatchPlayedAt(@Param("seasonId") long seasonId);

    /** 시즌 총 매치 수 (집계 메타) */
    Long getRtaMonsterStatsTotalFromAgg(@Param("seasonId") Long seasonId);

    /**
     * RTA 몬스터별 통계 — {@code rta_agg_synergy_solo}.
     * {@code ratingId} 단일 / {@code ratingIds} 복수 중 하나만 사용(단일 우선). 둘 다 없으면 전체 티어 합산.
     */
    List<Map<String, Object>> getRtaMonsterStatsFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("ratingId") Integer ratingId,
            @Param("ratingIds") List<Integer> ratingIds, @Param("minPickCount") int minPickCount);

    /** 2마리 조합 — {@code rta_agg_synergy_duo} */
    List<Map<String, Object>> getRtaDuoComboStatsFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("ratingId") Integer ratingId,
            @Param("ratingIds") List<Integer> ratingIds, @Param("minPickCount") int minPickCount);

    /** 3마리 조합 — {@code rta_agg_synergy_trio} */
    List<Map<String, Object>> getRtaTrioComboStatsFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("ratingId") Integer ratingId,
            @Param("ratingIds") List<Integer> ratingIds, @Param("minPickCount") int minPickCount);

    /**
     * 시즌 전체 합산 상위 100 스냅 — {@code rta_agg_monster_stats_tier_top_snap}(티어 컬럼 없음).
     */
    List<Map<String, Object>> getRtaMonsterStatsFromTierTopSnap(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("minPickCount") int minPickCount);

    List<Map<String, Object>> getRtaDuoComboStatsFromTierTopSnap(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("minPickCount") int minPickCount);

    List<Map<String, Object>> getRtaTrioComboStatsFromTierTopSnap(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("minPickCount") int minPickCount);

    /** 대시보드 프리뷰 전용: 솔로 전체 티어 TOP N — {@code rta_agg_monster_stats_tier_top_snap} */
    List<Map<String, Object>> getDashboardPreviewSoloFromSnap(@Param("limit") int limit,
            @Param("seasonId") Long seasonId, @Param("minPickCount") int minPickCount);

    /** 대시보드 프리뷰 전용: 듀오 전체 티어 TOP N — {@code rta_agg_monster_stats_tier_top_snap} */
    List<Map<String, Object>> getDashboardPreviewDuoFromSnap(@Param("limit") int limit,
            @Param("seasonId") Long seasonId, @Param("minPickCount") int minPickCount);

    /** 대시보드 프리뷰 전용: 트리오 전체 티어 TOP N — {@code rta_agg_monster_stats_tier_top_snap} */
    List<Map<String, Object>> getDashboardPreviewTrioFromSnap(@Param("limit") int limit,
            @Param("seasonId") Long seasonId, @Param("minPickCount") int minPickCount);

    /**
     * RTA 몬스터 기본 정보 조회
     */
    Map<String, Object> getRtaMonsterBasicInfo(@Param("monsterId") int monsterId, @Param("seasonId") Long seasonId);

    /** RTA 몬스터 강한 상대 조회 */
    List<Map<String, Object>> getRtaMonsterStrongAgainst(@Param("monsterId") int monsterId,
            @Param("seasonId") Long seasonId);

    /** RTA 몬스터 좋은 콤비 조회 */
    List<Map<String, Object>> getRtaMonsterGoodCombos(@Param("monsterId") int monsterId,
            @Param("seasonId") Long seasonId);

    /** RTA 몬스터 좋은 3체인 콤비 조회 */
    List<Map<String, Object>> getRtaMonsterGoodTripleCombos(@Param("monsterId") int monsterId,
            @Param("seasonId") Long seasonId);

    /** RTA 몬스터 최근 경기 조회 */
    List<Map<String, Object>> getRtaMonsterRecentMatches(@Param("monsterId") int monsterId,
            @Param("seasonId") Long seasonId);

    /**
     * 일자×티어별 누적 인원 (대시보드). {@code rta_agg_tier_daily} 배치 적재본.
     */
    List<Map<String, Object>> getRtaTierDistributionDaily(@Param("seasonId") Long seasonId);

    /** 현재 시즌×티어별 총 경기 수 upsert 재집계 ({@code rta_agg_season_rating_match_total}). */
    int rebuildRtaSeasonRatingMatchTotal(@Param("seasonId") long seasonId);

    /** participant 최신 played_at 이 match_total 집계 시각보다 이후이면 true(재집계 필요). */
    boolean existsParticipantPlayedAfterMatchTotalComputed(@Param("seasonId") long seasonId);

    /** {@code rta_agg_season_rating_match_total} 전체 행 수 (배치 적재 후 로깅) */
    Long countRtaSeasonRatingMatchTotalRows();

    List<Map<String, Object>> selectSeasonRatingMatchTotals(@Param("seasonId") long seasonId);

    List<Map<String, Object>> selectPreviousRankCutSnapsByGrade(
            @Param("seasonId") long seasonId,
            @Param("beforeHour") java.time.Instant beforeHour);

    /** 배치 전용: 세션 lock_timeout 해제 (인덱스 락 충돌 방지) */
    @org.apache.ibatis.annotations.Update("SET lock_timeout = 0")
    void disableLockTimeout();

    /** 배치 전용: 현재 TX 범위에서만 lock_timeout 해제 — TX 종료(commit/rollback) 시 자동 복원 */
    @org.apache.ibatis.annotations.Update("SET LOCAL lock_timeout = 0")
    void disableLocalLockTimeout();

    /** 배치 전용: 현재 TX에서 idle_in_transaction_session_timeout 해제(기본 300s 초과 방지). */
    @org.apache.ibatis.annotations.Update("SET LOCAL idle_in_transaction_session_timeout = 0")
    void disableLocalIdleInTransactionTimeout();

    /** 배치 전용: 장시간 INSERT/CTE — 세션 statement_timeout 이 짧을 때 중단 방지. */
    @org.apache.ibatis.annotations.Update("SET LOCAL statement_timeout = 0")
    void disableLocalStatementTimeout();

    /** 시즌의 누락된 랭크컷 스냅 시간대 목록 조회 (limit: 1회 처리 최대 개수) */
    List<java.time.Instant> selectMissingRankCutSnapHours(@Param("seasonId") long seasonId,
                                                          @Param("limit") int limit);

    /** 특정 시간대에 rta_match 경기가 존재하는지 확인 (0이면 스킵) */
    long countRtaMatchForHour(@Param("seasonId") long seasonId,
                              @Param("snapHour") java.time.Instant snapHour);

    /** threshold 이후(포함) played_at 를 가진 rta_match 가 1건 이상 있으면 true — 레거시·기타 Job용 */
    boolean existsRtaMatchAfter(@Param("threshold") java.time.Instant threshold);


    /** 시간대별 랭크컷 계산 결과 조회 */
    List<com.smw.rta.model.RtaRankCutSnapRow> selectRankCutSnapsForHour(
            @Param("seasonId") long seasonId,
            @Param("snapHour") java.time.Instant snapHour);

    /** 랭크컷 스냅 batch INSERT */
    int insertRtaRankCutHourlySnaps(
            @Param("seasonId") long seasonId,
            @Param("snapHour") java.time.Instant snapHour,
            @Param("rows") List<com.smw.rta.model.RtaRankCutSnapRow> rows);

    /** 92일 초과 시간별 랭크 컷 스냅 정리 */
    int pruneRtaRankCutHourlySnap();

    /** 대시보드용: 현재·3h·6h·12h·3d·7d 시점 랭크 컷 */
    List<Map<String, Object>> getRtaRankCutHourlyForDashboard(@Param("seasonId") Long seasonId);

    int getRtaTop1Score(@Param("seasonId") long seasonId);

    /** 상세 페이지용: 시즌 전체 일별 랭크 컷 히스토리 */
    List<Map<String, Object>> getRtaRankCutHourlyDaily(@Param("seasonId") Long seasonId);

    /**
     * 랭킹 스냅 쓰기 직렬화(트랜잭션 범위). {@link RtaBatchAggregationService#rebuildSummonerRankingAgg} 에서
     * 시즌별 delete+insert 와 동일 TX 안에서만 호출.
     */
    Integer acquireRtaSummonerSnapSeasonXactLock(@Param("seasonId") long seasonId);

    /** 시즌별 상위 500명 스냅샷 전체 삭제 (insert 직전, 동일 TX 권장) */
    int deleteRtaSummonerRankingSnapBySeason(@Param("seasonId") long seasonId);

    /** 시즌별 상위 500명 스냅샷 적재 (직전에 같은 시즌 delete 가 있어야 함) */
    int insertRtaSummonerRankingSnapForSeason(@Param("seasonId") long seasonId);

    /**
     * 검색 스냅 전역 쓰기 직렬화(트랜잭션 범위). {@link com.smw.rta.service.RtaBatchAggregationService#rebuildSummonerRankingAgg} 에서
     * upsert 와 동일 TX 안에서만 호출.
     */
    Integer acquireRtaSummonerSearchSnapGlobalXactLock();

    /** 현재 시즌 participant 기준 wizard_id 당 대표 1행을 upsert(닉·국가 동일이면 UPDATE 생략) */
    int upsertRtaSummonerSearchSnap(@Param("seasonId") long seasonId);

    /** {@code rta_agg_monster_stats_tier_top_snap} 시즌 전체 삭제 후 솔/듀/트 상위 100 재적재 */
    int deleteRtaMonsterStatsTierTopSnapBySeason(@Param("seasonId") long seasonId);

    /** 락 충돌 진단: rta_agg_monster_stats_tier_top_snap 에 락을 보유한 세션 목록 */
    @Select("""
            SELECT l.pid, l.mode, l.granted, a.state, a.wait_event_type, a.wait_event,
                   now() - a.xact_start AS xact_duration,
                   left(a.query, 200) AS query
              FROM pg_locks l
              JOIN pg_stat_activity a ON a.pid = l.pid
             WHERE l.relation = 'rta_agg_monster_stats_tier_top_snap'::regclass
             ORDER BY l.granted DESC, a.xact_start
            """)
    List<Map<String, Object>> selectTierTopSnapLockDiagnostics();

    int insertRtaMonsterStatsTierTopSoloSnapForSeason(
            @Param("seasonId") long seasonId, @Param("minPickCount") int minPickCount);

    int insertRtaMonsterStatsTierTopDuoSnapForSeason(
            @Param("seasonId") long seasonId, @Param("minPickCount") int minPickCount);

    int insertRtaMonsterStatsTierTopTrioSnapForSeason(
            @Param("seasonId") long seasonId, @Param("minPickCount") int minPickCount);

    /** 전체 랭킹 스냅샷 행 수 (배치 적재 후 로깅) */
    Long countRtaSummonerRankingSnapRows();

    /** 전체 검색 스냅샷 행 수 (배치 적재 후 로깅) */
    Long countRtaSummonerSearchSnapRows();

    /** 시즌 단위: 소환사 전투 스냅(분모) 삭제 */
    int deleteRtaSummonerSeasonFightSnapBySeason(@Param("seasonId") long seasonId);

    /** 시즌 단위: 소환사 픽턴(snake)·선후 라인 스냅 삭제 */
    int deleteRtaSummonerPickTurnSnapBySeason(@Param("seasonId") long seasonId);

    /** 시즌 단위: 소환사×몬스터 스냅 삭제 */
    int deleteRtaSummonerMonsterSnapBySeason(@Param("seasonId") long seasonId);

    /** 시즌 단위: 소환사×상대 H2H 스냅 삭제(재적재 직전) */
    int deleteRtaSummonerOpponentH2hSnapBySeason(@Param("seasonId") long seasonId);

    /**
     * 시즌 H2H 스냅을 wizard_id 배치로 삭제 — 단일 DELETE 장시간·I/O 오류 완화.
     * {@code idx_rta_agg_summoner_opp_h2h_season_wizard} 활용.
     */
    int deleteRtaSummonerOpponentH2hSnapBySeasonWizardBatch(
            @Param("seasonId") long seasonId,
            @Param("wizardBatchSize") int wizardBatchSize);

    /**
     * 시즌×participant 기준 1:1 H2H 전량 INSERT — {@code rta_match_participant} me/o 조인 집계.
     */
    int insertRtaSummonerOpponentH2hSnapForSeason(@Param("seasonId") long seasonId);

    Long countRtaSummonerOpponentH2hSnapRows();

    /** 시즌 단위: {@code rta_agg_summoner_season_fight_snap} 적재 (전체 재집계 — 레거시; 청크 방식으로 대체됨) */
    int insertRtaSummonerSeasonFightSnapForSeason(@Param("seasonId") long seasonId);

    /** 쓰로틀용: 해당 시즌 fight snap 의 MAX(computed_at) epoch ms. 행 없으면 null. */
    Long selectFightSnapMaxComputedAtForSeason(@Param("seasonId") long seasonId);

    /** 청크 증분: staging_rta_summoner_snap_rid 의 replay_id 기준 ADD-UPSERT (청크 트랜잭션 내 호출) */
    int insertRtaSummonerFightSnapForStagingReplays(@Param("seasonId") long seasonId);

    /** 청크 증분: staging_rta_summoner_snap_rid 의 replay_id 기준 rta_match_flat INSERT (청크 트랜잭션 내 호출) */
    int insertRtaMatchFlatForStagingReplays(@Param("seasonId") long seasonId);

    /** Redis 전체 워밍용: fight_snap 기준 wizard_id keyset 페이징 (map 리스트 반환) */
    List<java.util.Map<String, Object>> selectAllPlayerSummaryPageByFightSnap(
            @Param("seasonId") long seasonId,
            @Param("afterWizardId") long afterWizardId,
            @Param("pageSize") int pageSize);

    /**
     * 키셋: 시즌 내 {@code summoner_ranking_apply_result IS NULL} 인 {@code rta_match.replay_id} 중
     * after 보다 큰 것만 오름차순, 최대 limit 건.
     */
    List<Long> selectReplayIdsForSummonerMonsterSnapKeyset(
            @Param("seasonId") long seasonId,
            @Param("afterReplayIdExclusive") long afterReplayIdExclusive,
            @Param("limit") int limit);

    /**
     * {@code summoner_ranking_apply_result IS NULL} 인 매치가 하나라도 있는 시즌만 (무거운 스냅 배치 대상 시즌).
     */
    List<Long> selectSeasonIdsWithPendingSummonerRankingReplays();

    Long countPendingSummonerRankingReplays();

    /** 시즌 스냅 전량 삭제 직후 — 해당 시즌 {@code rta_match} 소환사 스냅 플래그를 NULL 로 (재집계 대기). */
    int clearSummonerRankingMatchFlagsForSeason(@Param("seasonId") long seasonId);

    /** 무거운 스냅: 청크 TX 전용 TEMP staging (public TRUNCATE 락 경합 방지). */
    void ensureSummonerSnapChunkTempStaging();

    /** 스테이징에 replay_id 적재 — 이후 집계 SQL 이 JOIN 으로 참조. */
    int insertStagingRtaSummonerSnapRidBatch(@Param("seasonId") long seasonId, @Param("rids") List<Long> rids);

    /**
     * {@code rta_agg_summoner_monster_snap} — 스테이징에 올린 rid 만 집계하여 INSERT.
     * 직전에 {@link #ensureSummonerSnapChunkTempStaging}, {@link #insertStagingRtaSummonerSnapRidBatch} 필수.
     */
    int insertRtaSummonerMonsterSnapForSeasonReplayChunk(@Param("seasonId") long seasonId);

    /**
     * {@code staging_rta_summoner_snap_rid} 에 올린 rid 의 {@code rta_match_unit_pick} 만으로
     * {@code rta_agg_summoner_owned_box_snap} 를 MERGE한다(청크 단위 증분. 전량 DELETE 없음).
     * 직전에 스테이징 적재되어 있어야 한다.
     */
    int mergeRtaSummonerOwnedBoxSnapFromStagingReplayChunk(@Param("seasonId") long seasonId);

    /** 스테이징 rid 에 해당하는 {@code rta_match} 만 S 마킹. */
    int markSummonerRankingAggDoneForStagingSeason(@Param("seasonId") long seasonId);

    Long countRtaSummonerSeasonFightSnapRows();

    Long countRtaSummonerMonsterSnapRows();

    Long countRtaSummonerPickTurnSnapRows();

    /**
     * RTA 픽 원천 전체 → 스냅 전량 교체(전량 DELETE 후 INSERT).
     * 정식 무거운 스냅 {@link com.smw.monster.batch.RtaSummonerRankingAggJob} 에서는 청크별
     * {@link #mergeRtaSummonerOwnedBoxSnapFromStagingReplayChunk} 만 사용하고, 수동
     * {@link com.smw.monster.batch.RtaSummonerOwnedBoxSnapJob} 에서만 호출한다.
     */
    int deleteAllRtaSummonerOwnedBoxSnap();

    int insertRtaSummonerOwnedBoxSnapFromRtaUnitPicks();

    Long countRtaSummonerOwnedBoxSnapRows();

    /** API 조회용 — {@code rta_agg_summoner_ranking_snap} */
    List<Map<String, Object>> getRtaSummonerRankingFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("countryFilter") String countryFilter);

    /** 닉네임 부분 일치 또는 위자드 ID 정확 일치 — {@code rta_agg_summoner_search_snap}(시즌 무관) */
    List<Map<String, Object>> searchRtaSummonersInAgg(@Param("query") String query, @Param("limit") int limit);

    /** 소환사 일별 점수 스냅 — {@code rta_agg_summoner_score_daily_snap} */
    List<Map<String, Object>> listRtaPlayerScoreDailySnap(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId, @Param("limit") int limit,
            @Param("recentTailOnly") Boolean recentTailOnly);

    Map<String, Object> getRtaPlayerSummaryFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonId") Long seasonId);

    /** page-data용 — 스냅 우선 summary(+ fight 스냅 컬럼). 스냅 미스 시 {@link #getRtaPlayerSummaryFromAgg} 폴백은 서비스에서 처리. */
    Map<String, Object> getRtaPlayerSummarySnapFirst(@Param("wizardId") String wizardId,
            @Param("seasonId") Long seasonId);

    /**
     * 수집 리플레이 participant 기준 과거 닉네임( DISTINCT wizard_name ).
     * {@code seasonId} null 이면 전 시즌.
     */
    List<Map<String, Object>> listRtaPlayerWizardNameHistory(@Param("wizardId") String wizardId,
            @Param("seasonId") Long seasonId, @Param("limit") int limit);

    /** 시즌×소환사 전투 분모 스냅 1행 — 없으면 null */
    Map<String, Object> getRtaPlayerSeasonFightSnapFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId);

    /** page-data용 fight 스냅 PK 조회만 — participant·unit_pick 라이브 폴백 없음 */
    Map<String, Object> getRtaPlayerSeasonFightSnapPkOnly(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId);

    /**
     * 시즌×소환사×몬스터 스냅 목록(비율 컬럼 포함).
     * {@code rta_agg_summoner_monster_snap} + fight 스냅, {@code monster} 메타.
     */
    List<Map<String, Object>> listRtaPlayerMonsterSnapFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId, @Param("limit") Integer limit,
            @Param("knownTotalMatches") Long knownTotalMatches);

    /**
     * 시즌×소환사×몬스터 — 드래프트 슬롯 묶음별 픽·승 — {@code rta_agg_summoner_pick_turn_snap} 롤업(스냅).
     */
    List<Map<String, Object>> listRtaPlayerMonsterPickBucketsFromSnap(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId, @Param("unitMasterId") long unitMasterId);

    /**
     * 소환사×몬스터 특정 픽 슬롯 경기 — {@code rta_match_unit_pick.pick_slot_no} 는 팀별 내부 순서 {@code 1..5}.
     */
    List<Map<String, Object>> listRtaPlayerMonsterPickSlotMatches(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId, @Param("unitMasterId") long unitMasterId,
            @Param("teamSide") int teamSide, @Param("pickSlotNo") int pickSlotNo, @Param("limit") int limit);

    /** 소환사별 RTA 픽·밴으로 노출된 몬스터 스냅 — {@code rta_agg_summoner_owned_box_snap} + {@code monster} 메타 */
    List<Map<String, Object>> listRtaSummonerOwnedBoxSnapByWizard(@Param("wizardId") String wizardId);

    /** 시너지 미집계 rid ({@code rta_match.synergy_applied_at IS NULL}, rid 오름차순). 성공/실패는 {@code synergy_apply_result}. */
    List<Long> selectPendingSynergyAggRids(@Param("batchSize") int batchSize);

    /** 시너지 미집계 1건 이상 여부 — partial index, COUNT 없음(배치 Job용). */
    Boolean existsPendingSynergyAgg();

    /** 시너지 미집계 건수 — 관리 API·캐시용. 대량 pending 시 풀 인덱스 스캔 비용 큼. */
    Long countPendingSynergyAgg();

    /** 파티션 배치용: 특정 rid 구간([minRid, maxRid]) 내 미집계 rid만 조회. */
    List<Long> selectPendingSynergyAggRidsBetween(@Param("minRid") long minRid,
                                                  @Param("maxRid") long maxRid,
                                                  @Param("batchSize") int batchSize);

    /**
     * 세션 수준 {@code enable_seqscan = OFF} — {@code idx_rta_match_synergy_pending} 강제 사용.
     * drain 루프 진입 직전에 호출하고, 완료 후 {@link #hintBatchEnableSeqScan()} 으로 복원한다.
     */
    void hintBatchDisableSeqScan();

    /** 세션 수준 {@code enable_seqscan = ON} — {@link #hintBatchDisableSeqScan()} 복원용. */
    void hintBatchEnableSeqScan();

    Map<String, Object> selectSynergyReplayRow(@Param("rid") long rid);

    List<Map<String, Object>> selectSynergyFieldUnits(@Param("rid") long rid);

    /** 시너지 집계: rta_match 다건 (rid·시즌·승자). {@code bigint[]} 한 번 바인딩. */
    List<Map<String, Object>> selectSynergyReplayRowsByRids(@Param("rids") long[] rids);

    /** 시너지 집계: 필드 유닛 다건 — 행마다 rid 포함 */
    List<Map<String, Object>> selectSynergyFieldUnitsByRids(@Param("rids") long[] rids);

    /** 시너지 집계: rid 당 wizard별 래더 rating_id */
    List<Map<String, Object>> selectSynergyWizardRatings(@Param("rid") long rid);

    /** 시너지 집계: participant 등급 다건 — 행마다 rid 포함 */
    List<Map<String, Object>> selectSynergyWizardRatingsByRids(@Param("rids") long[] rids);

    int upsertRtaSynergySoloAgg(@Param("rows") List<RtaSynergyAggUpsertRow> rows);

    int upsertRtaSynergyDuoAgg(@Param("rows") List<RtaSynergyAggUpsertRow> rows);

    int upsertRtaSynergyTrioAgg(@Param("rows") List<RtaSynergyAggUpsertRow> rows);

    /** COPY 적재 전·후 스테이징 비우기 */
    void truncateStagingSynergyAgg();

    /** 카운터: 솔로(상대 단일 유닛) — {@code rta_agg_counter_solo} */
    int upsertRtaCounterSoloAgg(@Param("rows") List<RtaCounterMatchupUpsertRow> rows);

    /** 카운터: 듀오(상대 2인 조합) — {@code rta_agg_counter_duo} */
    int upsertRtaCounterDuoAgg(@Param("rows") List<RtaCounterMatchupUpsertRow> rows);

    /** 카운터: 트리오(상대 3인 조합) — {@code rta_agg_counter_trio} */
    int upsertRtaCounterTrioAgg(@Param("rows") List<RtaCounterMatchupUpsertRow> rows);

    /** COPY 적재 전·후 스테이징 비우기 */
    void truncateStagingMatchupAgg();

    /**
     * {@code rta_agg_summoner_pick_turn_snap} — 스테이징 rid 청크 upsert. 몬 스냅과 동일 트랜잭션에서 호출.
     */
    int insertRtaSummonerPickTurnSnapForSeasonReplayChunk(@Param("seasonId") long seasonId);

    /**
     * 픽 순서별 집계 — {@code rta_agg_pick_turn}. 시즌 전량 DELETE 후 INSERT.
     */
    int deleteRtaPickTurnAggBySeason(@Param("seasonId") long seasonId);

    int insertRtaPickTurnAggForSeason(@Param("seasonId") long seasonId);

    /** {@code rta_match_participant}에 존재하는 distinct season_id 목록. 픽턴 집계 대상 시즌 확정용. */
    List<Long> selectDistinctParticipantSeasonIds();

    /** 집계 성공: {@code synergy_apply_result='S'} */
    int markSynergyAggDone(@Param("rid") long rid);

    /** 청크 단위 시너지 집계 완료 ({@code synergy_applied_at}, {@code synergy_apply_result='S'}). {@code replay_id = ANY(#{rids}::bigint[])} 단일 바인딩 */
    int markSynergyAggDoneForRids(@Param("rids") long[] rids);

    /** 집계 스킵(실패): {@code synergy_apply_result='F'} — 재시도 대열에서 제외 */
    int markSynergyAggFailed(@Param("rid") long rid);

    /** RTA 시즌 목록 (sort_order, season_no 순) */
    List<Map<String, Object>> listRtaSeasons();

    /** 금일(now)이 [start_at, end_at) 에 포함되는 season_id. 없으면 season_no 최대 폴백 */
    Long selectDefaultSeasonIdForNow();

    /** 캐시 워밍용: 시즌 내 daily_snap에 존재하는 (unit_master_id, rating_id) 쌍 전체 */
    @Select("SELECT DISTINCT unit_master_id, rating_id FROM rta_agg_monster_daily_snap WHERE season_id = #{seasonId}")
    List<Map<String, Object>> selectDistinctMonsterRatingPairsFromDailySnap(@Param("seasonId") Long seasonId);

    /** 시즌 PK로 start_at, end_at, season_code 조회 */
    Map<String, Object> selectRtaSeasonBoundsBySeasonId(@Param("seasonId") long seasonId);

    /** 공식 티어 규칙 참고 — 시즌별 커트라인(rta_rating_grade_season) 포함 */
    List<Map<String, Object>> listRtaRatingGradeReference(@Param("seasonId") long seasonId);

    /** 카운터 매치업(솔로) — {@code rta_agg_counter_solo}, 전 티어 합산, 상위 100 */
    List<Map<String, Object>> getRtaCounterSoloMatchups(@Param("monsterId") long monsterId,
            @Param("seasonId") Long seasonId, @Param("ratingId") int ratingId);

    /** 카운터 매치업(듀오) — {@code rta_agg_counter_duo}, 단일 티어, 상위 100 */
    List<Map<String, Object>> getRtaCounterDuoMatchups(@Param("monsterId") long monsterId,
            @Param("seasonId") Long seasonId, @Param("ratingId") int ratingId);

    /** 카운터 매치업(트리오) — {@code rta_agg_counter_trio}, 단일 티어, 상위 100 */
    List<Map<String, Object>> getRtaCounterTrioMatchups(@Param("monsterId") long monsterId,
            @Param("seasonId") Long seasonId, @Param("ratingId") int ratingId);

    /** 배치·점검용. API에서는 호출하지 않음. */
    List<Map<String, Object>> getRtaTierDistributionDailyLive(@Param("seasonId") Long seasonId);

    // ── 몬스터 일별/슬롯 집계 (배치) ──────────────────────────────────────

    /** 경기가 있는 시즌 목록과 시즌 시작일 반환 */
    List<Map<String, Object>> selectParticipantSeasonsWithStart();

    /** fromDate~today 사이 snap이 없는 날짜 목록 */
    List<String> selectMissingMonsterDailySnapDates(@Param("seasonId") long seasonId,
            @Param("fromDate") String fromDate);

    void deleteRtaMonsterDailySnapByDate(@Param("seasonId") long seasonId, @Param("snapDate") String snapDate);

    void insertRtaMonsterDailySnapForDate(@Param("seasonId") long seasonId, @Param("snapDate") String snapDate);

    /** participant 경기 있는데 스냅 행이 없는 KST 일자 (시즌 시작~오늘) */
    List<String> selectMissingSummonerScoreDailySnapDates(@Param("seasonId") long seasonId,
            @Param("fromDate") String fromDate);

    void insertRtaSummonerScoreDailySnapForDate(@Param("seasonId") long seasonId, @Param("snapDate") String snapDate);

    /** pick_slot_snap 미처리 replay_id 청크 */
    List<Long> selectPendingPickSlotSnapRids(@Param("batchSize") int batchSize);

    Long countPendingPickSlotSnap();

    /** pick_slot_snap 청크 누적 UPSERT — per_rating + all_rating(-1) 동시 처리 */
    int insertPickSlotSnapForRids(@Param("rids") long[] rids);

    /** pick_slot_snap 처리 성공 마킹 (pick_slot_snap_apply_result='S') */
    int markPickSlotSnapDoneForRids(@Param("rids") long[] rids);

    /** pick_slot_snap 처리 실패 마킹 (pick_slot_snap_apply_result='F', 재시도 제외) */
    int markPickSlotSnapFailedForRids(@Param("rids") long[] rids);

    void deleteRtaMonsterPickSlotSnapBySeason(@Param("seasonId") long seasonId, @Param("ratingId") int ratingId);

    void insertRtaMonsterPickSlotSnapForSeason(@Param("seasonId") long seasonId, @Param("ratingId") int ratingId);

    // ── 몬스터 개요 (라이브 API) ──────────────────────────────────────────

    Map<String, Object> getRtaMonsterOverviewStats(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId, @Param("ratingId") Integer ratingId);

    Map<String, Object> getRtaMonsterOverviewStatsByIds(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId, @Param("ratingIds") List<Integer> ratingIds);

    List<Map<String, Object>> getRtaMonsterDailyTrend(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId, @Param("ratingId") Integer ratingId, @Param("days") int days);

    List<Map<String, Object>> getRtaMonsterDailyTrendByIds(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId, @Param("ratingIds") List<Integer> ratingIds, @Param("days") int days);

    List<Map<String, Object>> getRtaMonsterDailyTrendPerRating(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId, @Param("ratingIds") List<Integer> ratingIds, @Param("days") int days);

    List<Map<String, Object>> getRtaMonsterPickSlots(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId, @Param("ratingId") Integer ratingId);

    List<Map<String, Object>> getRtaMonsterPickSlotsByIds(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId, @Param("ratingIds") List<Integer> ratingIds);

    List<Map<String, Object>> getRtaMonsterTopSummoners(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId, @Param("limit") int limit);

    List<Map<String, Object>> listRtaMonsterTopSummonerSnap(@Param("monsterId") int monsterId,
            @Param("seasonId") long seasonId);

    int deleteRtaMonsterTopSummonerSnapBySeason(@Param("seasonId") long seasonId);

    int insertRtaMonsterTopSummonerSnapForSeason(@Param("seasonId") long seasonId,
            @Param("minPickCnt") int minPickCnt, @Param("topN") int topN);

}
