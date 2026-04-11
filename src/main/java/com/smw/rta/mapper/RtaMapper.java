package com.smw.rta.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.smw.rta.model.RtaCounterMatchupUpsertRow;
import com.smw.rta.model.RtaSynergyAggUpsertRow;

@Mapper
public interface RtaMapper {
    
    List<Map<String, Object>> getRtaMatches(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd,
            @Param("seasonId") Long seasonId, @Param("tierKey") String tierKey);
    
    List<Map<String, Object>> getPlayerRtaMatches(@Param("wizardId") String wizardId, @Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd,
            @Param("seasonId") Long seasonId);
    
    int getTodayRtaMatches(@Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd,
            @Param("seasonId") Long seasonId, @Param("tierKey") String tierKey);
    
    int getWeeklyRtaMatches(@Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd,
            @Param("seasonId") Long seasonId, @Param("tierKey") String tierKey);

    /**
     * 이전 시그니처( seasonId 없음 )와의 바이너리 호환 — XML 은 5번째 파라미터만 매핑.
     */
    default List<Map<String, Object>> getRtaMatches(int limit, int offset, Timestamp seasonStart, Timestamp seasonEnd) {
        return getRtaMatches(limit, offset, seasonStart, seasonEnd, null, null);
    }

    default List<Map<String, Object>> getRtaMatches(int limit, int offset, Timestamp seasonStart, Timestamp seasonEnd,
            Long seasonId) {
        return getRtaMatches(limit, offset, seasonStart, seasonEnd, seasonId, null);
    }

