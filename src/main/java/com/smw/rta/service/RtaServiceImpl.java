package com.smw.rta.service;

import com.smw.rta.mapper.RtaMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Primary
public class RtaServiceImpl implements RtaService {

    /** /rta 매치 목록과 동일: 요청 limit를 페이지 크기로 볼 때 11페이지 이상이면(offset >= 10*limit) DB 미조회 */
    private static final int RTA_MATCH_LIST_MAX_PAGES = 10;

    /** 소환사 랭킹 API·화면: 상위 N위까지만 노출 (집계 테이블 전체 행 수와 무관) */
    private static final int RTA_SUMMONER_RANKING_MAX_ROWS = 500;

    @Autowired
    private RtaMapper rtaMapper;

    private static final class ResolvedSeason {
        final String code;
        final Timestamp start;
        final Timestamp end;

        ResolvedSeason(String code, Timestamp start, Timestamp end) {
            this.code = code;
            this.start = start;
            this.end = end;
        }
    }

    private ResolvedSeason resolveSeason(String seasonCode) {
        String code = seasonCode != null ? seasonCode.trim() : "";
        if (code.isEmpty()) {
            code = rtaMapper.selectDefaultSeasonCodeForNow();
        }
        Map<String, Object> row = code != null && !code.isEmpty() ? rtaMapper.selectRtaSeasonBounds(code) : null;
        if (row == null || row.isEmpty()) {
            code = rtaMapper.selectDefaultSeasonCodeForNow();
            row = rtaMapper.selectRtaSeasonBounds(code);
        }
        if (row == null || row.isEmpty()) {
            return new ResolvedSeason(null, null, null);
        }
        Object s = row.get("startAt");
        if (s == null) {
            s = row.get("start_at");
        }
        Object e = row.get("endAt");
        if (e == null) {
            e = row.get("end_at");
        }
        Timestamp start = toTimestamp(s);
        Timestamp end = toTimestamp(e);
        Object sc = row.get("seasonCode");
        if (sc == null) {
            sc = row.get("season_code");
        }
        String c = sc != null ? String.valueOf(sc) : code;
        return new ResolvedSeason(c, start, end);
    }

