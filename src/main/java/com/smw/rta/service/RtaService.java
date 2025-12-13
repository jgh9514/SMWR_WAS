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
}
