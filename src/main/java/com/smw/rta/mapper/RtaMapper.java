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

    /** 시즌별 상위 500명 스냅샷 삭제 후 재적재에 사용 */
    int deleteRtaSummonerRankingSnapBySeason(@Param("seasonId") long seasonId);

    /** 시즌별 상위 500명 스냅샷 적재 */
    int insertRtaSummonerRankingSnapForSeason(@Param("seasonId") long seasonId);

    /** 전체 랭킹 스냅샷 행 수 (배치 적재 후 로깅) */
    Long countRtaSummonerRankingSnapRows();

    /** API 조회용 — {@code rta_agg_summoner_ranking_snap} */
    List<Map<String, Object>> getRtaSummonerRankingFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonId") Long seasonId, @Param("countryFilter") String countryFilter);

    /** 닉네임 부분 일치 또는 위자드 ID 정확 일치 */
    List<Map<String, Object>> searchRtaSummonersInAgg(@Param("query") String query,
            @Param("limit") int limit, @Param("seasonId") Long seasonId);

    /** 소환사 요약 — 수집 리플레이 기준 최신 점수·글로벌 순위 */
    Map<String, Object> getRtaPlayerSummaryFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonId") Long seasonId);

    /** 시너지 미집계 rid ({@code rta_match.synergy_applied_at IS NULL}, rid 오름차순). 성공/실패는 {@code synergy_apply_result}. */
    List<Long> selectPendingSynergyAggRids(@Param("batchSize") int batchSize);

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

    /** 몬스터 상세 카운터: 필드 유닛(subject) vs 상대 듀오·트리오(opponent_combo_key) 승·패 */
    int upsertRtaCounterMatchupAgg(@Param("rows") List<RtaCounterMatchupUpsertRow> rows);

    /** COPY 적재 전·후 스테이징 비우기 */
    void truncateStagingMatchupAgg();

    /** 배치 전 조회용 인덱스 DROP (merge 속도 향상) */
    void dropCounterMatchupQueryIndex();

    /** 배치 후 조회용 인덱스 REBUILD */
    void rebuildCounterMatchupQueryIndex();

    /** staging_matchup_agg 집계 → rta_agg_counter_matchup 누적 UPSERT (한 방) */
    int mergeStagingIntoRtaAggCounterMatchup();

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

    /** 몬스터 상세: 카운터 매치업 (rta_agg_counter_matchup) */
    List<Map<String, Object>> getRtaMonsterCounterMatchups(@Param("monsterId") long monsterId,
            @Param("seasonId") Long seasonId);

    /** 배치·점검용. API에서는 호출하지 않음. */
    List<Map<String, Object>> getRtaTierDistributionDailyLive(@Param("seasonId") Long seasonId);

    /** 배치 적재 SQL과 동일 로직 점검용. API에서는 호출하지 않음. */
    List<Map<String, Object>> getRtaRankCutoffAnchorsFromLive(@Param("seasonId") Long seasonId);
}