    private static Timestamp toTimestamp(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Timestamp) {
            return (Timestamp) o;
        }
        if (o instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) o).getTime());
        }
        return null;
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "shortLivedCacheManager",
            key = "'m_' + #seasonCode + '_' + #limit + '_' + #offset")
    public List<Map<String, Object>> getRtaMatches(int limit, int offset, String seasonCode) {
        if (limit > 0 && offset >= (long) RTA_MATCH_LIST_MAX_PAGES * limit) {
            return Collections.emptyList();
        }
        ResolvedSeason se = resolveSeason(seasonCode);
        return rtaMapper.getRtaMatches(limit, offset, se.start, se.end);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "shortLivedCacheManager",
            key = "'p_' + #seasonCode + '_' + #wizardId + '_' + #limit + '_' + #offset")
    public List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        return rtaMapper.getPlayerRtaMatches(wizardId, limit, offset, se.start, se.end);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "shortLivedCacheManager",
            key = "'cnt_' + #seasonCode")
    public long getRtaMatchesCount(String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        return rtaMapper.getTotalRtaMatches(se.start, se.end);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "shortLivedCacheManager",
            key = "'stats_' + #seasonCode")
    public Object getRtaStats(String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        Map<String, Object> stats = new HashMap<>();

        int totalMatches = rtaMapper.getTotalRtaMatches(se.start, se.end);
        stats.put("totalMatches", totalMatches);

        int todayMatches = rtaMapper.getTodayRtaMatches(se.start, se.end);
        stats.put("todayMatches", todayMatches);

        int weeklyMatches = rtaMapper.getWeeklyRtaMatches(se.start, se.end);
        stats.put("weeklyMatches", weeklyMatches);

        return stats;
    }

    @Override
    public Map<String, Object> testRtaData() {
        return rtaMapper.testRtaData();
    }

    @Override
    @Cacheable(cacheNames = "rtaMonster", cacheManager = "shortLivedCacheManager",
            key = "'ms_' + #seasonCode + '_' + #limit + '_' + #offset")
    public Map<String, Object> getRtaMonsterStats(int limit, int offset, String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        String aggKey = se.code != null ? se.code.trim() : "";

        long totalMatches = 0L;
        List<Map<String, Object>> stats = Collections.emptyList();
        List<Map<String, Object>> duoStats = Collections.emptyList();
        List<Map<String, Object>> trioStats = Collections.emptyList();

        if (!aggKey.isEmpty()) {
            Long tm = rtaMapper.getRtaMonsterStatsTotalFromAgg(aggKey);
            totalMatches = tm != null ? tm.longValue() : 0L;
            stats = rtaMapper.getRtaMonsterStatsFromAgg(limit, offset, aggKey);
            duoStats = rtaMapper.getRtaDuoComboStatsFromAgg(50, aggKey);
            trioStats = rtaMapper.getRtaTrioComboStatsFromAgg(50, aggKey);
        }

        boolean hasMore = stats.size() == limit;

        Map<String, Object> response = new HashMap<>();
        response.put("stats", stats != null ? stats : Collections.emptyList());
        response.put("duo_stats", duoStats != null ? duoStats : Collections.emptyList());
        response.put("trio_stats", trioStats != null ? trioStats : Collections.emptyList());
        response.put("total_matches", totalMatches);
        response.put("has_more", hasMore);
        response.put("seasonCode", se.code);

        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaMonster", cacheManager = "shortLivedCacheManager",
            key = "'md_' + #seasonCode + '_' + #monsterId")
    public Map<String, Object> getRtaMonsterDetail(int monsterId, String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        Map<String, Object> response = new HashMap<>();

        Map<String, Object> basicInfo = rtaMapper.getRtaMonsterBasicInfo(monsterId, se.start, se.end);
        response.putAll(basicInfo);

        List<Map<String, Object>> strongAgainst = rtaMapper.getRtaMonsterStrongAgainst(monsterId, se.start, se.end);
        response.put("strong_against", strongAgainst);

        List<Map<String, Object>> goodCombos = rtaMapper.getRtaMonsterGoodCombos(monsterId, se.start, se.end);
        response.put("good_combos", goodCombos);

        List<Map<String, Object>> goodTripleCombos = rtaMapper.getRtaMonsterGoodTripleCombos(monsterId, se.start, se.end);
        response.put("good_triple_combos", goodTripleCombos);

        List<Map<String, Object>> recentMatches = rtaMapper.getRtaMonsterRecentMatches(monsterId, se.start, se.end);
        response.put("recent_matches", recentMatches);
        response.put("seasonCode", se.code);

        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaDashboard", cacheManager = "shortLivedCacheManager",
            key = "'dash_' + #seasonCode")
    public Map<String, Object> getRtaDashboard(String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);

        List<Map<String, Object>> daily = rtaMapper.getRtaTierDistributionDailyFromAgg(se.start, se.end);
        Map<String, Object> dateRange = rtaMapper.getRtaReplayDateRangeFromAgg(se.start, se.end);
        List<Map<String, Object>> rankCutoffAnchors = rtaMapper.getRtaRankCutoffAnchorsFromAgg();

        Map<String, Object> response = new HashMap<>();
        response.put("daily_tiers", daily != null ? daily : Collections.emptyList());
        response.put("date_range", dateRange != null ? dateRange : new HashMap<>());
        response.put("rank_cutoff_anchors", rankCutoffAnchors != null ? rankCutoffAnchors : Collections.emptyList());
        response.put("seasonCode", se.code);
        return response;
    }

    /**
     * 소환사 랭킹: 사용자 요청 시에는 항상 집계 테이블 {@code rta_summoner_ranking_agg}만 조회한다.
     * (원장 기준 동적 쿼리 {@code getRtaSummonerRanking}은 배치 적재용 SQL에만 존재하며 여기서 호출하지 않음.)
     */
    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "shortLivedCacheManager",
            key = "'sr_' + #seasonCode + '_' + #limit + '_' + #offset + '_' + (#countryFilter != null ? #countryFilter : 'all')")
    public Map<String, Object> getRtaSummonerRanking(int limit, int offset, String seasonCode, String countryFilter) {
        ResolvedSeason se = resolveSeason(seasonCode);
        String aggKey = se.code != null ? se.code.trim() : "";
        String cf = countryFilter != null ? countryFilter.trim() : "";
        String countryForMapper = cf.isEmpty() ? null : cf;
        int total = 0;
        List<Map<String, Object>> rows = Collections.emptyList();
        if (!aggKey.isEmpty()) {
            int rawCount = rtaMapper.getRtaSummonerRankingAggCount(aggKey, countryForMapper);
            total = Math.min(rawCount, RTA_SUMMONER_RANKING_MAX_ROWS);
            if (total > 0 && offset < RTA_SUMMONER_RANKING_MAX_ROWS) {
                int fetchLimit = Math.min(limit, RTA_SUMMONER_RANKING_MAX_ROWS - offset);
                if (fetchLimit > 0) {
                    rows = rtaMapper.getRtaSummonerRankingFromAgg(fetchLimit, offset, aggKey, countryForMapper);
                }
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("rankings", rows != null ? rows : Collections.emptyList());
        response.put("seasonCode", se.code);
        if (countryForMapper != null) {
            response.put("countryFilter", countryForMapper);
        }
        return response;
    }

    @Override
    public Map<String, Object> searchRtaSummoners(String query, String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        String aggKey = se.code != null ? se.code.trim() : "";
        Map<String, Object> response = new HashMap<>();
        response.put("seasonCode", se.code);
        if (aggKey.isEmpty() || query == null) {
            response.put("results", Collections.emptyList());
            return response;
        }
        String q = query.trim();
        if (q.length() > 50) {
            q = q.substring(0, 50);
        }
        if (q.isEmpty()) {
            response.put("results", Collections.emptyList());
            return response;
        }
        List<Map<String, Object>> rows = rtaMapper.searchRtaSummonersInAgg(aggKey, q, 20);
        response.put("results", rows != null ? rows : Collections.emptyList());
        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "shortLivedCacheManager",
            key = "'ps_' + #seasonCode + '_' + #wizardId")
    public Map<String, Object> getRtaPlayerSummary(String wizardId, String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        Map<String, Object> response = new HashMap<>();
        if (wizardId == null || wizardId.trim().isEmpty()) {
            response.put("found", false);
            return response;
        }
        String wid = wizardId.trim();
        String aggKey = se.code != null ? se.code.trim() : "";
        Map<String, Object> row = null;
        if (!aggKey.isEmpty()) {
            row = rtaMapper.getRtaPlayerSummaryFromAgg(wid, aggKey, se.start, se.end);
        }
        if (row == null || row.isEmpty()) {
            response.put("found", false);
            return response;
        }
        response.putAll(row);
        response.put("found", true);
        response.put("seasonCode", se.code);
        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaSeasons", cacheManager = "shortLivedCacheManager", key = "'list'")
    public Map<String, Object> getRtaSeasons() {
        Map<String, Object> response = new HashMap<>();
        response.put("seasons", rtaMapper.listRtaSeasons());
        response.put("defaultSeasonCode", rtaMapper.selectDefaultSeasonCodeForNow());
        return response;
    }
}
