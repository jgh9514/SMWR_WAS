package com.smw.rta.service;

import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.support.RtaTierKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@Primary
public class RtaServiceImpl implements RtaService {

    /** /rta 매치 목록과 동일: 요청 limit를 페이지 크기로 볼 때 11페이지 이상이면(offset >= 10*limit) DB 미조회 */
    private static final int RTA_MATCH_LIST_MAX_PAGES = 10;

    /** 소환사 랭킹 API·화면: 상위 N위까지만 노출 (집계 테이블 전체 행 수와 무관) */
    private static final int RTA_SUMMONER_RANKING_MAX_ROWS = 500;

    /**
     * getRtaListPage: 목록만 DB 조회 — 전체 건수 COUNT(getTotalRtaMatches)는 부하가 커서 제외하고,
     * stats.hasMore 로 다음 페이지 존재 여부만 반환(클라이언트는 최대 10페이지 상한과 함께 페이지 수 추정).
     */
    private static final Executor RTA_LIST_PAGE_ASYNC = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    private RtaMapper rtaMapper;

    @Autowired
    @Lazy
    private RtaService rtaServiceSelf;

    @Autowired
    private RtaDashboardTierCacheService rtaDashboardTierCacheService;

    @Autowired
    private RtaRankCutoffLiveCacheService rtaRankCutoffLiveCacheService;

    private static final class ResolvedSeason {
        final String code;
        final Timestamp start;
        final Timestamp end;
        /** rta_season.season_id — 목록/카운트에서 m.season_id = ? 로 인덱스 활용 */
        final Long seasonId;

