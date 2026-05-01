package com.smw.rta.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /** 시즌별 일자×티어 집계 적재 — 해당 시즌 행만 삭제 후 INSERT (배치 전용). {@code season_id} 기준. */
    int insertRtaTierAggDailyForSeason(@Param("seasonId") long seasonId);

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

    /** 랭크 컷 앵커 — {@code rta_rank_cutoff_anchor_snap} 배치 적재 결과 */
    List<Map<String, Object>> getRtaRankCutoffAnchorsFromAgg();

    /** 앵커 스냅샷 전체 삭제 (운영 락 완화용 DELETE) */
    void deleteAllRtaRankCutoffAnchorSnap();

    int insertRtaRankCutoffAnchorSnapFromLive(@Param("seasonId") long seasonId);

    /** 시즌×등급별 컷 히스토리 1회 적재 */
    int insertRtaSnapshotRankCutForAllSeasons();

    /** 시즌×티어별 총 경기 수 upsert 재집계 ({@code rta_agg_season_rating_match_total}). */
    int rebuildRtaSeasonRatingMatchTotal();

    /** {@code rta_rank_cutoff_anchor_snap} 전체 행 수 (배치 적재 후 로깅) */
    Long countRtaRankCutoffAnchorSnapRows();

    /** 가장 최근 {@code snapshot_at} 묶음의 행 수 (직전 INSERT 배치 규모 추정) */
    Long countRtaSnapshotRankCutAtLatestSnapshot();

    /** {@code rta_agg_season_rating_match_total} 전체 행 수 (배치 적재 후 로깅) */
    Long countRtaSeasonRatingMatchTotalRows();

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

    /** participant 기준 wizard_id 당 대표 1행을 upsert(닉·국가 동일이면 UPDATE 생략) */
    int upsertRtaSummonerSearchSnap();

    /** {@code rta_agg_monster_stats_tier_top_snap} 시즌 전체 삭제 후 솔/듀/트 상위 100 재적재 */
    int deleteRtaMonsterStatsTierTopSnapBySeason(@Param("seasonId") long seasonId);

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
     * 시즌×participant 기준 1:1 H2H 전량 INSERT — {@code rta_match_participant} me/o 조인 집계.
     */
    int insertRtaSummonerOpponentH2hSnapForSeason(@Param("seasonId") long seasonId);

    Long countRtaSummonerOpponentH2hSnapRows();

    /** 시즌 단위: {@code rta_agg_summoner_season_fight_snap} 적재 */
    int insertRtaSummonerSeasonFightSnapForSeason(@Param("seasonId") long seasonId);

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

    /** 시즌 스냅 전량 삭제 직후 — 해당 시즌 {@code rta_match} 소환사 스냅 플래그를 NULL 로 (재집계 대기). */
    int clearSummonerRankingMatchFlagsForSeason(@Param("seasonId") long seasonId);

    /** 무거운 스냅: 청크 시작 시 스테이징 비우기(UNLOGGED). */
    void truncateStagingRtaSummonerSnapRid();

    /** 스테이징에 replay_id 적재 — 이후 집계 SQL 이 JOIN 으로 참조. */
    int insertStagingRtaSummonerSnapRidBatch(@Param("seasonId") long seasonId, @Param("rids") List<Long> rids);

    /**
     * {@code rta_agg_summoner_monster_snap} — 스테이징에 올린 rid 만 집계하여 INSERT.
     * 직전에 {@link #truncateStagingRtaSummonerSnapRid}, {@link #insertStagingRtaSummonerSnapRidBatch} 필수.
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

    /** 소환사 요약 — 수집 리플레이 기준 최신 점수·글로벌 순위 */
    Map<String, Object> getRtaPlayerSummaryFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonId") Long seasonId);

    /** 시즌×소환사 전투 분모 스냅 1행 — 없으면 null */
    Map<String, Object> getRtaPlayerSeasonFightSnapFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId);

    /**
     * 시즌×소환사×몬스터 스냅 목록(비율 컬럼 포함).
     * {@code rta_agg_summoner_monster_snap} + fight 스냅, {@code monster} 메타.
     */
    List<Map<String, Object>> listRtaPlayerMonsterSnapFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonId") long seasonId);

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

    /** 시즌 PK로 start_at, end_at, season_code 조회 */
    Map<String, Object> selectRtaSeasonBoundsBySeasonId(@Param("seasonId") long seasonId);

    /** 공식 티어 규칙 참고 — 시즌별 커트라인(rta_rating_grade_season) 포함 */
    List<Map<String, Object>> listRtaRatingGradeReference(@Param("seasonId") long seasonId);

    /** 시즌별 최신 스냅샷 컷 (rta_snapshot_rank_cut) */
    List<Map<String, Object>> getRtaSnapshotRankCutLatest(@Param("seasonId") Long seasonId);

    /** 몬스터 상세: 카운터 매치업 — {@code rta_agg_counter_solo/duo/trio} UNION, 전 티어 합산 */
    List<Map<String, Object>> getRtaMonsterCounterMatchups(@Param("monsterId") long monsterId,
            @Param("seasonId") Long seasonId);

    /** 배치·점검용. API에서는 호출하지 않음. */
    List<Map<String, Object>> getRtaTierDistributionDailyLive(@Param("seasonId") Long seasonId);

    /** 배치 적재 SQL과 동일 로직 점검용. API에서는 호출하지 않음. */
    List<Map<String, Object>> getRtaRankCutoffAnchorsFromLive(@Param("seasonId") Long seasonId);
}
