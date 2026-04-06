package com.smw.rta.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.smw.rta.model.RtaSynergyAggUpsertRow;

@Mapper
public interface RtaMapper {
    
    List<Map<String, Object>> getRtaMatches(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    List<Map<String, Object>> getPlayerRtaMatches(@Param("wizardId") String wizardId, @Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    int getTotalRtaMatches(@Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    int getTodayRtaMatches(@Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    int getWeeklyRtaMatches(@Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    Map<String, Object> testRtaData();
    
    List<Map<String, Object>> debugRtaData();
    
    List<Map<String, Object>> debugMatchDetail(@Param("rid") String rid);
    
    /** RTA 몬스터 통계 집계 전체 삭제 후 재적재 */
    int deleteAllRtaMonsterStatsAgg();

    int insertRtaMonsterStatsMetaForSeason(@Param("seasonCode") String seasonCode,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    int insertRtaMonsterStatsAggForSeason(@Param("seasonCode") String seasonCode,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 시즌 총 매치 수 (집계 메타) */
    Long getRtaMonsterStatsTotalFromAgg(@Param("seasonCode") String seasonCode);

    /** RTA 몬스터별 통계 — 집계 테이블만 */
    List<Map<String, Object>> getRtaMonsterStatsFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonCode") String seasonCode);

    /** 2마리 조합 — {@code rta_agg_synergy_combo} (combo_size=2) */
    List<Map<String, Object>> getRtaDuoComboStatsFromAgg(@Param("limit") int limit);

    /** 3마리 조합 — {@code rta_agg_synergy_combo} (combo_size=3) */
    List<Map<String, Object>> getRtaTrioComboStatsFromAgg(@Param("limit") int limit);
    
    /**
     * RTA 몬스터 기본 정보 조회
     */
    Map<String, Object> getRtaMonsterBasicInfo(@Param("monsterId") int monsterId,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    /**
     * RTA 몬스터 강한 상대 조회
     */
    List<Map<String, Object>> getRtaMonsterStrongAgainst(@Param("monsterId") int monsterId,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    /**
     * RTA 몬스터 좋은 콤비 조회
     */
    List<Map<String, Object>> getRtaMonsterGoodCombos(@Param("monsterId") int monsterId,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    /**
     * RTA 몬스터 좋은 3체인 콤비 조회
     */
    List<Map<String, Object>> getRtaMonsterGoodTripleCombos(@Param("monsterId") int monsterId,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    /**
     * RTA 몬스터 최근 경기 조회
     */
    List<Map<String, Object>> getRtaMonsterRecentMatches(@Param("monsterId") int monsterId,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 일자×티어별 출현 수 — 라이브 집계 (대시보드) */
    List<Map<String, Object>> getRtaTierDistributionDailyFromAgg(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 리플레이 기간 min/max — {@code rta_match.played_at} (대시보드) */
    Map<String, Object> getRtaReplayDateRangeFromAgg(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 랭크 컷 앵커 — 현재 시각 기준 되돌림 구간 [now-iv, now] 라이브 집계 (대시보드) */
    List<Map<String, Object>> getRtaRankCutoffAnchorsFromLive();

    /** 티어 분포 집계 테이블 미사용 시 no-op (배치 호환) */
    int deleteAllRtaTierDistributionDailyAgg();

    int insertRtaTierDistributionDailyAggFromLive();

    /** 소환사 랭킹 (최근 리플레이 기준 점수 순) */
    int getRtaSummonerRankingCount(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    List<Map<String, Object>> getRtaSummonerRanking(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    int deleteAllRtaSummonerRankingAgg();

    int insertRtaSummonerRankingAggForSeason(@Param("seasonCode") String seasonCode,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    int getRtaSummonerRankingAggCount(@Param("seasonCode") String seasonCode,
            @Param("countryFilter") String countryFilter,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    List<Map<String, Object>> getRtaSummonerRankingFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonCode") String seasonCode, @Param("countryFilter") String countryFilter,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 랭킹 라이브 검색(닉네임 부분 일치 또는 위자드 ID 정확 일치) */
    List<Map<String, Object>> searchRtaSummonersInAgg(@Param("seasonCode") String seasonCode, @Param("query") String query,
            @Param("limit") int limit,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 소환사 상세 헤더용 요약 (수집 리플레이 기준 최신 점수·글로벌 순위) */
    Map<String, Object> getRtaPlayerSummary(@Param("wizardId") String wizardId,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 소환사 요약 — {@link #getRtaPlayerSummary} 와 동일 라이브 집계 */
    Map<String, Object> getRtaPlayerSummaryFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonCode") String seasonCode,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 스냅샷 미집계 rid — v2에서 스냅샷 테이블 제거 시 no-op 스텁과 연동 */
    List<Long> selectPendingRtaAggRids(@Param("batchSize") int batchSize);

    /** 레거시 스냅샷 upsert — v2 스키마에서는 no-op 스텁 */
    int upsertRtaMatchSnapshotsForRids(@Param("rids") List<Long> rids);

    /** 레거시 스냅샷 done — v2 스키마에서는 no-op 스텁 */
    int markRtaAggDoneForRidsWithSnapshot(@Param("rids") List<Long> rids);

    /** 시너지 미집계 rid ({@code rta_match.synergy_applied_at IS NULL}, rid 오름차순) */
    List<Long> selectPendingSynergyAggRids(@Param("batchSize") int batchSize);

    Map<String, Object> selectSynergyReplayRow(@Param("rid") long rid);

    List<Map<String, Object>> selectSynergyFieldUnits(@Param("rid") long rid);

    int upsertRtaSynergyAgg(@Param("rows") List<RtaSynergyAggUpsertRow> rows);

    /** pending 인 rid 만 done */
    int markSynergyAggDone(@Param("rid") long rid);

    /** 청크 단위 시너지 집계 완료 (synergy_applied_at = now) */
    int markSynergyAggDoneForRids(@Param("rids") List<Long> rids);

    int markSynergyAggFailed(@Param("rid") long rid);

    /** RTA 시즌 목록 (sort_order, season_no 순) */
    List<Map<String, Object>> listRtaSeasons();

    /** 금일(now)이 [start_at, end_at) 에 포함되는 season_code. 없으면 season_no 최대 폴백 */
    String selectDefaultSeasonCodeForNow();

    /** 시즌 코드로 start_at, end_at 조회 */
    Map<String, Object> selectRtaSeasonBounds(@Param("seasonCode") String seasonCode);

    /** 공식 티어 규칙 참고 (rta_rating_grade.tier_key 등) */
    List<Map<String, Object>> listRtaRatingGradeReference();

    /** 시즌별 최신 스냅샷 컷 (rta_snapshot_rank_cut) */
    List<Map<String, Object>> getRtaSnapshotRankCutLatest(@Param("seasonCode") String seasonCode);

    /** 몬스터 상세: 카운터 매치업 (rta_agg_counter_matchup, rating_id=0) */
    List<Map<String, Object>> getRtaMonsterCounterMatchups(@Param("monsterId") long monsterId,
            @Param("seasonCode") String seasonCode);
}
