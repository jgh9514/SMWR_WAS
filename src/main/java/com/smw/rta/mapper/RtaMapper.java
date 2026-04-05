package com.smw.rta.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.smw.rta.model.RtaSynergyAggUpsertRow;
import com.smw.rta.model.RtaSynergyComboRow;

@Mapper
public interface RtaMapper {
    
    List<Map<String, Object>> getRtaMatches(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    List<Map<String, Object>> getPlayerRtaMatches(@Param("wizardId") String wizardId, @Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    int getTotalRtaMatches(@Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    int getTodayRtaMatches(@Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    int getWeeklyRtaMatches(@Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
    Map<String, Object> getRtaStats();
    
    Map<String, Object> testRtaData();
    
    List<Map<String, Object>> debugRtaData();
    
    List<Map<String, Object>> debugMatchDetail(@Param("rid") String rid);
    
    /**
     * RTA 몬스터별 통계 조회
     */
    List<Map<String, Object>> getRtaMonsterStats(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 동일 팀에서 함께 등장한 2마리 조합 승률 (전역 상위) */
    List<Map<String, Object>> getRtaDuoComboStats(@Param("limit") int limit,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 동일 팀에서 함께 등장한 3마리 조합 승률 (전역 상위) */
    List<Map<String, Object>> getRtaTrioComboStats(@Param("limit") int limit,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);
    
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

    /** 일자×티어별 출현 수 — 집계 테이블만 (대시보드) */
    List<Map<String, Object>> getRtaTierDistributionDailyFromAgg(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 리플레이 기간 min/max — 집계 테이블(티어 분포 일자)만 (대시보드) */
    Map<String, Object> getRtaReplayDateRangeFromAgg(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 랭크 컷 스냅샷 — 집계 테이블만 (대시보드) */
    List<Map<String, Object>> getRtaRankCutoffAnchorsFromAgg();

    /** 티어 분포 집계 전체 삭제 후 재적재용 */
    int deleteAllRtaTierDistributionDailyAgg();

    int insertRtaTierDistributionDailyAggFromLive();

    int deleteAllRtaRankCutoffSnapshot();

    int insertRtaRankCutoffSnapshotFromLive();

    /** 소환사 랭킹 (최근 리플레이 기준 점수 순) */
    int getRtaSummonerRankingCount(
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    List<Map<String, Object>> getRtaSummonerRanking(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    int deleteAllRtaSummonerRankingAgg();

    int insertRtaSummonerRankingAggForSeason(@Param("seasonCode") String seasonCode,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    int getRtaSummonerRankingAggCount(@Param("seasonCode") String seasonCode);

    List<Map<String, Object>> getRtaSummonerRankingFromAgg(@Param("limit") int limit, @Param("offset") int offset,
            @Param("seasonCode") String seasonCode);

    /** 소환사 상세 헤더용 요약 (수집 리플레이 기준 최신 점수·글로벌 순위) */
    Map<String, Object> getRtaPlayerSummary(@Param("wizardId") String wizardId,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 소환사 요약 — 랭킹·프로필은 agg, 경기 수·승률은 내 경기 목록과 동일 소스(rta_replay_match_snapshot) */
    Map<String, Object> getRtaPlayerSummaryFromAgg(@Param("wizardId") String wizardId,
            @Param("seasonCode") String seasonCode,
            @Param("seasonStart") Timestamp seasonStart, @Param("seasonEnd") Timestamp seasonEnd);

    /** 스냅샷 미집계 rid (rta_agg_status = pending, rid 오름차순) */
    List<Long> selectPendingRtaAggRids(@Param("batchSize") int batchSize);

    /** getRtaMatches 와 동일 조인으로 rta_replay_match_snapshot upsert (조회는 집계 테이블 사용) */
    int upsertRtaMatchSnapshotsForRids(@Param("rids") List<Long> rids);

    /** 스냅샷이 존재하는 rid 만 replay_list 를 done 처리 */
    int markRtaAggDoneForRidsWithSnapshot(@Param("rids") List<Long> rids);

    /** 시너지 미집계 rid (synergy_agg_status = pending, rid 오름차순) */
    List<Long> selectPendingSynergyAggRids(@Param("batchSize") int batchSize);

    Map<String, Object> selectSynergyReplayRow(@Param("rid") long rid);

    List<Map<String, Object>> selectSynergyFieldUnits(@Param("rid") long rid);

    int insertRtaSynergyFacts(@Param("rows") List<RtaSynergyComboRow> rows);

    int upsertRtaSynergyAgg(@Param("rows") List<RtaSynergyAggUpsertRow> rows);

    /** pending 인 rid 만 done */
    int markSynergyAggDone(@Param("rid") long rid);

    int markSynergyAggFailed(@Param("rid") long rid);

    /** RTA 시즌 목록 (sort_order, season_no 순) */
    List<Map<String, Object>> listRtaSeasons();

    /** 금일(now)이 [start_at, end_at) 에 포함되는 season_code. 없으면 season_no 최대 폴백 */
    String selectDefaultSeasonCodeForNow();

    /** 시즌 코드로 start_at, end_at 조회 */
    Map<String, Object> selectRtaSeasonBounds(@Param("seasonCode") String seasonCode);
}
