package com.smw.rta.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RtaMapper {
    
    List<Map<String, Object>> getRtaMatches(@Param("limit") int limit, @Param("offset") int offset);
    
    List<Map<String, Object>> getPlayerRtaMatches(@Param("wizardId") String wizardId, @Param("limit") int limit, @Param("offset") int offset);
    
    int getTotalRtaMatches();
    
    int getTodayRtaMatches();
    
    int getWeeklyRtaMatches();
    
    Map<String, Object> getRtaStats();
    
    Map<String, Object> testRtaData();
    
    List<Map<String, Object>> debugRtaData();
    
    List<Map<String, Object>> debugMatchDetail(@Param("rid") String rid);
    
    /**
     * RTA 몬스터별 통계 조회
     */
    List<Map<String, Object>> getRtaMonsterStats(@Param("limit") int limit, @Param("offset") int offset);

    /** 동일 팀에서 함께 등장한 2마리 조합 승률 (전역 상위) */
    List<Map<String, Object>> getRtaDuoComboStats(@Param("limit") int limit);

    /** 동일 팀에서 함께 등장한 3마리 조합 승률 (전역 상위) */
    List<Map<String, Object>> getRtaTrioComboStats(@Param("limit") int limit);
    
    /**
     * RTA 몬스터 기본 정보 조회
     */
    Map<String, Object> getRtaMonsterBasicInfo(@Param("monsterId") int monsterId);
    
    /**
     * RTA 몬스터 강한 상대 조회
     */
    List<Map<String, Object>> getRtaMonsterStrongAgainst(@Param("monsterId") int monsterId);
    
    /**
     * RTA 몬스터 좋은 콤비 조회
     */
    List<Map<String, Object>> getRtaMonsterGoodCombos(@Param("monsterId") int monsterId);
    
    /**
     * RTA 몬스터 좋은 3체인 콤비 조회
     */
    List<Map<String, Object>> getRtaMonsterGoodTripleCombos(@Param("monsterId") int monsterId);
    
    /**
     * RTA 몬스터 최근 경기 조회
     */
    List<Map<String, Object>> getRtaMonsterRecentMatches(@Param("monsterId") int monsterId);

    /** 일자×티어별 출현 수 (전체 기간, 대시보드 클라이언트에서 기간 합산) */
    List<Map<String, Object>> getRtaTierDistributionDaily();

    /** 리플레이 날짜 min/max */
    Map<String, Object> getRtaReplayDateRange();

    /** 앵커 시각(3h·6h·12h·3d·7d) 기준 일자 내 최저 점수 (P2·P3·G1~G3, 랭크 컷 추정용) */
    List<Map<String, Object>> getRtaRankCutoffAnchors();

    /** 소환사 랭킹 (최근 리플레이 기준 점수 순) */
    int getRtaSummonerRankingCount();

    List<Map<String, Object>> getRtaSummonerRanking(@Param("limit") int limit, @Param("offset") int offset);
} 
