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
} 
