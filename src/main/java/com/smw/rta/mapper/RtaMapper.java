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
    
    /** RTA 몬스터 통계 집계 전체 삭제 후 재적재 */
    int deleteAllRtaMonsterStatsAgg();

    /** 대시보드 일자×티어 집계 테이블 전체 비우기 (배치 재적재 전) */
    int deleteAllRtaTierAggDaily();

    /** 시즌별 일자×티어 집계 적재 (participant 스캔은 배치에서만) */
    int insertRtaTierAggDailyForSeason(@Param("seasonCode") String seasonCode,
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

    /**
     * 일자×티어별 출현 수 (대시보드). {@code rta_agg_tier_daily} 배치 적재본.
     */
    List<Map<String, Object>> getRtaTierDistributionDaily(
            @Param("seasonCode") String seasonCode,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 리플레이 기간 min/max — {@code rta_match.played_at} (대시보드) */
    Map<String, Object> getRtaReplayDateRangeFromAgg(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

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

    /** 시너지 미집계 rid ({@code rta_match.synergy_applied_at IS NULL}, rid 오름차순) */
    List<Long> selectPendingSynergyAggRids(@Param("batchSize") int batchSize);

    Map<String, Object> selectSynergyReplayRow(@Param("rid") long rid);

    List<Map<String, Object>> selectSynergyFieldUnits(@Param("rid") long rid);

    int upsertRtaSynergyAgg(@Param("rows") List<RtaSynergyAggUpsertRow> rows);

    /** 몬스터 상세 카운터: 필드 유닛(subject) vs 상대 듀오·트리오(opponent_combo_key) 승·패 */
    int upsertRtaCounterMatchupAgg(@Param("rows") List<RtaCounterMatchupUpsertRow> rows);

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