    default List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, Timestamp seasonStart,
            Timestamp seasonEnd) {
        return getPlayerRtaMatches(wizardId, limit, offset, seasonStart, seasonEnd, null);
    }

    default int getTodayRtaMatches(Timestamp seasonStart, Timestamp seasonEnd) {
        return getTodayRtaMatches(seasonStart, seasonEnd, null, null);
    }

    default int getTodayRtaMatches(Timestamp seasonStart, Timestamp seasonEnd, Long seasonId) {
        return getTodayRtaMatches(seasonStart, seasonEnd, seasonId, null);
    }

    default int getWeeklyRtaMatches(Timestamp seasonStart, Timestamp seasonEnd) {
        return getWeeklyRtaMatches(seasonStart, seasonEnd, null, null);
    }

    default int getWeeklyRtaMatches(Timestamp seasonStart, Timestamp seasonEnd, Long seasonId) {
        return getWeeklyRtaMatches(seasonStart, seasonEnd, seasonId, null);
    }

    /** (호환) 예전 빌드에서 참조할 수 있음 — 현재 서비스 경로에서는 미사용 가능 */
    List<Map<String, Object>> selectMonsterPortraitMetaByIds(@Param("ids") List<String> ids);
    
    Map<String, Object> testRtaData();
    
    List<Map<String, Object>> debugRtaData();
    
    List<Map<String, Object>> debugMatchDetail(@Param("rid") String rid);
    
    /** 시즌별 일자×티어 집계 적재 — 해당 시즌 행만 삭제 후 INSERT (배치 전용). {@code season_id} 기준. */
    int insertRtaTierAggDailyForSeason(@Param("seasonId") long seasonId,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 시즌 총 매치 수 (집계 메타) */
    Long getRtaMonsterStatsTotalFromAgg(@Param("seasonCode") String seasonCode);

    /**
     * RTA 몬스터별 통계 — {@code rta_agg_synergy_combo} (combo_size=1). 선픽/벤은 시너지 집계에 없음 → 0.
     *
     * @param tierKey null/빈값=전체 티어 합산, CH_ALL, F_ALL, 또는 rta_rating_grade.tier_key (Ch1, F3, G3…)
     */
    List<Map<String, Object>> getRtaMonsterStatsFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonCode") String seasonCode, @Param("tierKey") String tierKey);

    /**
     * 몬스터별 통계 — 집계 테이블이 비었을 때 시즌 구간에서 픽 원본으로 라이브 집계.
     */
    List<Map<String, Object>> getRtaMonsterStatsLive(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonCode") String seasonCode, @Param("seasonStart") Timestamp seasonStart,
            @Param("seasonEnd") Timestamp seasonEnd);

    Long countRtaMonsterStatsFromAgg(@Param("seasonCode") String seasonCode, @Param("tierKey") String tierKey);

    Long countRtaMonsterStatsLive(@Param("seasonCode") String seasonCode, @Param("seasonStart") Timestamp seasonStart,
            @Param("seasonEnd") Timestamp seasonEnd);

    /** 2마리 조합 — {@code rta_agg_synergy_combo} (combo_size=2) */
    List<Map<String, Object>> getRtaDuoComboStatsFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonCode") String seasonCode, @Param("tierKey") String tierKey);

    Long countRtaDuoComboStatsFromAgg(@Param("seasonCode") String seasonCode, @Param("tierKey") String tierKey);

    /** 3마리 조합 — {@code rta_agg_synergy_combo} (combo_size=3) */
    List<Map<String, Object>> getRtaTrioComboStatsFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonCode") String seasonCode, @Param("tierKey") String tierKey);

    Long countRtaTrioComboStatsFromAgg(@Param("seasonCode") String seasonCode, @Param("tierKey") String tierKey);
    
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

    /**
     * 일자×티어별 출현 수 (대시보드). {@code rta_agg_tier_daily} 배치 적재본.
     */
    List<Map<String, Object>> getRtaTierDistributionDaily(@Param("seasonCode") String seasonCode);

    /** 리플레이 기간 min/max — {@code rta_match.played_at} (대시보드) */
    Map<String, Object> getRtaReplayDateRangeFromAgg(@Param("seasonCode") String seasonCode);

    /** 랭크 컷 앵커 — 현재 시각 기준 되돌림 구간 [now-iv, now] 라이브 집계 (대시보드). 시즌 경계는 티어 분포와 동일하게 전달 */
    List<Map<String, Object>> getRtaRankCutoffAnchorsFromLive(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 랭크 컷 앵커 — {@code rta_rank_cutoff_anchor_snap} 배치 적재 결과 */
    List<Map<String, Object>> getRtaRankCutoffAnchorsFromAgg();

    /** 앵커 스냅샷 전체 삭제 후 라이브와 동일 로직으로 재적재 */
    int deleteAllRtaRankCutoffAnchorSnap();

    int insertRtaRankCutoffAnchorSnapFromLive();

    /** 시즌×등급별 컷 히스토리 1회 적재 */
    int insertRtaSnapshotRankCutForAllSeasons();

    /** 소환사 랭킹 (최근 리플레이 기준 점수 순) */
    int getRtaSummonerRankingCount(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    List<Map<String, Object>> getRtaSummonerRanking(@Param("limit") int limit, @Param("offset") int offset,
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

    int upsertRtaSynergyAgg(@Param("rows") List<RtaSynergyAggUpsertRow> rows);

    void truncateStagingSynergyAgg();

    int mergeStagingIntoRtaAggSynergyCombo();

    /** 몬스터 상세 카운터: 필드 유닛(subject) vs 상대 듀오·트리오(opponent_combo_key) 승·패 */
    int upsertRtaCounterMatchupAgg(@Param("rows") List<RtaCounterMatchupUpsertRow> rows);

    /** COPY 적재 전·후 스테이징 비우기 */
    void truncateStagingMatchupAgg();

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

    /** 금일(now)이 [start_at, end_at) 에 포함되는 season_code. 없으면 season_no 최대 폴백 */
    String selectDefaultSeasonCodeForNow();

    /** 시즌 코드로 start_at, end_at 조회 */
    Map<String, Object> selectRtaSeasonBounds(@Param("seasonCode") String seasonCode);

    /** 공식 티어 규칙 참고 (rta_rating_grade.tier_key 등) */
    List<Map<String, Object>> listRtaRatingGradeReference();

    /** 시즌별 최신 스냅샷 컷 (rta_snapshot_rank_cut) */
    List<Map<String, Object>> getRtaSnapshotRankCutLatest(@Param("seasonCode") String seasonCode);

    /** 몬스터 상세: 카운터 매치업 (rta_agg_counter_matchup) */
    List<Map<String, Object>> getRtaMonsterCounterMatchups(@Param("monsterId") long monsterId,
            @Param("seasonCode") String seasonCode);
}
