package com.smw.rta.service;

import com.smw.rta.mapper.RtaMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Primary
public class RtaServiceImpl implements RtaService {

    @Autowired
    private RtaMapper rtaMapper;

    @Override
    public List<Map<String, Object>> getRtaMatches(int limit, int offset) {
        return rtaMapper.getRtaMatches(limit, offset);
    }

    @Override
    public List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset) {
        return rtaMapper.getPlayerRtaMatches(wizardId, limit, offset);
    }

    @Override
    public long getRtaMatchesCount() {
        return rtaMapper.getTotalRtaMatches();
    }

    @Override
    public Object getRtaStats() {
        Map<String, Object> stats = new HashMap<>();
        
        int totalMatches = rtaMapper.getTotalRtaMatches();
        stats.put("totalMatches", totalMatches);
        
        int todayMatches = rtaMapper.getTodayRtaMatches();
        stats.put("todayMatches", todayMatches);
        
        int weeklyMatches = rtaMapper.getWeeklyRtaMatches();
        stats.put("weeklyMatches", weeklyMatches);
        
        return stats;
    }
    
    @Override
    public Map<String, Object> testRtaData() {
        return rtaMapper.testRtaData();
    }
    
    @Override
    public Map<String, Object> getRtaMonsterStats(int limit, int offset) {
        // 전체 매치 수 조회
        long totalMatches = rtaMapper.getTotalRtaMatches();
        
        // 몬스터별 통계 조회
        List<Map<String, Object>> stats = rtaMapper.getRtaMonsterStats(limit, offset);
        List<Map<String, Object>> duoStats = rtaMapper.getRtaDuoComboStats(50);
        List<Map<String, Object>> trioStats = rtaMapper.getRtaTrioComboStats(50);
        
        // 더 불러올 데이터가 있는지 확인
        boolean hasMore = stats.size() == limit;
        
        Map<String, Object> response = new HashMap<>();
        response.put("stats", stats);
        response.put("duo_stats", duoStats);
        response.put("trio_stats", trioStats);
        response.put("total_matches", totalMatches);
        response.put("has_more", hasMore);
        
        return response;
    }
    
    @Override
    public Map<String, Object> getRtaMonsterDetail(int monsterId) {
        Map<String, Object> response = new HashMap<>();
        
        // 기본 정보
        Map<String, Object> basicInfo = rtaMapper.getRtaMonsterBasicInfo(monsterId);
        response.putAll(basicInfo);
        
        // 강한 상대 (이 몬스터가 상대했을 때 승률이 높은 몬스터)
        List<Map<String, Object>> strongAgainst = rtaMapper.getRtaMonsterStrongAgainst(monsterId);
        response.put("strong_against", strongAgainst);
        
        // 좋은 콤비 (함께 사용했을 때 승률이 높은 몬스터)
        List<Map<String, Object>> goodCombos = rtaMapper.getRtaMonsterGoodCombos(monsterId);
        response.put("good_combos", goodCombos);
        
        // 좋은 3체인 콤비 (2개 몬스터와 함께 사용했을 때 승률이 높은 조합)
        List<Map<String, Object>> goodTripleCombos = rtaMapper.getRtaMonsterGoodTripleCombos(monsterId);
        response.put("good_triple_combos", goodTripleCombos);
        
        // 최근 경기 정보
        List<Map<String, Object>> recentMatches = rtaMapper.getRtaMonsterRecentMatches(monsterId);
        response.put("recent_matches", recentMatches);
        
        return response;
    }

    @Override
    public Map<String, Object> getRtaDashboard() {
        List<Map<String, Object>> daily = rtaMapper.getRtaTierDistributionDaily();
        Map<String, Object> dateRange = rtaMapper.getRtaReplayDateRange();
        List<Map<String, Object>> rankCutoffAnchors = rtaMapper.getRtaRankCutoffAnchors();
        Map<String, Object> response = new HashMap<>();
        response.put("daily_tiers", daily != null ? daily : Collections.emptyList());
        response.put("date_range", dateRange != null ? dateRange : new HashMap<>());
        response.put("rank_cutoff_anchors", rankCutoffAnchors != null ? rankCutoffAnchors : Collections.emptyList());
        return response;
    }

    @Override
    public Map<String, Object> getRtaSummonerRanking(int limit, int offset) {
        int total = rtaMapper.getRtaSummonerRankingCount();
        List<Map<String, Object>> rows = rtaMapper.getRtaSummonerRanking(limit, offset);
        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("rankings", rows != null ? rows : Collections.emptyList());
        return response;
    }

    @Override
    public Map<String, Object> getRtaPlayerSummary(String wizardId) {
        Map<String, Object> response = new HashMap<>();
        if (wizardId == null || wizardId.trim().isEmpty()) {
            response.put("found", false);
            return response;
        }
        Map<String, Object> row = rtaMapper.getRtaPlayerSummary(wizardId.trim());
        if (row == null || row.isEmpty()) {
            response.put("found", false);
            return response;
        }
        response.putAll(row);
        response.put("found", true);
        return response;
    }
}