        ResolvedSeason(String code, Timestamp start, Timestamp end, Long seasonId) {
            this.code = code;
            this.start = start;
            this.end = end;
            this.seasonId = seasonId;
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
            return new ResolvedSeason(null, null, null, null);
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
        Long seasonId = null;
        Object sid = row.get("seasonId");
        if (sid == null) {
            sid = row.get("season_id");
        }
        if (sid instanceof Number) {
            seasonId = ((Number) sid).longValue();
        } else if (sid != null) {
            try {
                seasonId = Long.parseLong(String.valueOf(sid).trim());
            } catch (NumberFormatException ignored) {
                // seasonId stays null
            }
        }
        return new ResolvedSeason(c, start, end, seasonId);
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
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'m_' + #seasonCode + '_' + #limit + '_' + #offset + '_' + (#tierKey != null ? #tierKey : 'all')")
    public List<Map<String, Object>> getRtaMatches(int limit, int offset, String seasonCode, String tierKey) {
        if (limit > 0 && offset >= (long) RTA_MATCH_LIST_MAX_PAGES * limit) {
            return Collections.emptyList();
        }
        ResolvedSeason se = resolveSeason(seasonCode);
        String tk = RtaTierKeyUtil.normalize(tierKey);
        return rtaMapper.getRtaMatches(limit, offset, se.start, se.end, se.seasonId, tk);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'p_' + #seasonCode + '_' + #wizardId + '_' + #limit + '_' + #offset")
    public List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        return rtaMapper.getPlayerRtaMatches(wizardId, limit, offset, se.start, se.end, se.seasonId);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'today_' + #seasonCode + '_' + (#tierKey != null ? #tierKey : 'all')")
    public int countTodayRtaMatches(String seasonCode, String tierKey) {
        ResolvedSeason se = resolveSeason(seasonCode);
        String tk = RtaTierKeyUtil.normalize(tierKey);
        return rtaMapper.getTodayRtaMatches(se.start, se.end, se.seasonId, tk);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'week_' + #seasonCode + '_' + (#tierKey != null ? #tierKey : 'all')")
    public int countWeeklyRtaMatches(String seasonCode, String tierKey) {
        ResolvedSeason se = resolveSeason(seasonCode);
        String tk = RtaTierKeyUtil.normalize(tierKey);
        return rtaMapper.getWeeklyRtaMatches(se.start, se.end, se.seasonId, tk);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'stats_' + #seasonCode + '_' + (#tierKey != null ? #tierKey : 'all')")
    public Object getRtaStats(String seasonCode, String tierKey) {
        Map<String, Object> stats = new HashMap<>();

        int todayMatches = rtaServiceSelf.countTodayRtaMatches(seasonCode, tierKey);
        stats.put("todayMatches", todayMatches);

        int weeklyMatches = rtaServiceSelf.countWeeklyRtaMatches(seasonCode, tierKey);
        stats.put("weeklyMatches", weeklyMatches);

        return stats;
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'page_' + #seasonCode + '_' + #limit + '_' + #offset + '_' + (#tierKey != null ? #tierKey : 'all')")
    public Map<String, Object> getRtaListPage(int limit, int offset, String seasonCode, String tierKey) {
        ResolvedSeason se = resolveSeason(seasonCode);
        String tk = RtaTierKeyUtil.normalize(tierKey);
        List<Map<String, Object>> matches;
        if (limit > 0 && offset >= (long) RTA_MATCH_LIST_MAX_PAGES * limit) {
            matches = Collections.emptyList();
        } else {
            matches = rtaMapper.getRtaMatches(limit, offset, se.start, se.end, se.seasonId, tk);
        }
        boolean hasMore = limit > 0
                && matches.size() == limit
                && (long) offset + limit < (long) RTA_MATCH_LIST_MAX_PAGES * limit;

        Map<String, Object> stats = new HashMap<>();
        stats.put("hasMore", hasMore);
        Map<String, Object> out = new HashMap<>();
        out.put("stats", stats);
        out.put("matches", matches);
        return out;
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
            duoStats = rtaMapper.getRtaDuoComboStatsFromAgg(50);
            trioStats = rtaMapper.getRtaTrioComboStatsFromAgg(50);
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

        if (se.code != null && !se.code.isEmpty()) {
            List<Map<String, Object>> counters = rtaMapper.getRtaMonsterCounterMatchups(monsterId, se.code);
            response.put("counter_matchups", counters != null ? counters : Collections.emptyList());
        } else {
            response.put("counter_matchups", Collections.emptyList());
        }
        response.put("seasonCode", se.code);

        return response;
    }

    @Override
    public List<Map<String, Object>> listRtaRatingGradeReference() {
        List<Map<String, Object>> rows = rtaMapper.listRtaRatingGradeReference();
        return rows != null ? rows : Collections.emptyList();
    }

    @Override
    public Map<String, Object> getRtaDashboard(String seasonCode) {
        ResolvedSeason se = resolveSeason(seasonCode);
        Map<String, Object> tierPart = rtaDashboardTierCacheService.getTierPart(seasonCode);
        List<Map<String, Object>> rankCutoffAnchors = rtaRankCutoffLiveCacheService.getAnchors(se.code, se.start, se.end);

        Map<String, Object> response = new HashMap<>();
        response.putAll(tierPart);
        response.put("rank_cutoff_anchors", rankCutoffAnchors);
        if (se.code != null && !se.code.isEmpty()) {
            List<Map<String, Object>> snap = rtaMapper.getRtaSnapshotRankCutLatest(se.code);
            response.put("snapshot_rank_cut", snap != null ? snap : Collections.emptyList());
        } else {
            response.put("snapshot_rank_cut", Collections.emptyList());
        }
        response.put("seasonCode", se.code);
        return response;
    }

    /**
     * 소환사 랭킹: 시즌 구간 내 라이브 집계({@code rta_match} / participant / unit_pick).
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
            CompletableFuture<Integer> countF = CompletableFuture.supplyAsync(
                    () -> rtaMapper.getRtaSummonerRankingAggCount(aggKey, countryForMapper, se.start, se.end),
                    RTA_LIST_PAGE_ASYNC);
            final int fetchLimit = (offset < RTA_SUMMONER_RANKING_MAX_ROWS && limit > 0)
                    ? Math.min(limit, RTA_SUMMONER_RANKING_MAX_ROWS - offset)
                    : 0;
            CompletableFuture<List<Map<String, Object>>> rowsF = fetchLimit > 0
                    ? CompletableFuture.supplyAsync(
                            () -> rtaMapper.getRtaSummonerRankingFromAgg(
                                    fetchLimit, offset, aggKey, countryForMapper, se.start, se.end),
                            RTA_LIST_PAGE_ASYNC)
                    : CompletableFuture.completedFuture(Collections.<Map<String, Object>>emptyList());
            CompletableFuture.allOf(countF, rowsF).join();
            int rawCount = countF.join();
            total = Math.min(rawCount, RTA_SUMMONER_RANKING_MAX_ROWS);
            rows = rowsF.join();
            if (rawCount == 0) {
                rows = Collections.emptyList();
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
        List<Map<String, Object>> rows = rtaMapper.searchRtaSummonersInAgg(aggKey, q, 20, se.start, se.end);
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
