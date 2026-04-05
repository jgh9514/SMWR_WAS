package com.smw.rta.service;

import java.util.List;
import java.util.Map;

public interface RtaService {
    
    /**
     * Get RTA match list
     */
    List<Map<String, Object>> getRtaMatches(int limit, int offset);
    
    /**
     * Get player RTA match list
     */
    List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset);
    
    /**
     * Get RTA match count
     */
    long getRtaMatchesCount();
    
    /**
     * Get RTA statistics
     */
    Object getRtaStats();
    
    /**
     * Test RTA data
     */
    Map<String, Object> testRtaData();
    
    /**
     * Get RTA monster statistics
     */
    Map<String, Object> getRtaMonsterStats(int limit, int offset);
    
    /**
     * Get RTA monster detail information
     */
    Map<String, Object> getRtaMonsterDetail(int monsterId);

    /**
     * RTA 대시보드: 일별 티어 집계 + 날짜 범위 (클라이언트에서 기간 필터)
     */
    Map<String, Object> getRtaDashboard();

    /**
     * RTA 소환사 랭킹 (수집 리플레이 중 소환사별 최신 경기 점수 기준)
     */
    Map<String, Object> getRtaSummonerRanking(int limit, int offset);

    /** RTA 소환사 요약 (닉네임·점수·순위·승률 등) */
    Map<String, Object> getRtaPlayerSummary(String wizardId);
}
