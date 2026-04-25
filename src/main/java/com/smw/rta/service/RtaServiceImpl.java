package com.smw.rta.service;

import com.smw.rta.mapper.RtaMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    /**
     * 메인 4패널 link-preview: 솔/듀/트/랭킹 4쿼리를 한 요청에서 병렬(블로킹 JDBC는 가상 스레드).
     */
    private static final Executor RTA_LINK_PREVIEW_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /** /rta 매치 목록: 요청 limit를 페이지 크기로 볼 때 11페이지 이상이면 DB 미조회 */
    private static final int RTA_MATCH_LIST_MAX_PAGES = 10;

    /** 소환사 랭킹 API·화면: 상위 N명 풀 */
    private static final int RTA_SUMMONER_RANKING_MAX_ROWS = 500;

    /** 소환사 랭킹: 페이지당 행 수 */
    private static final int RTA_SUMMONER_RANKING_PAGE_SIZE = 50;

    /** 소환사 랭킹: 최대 페이지 수 (50×10=500) */
    private static final int RTA_SUMMONER_RANKING_MAX_PAGES = 10;

    /** 몬스터 통계: 집계 최소 픽 수 임계값 */
    @Value("${smw.rta.monster-stats.min-pick-count:10}")
    private int monsterStatsMinPickCount;

    @Autowired
    private RtaMapper rtaMapper;

    @Autowired
    @Lazy
    private RtaService rtaServiceSelf;

    @Autowired
    private RtaDashboardTierCacheService rtaDashboardTierCacheService;

    @Autowired
    private RtaDashboardRankCutoffCacheService rtaDashboardRankCutoffCacheService;

    /**
     * seasonId가 null이면 현재 활성 시즌 ID를 조회. 여전히 null이면 null 반환.
     */
    private Long doResolveSeasonId(Long seasonId) {
        if (seasonId != null) {
            return seasonId;
        }
        return rtaMapper.selectDefaultSeasonIdForNow();
    }

    private static String normalizeCountryFilter(String countryFilter) {
        if (countryFilter == null) {
            return null;
        }
        String cf = countryFilter.trim();
        return cf.isEmpty() ? null : cf;
    }

    private static Map<String, Object> buildSummonerRankingResponse(
            List<Map<String, Object>> raw,
            Long seasonId,
            String countryFilter) {
        int total = 0;
        List<Map<String, Object>> rows = Collections.emptyList();
        if (raw != null && !raw.isEmpty()) {
            Object pt = raw.get(0).get("rankingPoolTotal");
            if (pt instanceof Number) {
                total = Math.min(((Number) pt).intValue(), RTA_SUMMONER_RANKING_MAX_ROWS);
            }
            List<Map<String, Object>> out = new ArrayList<>(raw.size());
            for (Map<String, Object> row : raw) {
                if (row == null) {
                    continue;
                }
                row.remove("rankingPoolTotal");
                if (row.get("wizard_id") != null) {
                    out.add(row);
                }
            }
            rows = out;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("total", total);
        response.put("rankings", rows);
        response.put("seasonId", seasonId);
        if (countryFilter != null) {
            response.put("countryFilter", countryFilter);
        }
        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "rtaShortLivedCacheManager", key = "'rsbi_' + #seasonId")
    public Map<String, Object> getRtaSeasonBoundsRowByIdCached(Long seasonId) {
        if (seasonId == null || seasonId < 1L) {
            return Collections.emptyMap();
        }
        Map<String, Object> row = rtaMapper.selectRtaSeasonBoundsBySeasonId(seasonId.longValue());
        return row != null && !row.isEmpty() ? row : Collections.emptyMap();
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'m_' + #seasonId + '_' + #limit + '_' + #offset + '_' + (#ratingId != null ? #ratingId : 'x') + '_' + (#ratingIds != null && !#ratingIds.isEmpty() ? #ratingIds.toString() : 'all')")
    public List<Map<String, Object>> getRtaMatches(int limit, int offset, Long seasonId, Integer ratingId,
            List<Integer> ratingIds) {
        if (limit > 0 && offset >= (long) RTA_MATCH_LIST_MAX_PAGES * limit) {
            return Collections.emptyList();
        }
        Long sid = doResolveSeasonId(seasonId);
        return rtaMapper.getRtaMatches(limit, offset, sid, ratingId, ratingIds);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'p_' + #seasonId + '_' + #wizardId + '_' + #limit + '_' + #offset")
    public List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, Long seasonId) {
        Long sid = doResolveSeasonId(seasonId);
        return rtaMapper.getPlayerRtaMatches(wizardId, limit, offset, sid);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'today_' + #seasonId + '_' + (#ratingId != null ? #ratingId : 'x') + '_' + (#ratingIds != null && !#ratingIds.isEmpty() ? #ratingIds.toString() : 'all')")
    public int countTodayRtaMatches(Long seasonId, Integer ratingId, List<Integer> ratingIds) {
        Long sid = doResolveSeasonId(seasonId);
        return rtaMapper.getTodayRtaMatches(sid, ratingId, ratingIds);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'week_' + #seasonId + '_' + (#ratingId != null ? #ratingId : 'x') + '_' + (#ratingIds != null && !#ratingIds.isEmpty() ? #ratingIds.toString() : 'all')")
    public int countWeeklyRtaMatches(Long seasonId, Integer ratingId, List<Integer> ratingIds) {
        Long sid = doResolveSeasonId(seasonId);
        return rtaMapper.getWeeklyRtaMatches(sid, ratingId, ratingIds);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'stats_' + #seasonId + '_' + (#ratingId != null ? #ratingId : 'x') + '_' + (#ratingIds != null && !#ratingIds.isEmpty() ? #ratingIds.toString() : 'all')")
    public Object getRtaStats(Long seasonId, Integer ratingId, List<Integer> ratingIds) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("todayMatches", rtaServiceSelf.countTodayRtaMatches(seasonId, ratingId, ratingIds));
        stats.put("weeklyMatches", rtaServiceSelf.countWeeklyRtaMatches(seasonId, ratingId, ratingIds));
        return stats;
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'page_' + #seasonId + '_' + #limit + '_' + #offset + '_' + (#ratingId != null ? #ratingId : 'x') + '_' + (#ratingIds != null && !#ratingIds.isEmpty() ? #ratingIds.toString() : 'all')")
    public Map<String, Object> getRtaListPage(int limit, int offset, Long seasonId, Integer ratingId,
            List<Integer> ratingIds) {
        Long sid = doResolveSeasonId(seasonId);
        List<Map<String, Object>> matches;
        if (limit > 0 && offset >= (long) RTA_MATCH_LIST_MAX_PAGES * limit) {
            matches = Collections.emptyList();
        } else {
            matches = rtaMapper.getRtaMatches(limit, offset, sid, ratingId, ratingIds);
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
    @Cacheable(cacheNames = "rtaMonster", cacheManager = "rtaShortLivedCacheManager",
            key = "'ms_' + #type + '_' + #seasonId + '_' + #pageSize + '_' + #offset + '_' + (#ratingId != null ? #ratingId : 'x') + '_' + (#ratingIds != null && !#ratingIds.isEmpty() ? #ratingIds.toString() : 'all')")
    public Map<String, Object> getRtaMonsterStats(int pageSize, int offset, String type, Long seasonId,
            Integer ratingId, List<Integer> ratingIds) {
        Long sid = doResolveSeasonId(seasonId);
        int fetchSize = pageSize + 1;
        int minPick = monsterStatsMinPickCount;

        List<Map<String, Object>> rows = Collections.emptyList();
        if (sid != null) {
            if ("duo".equals(type)) {
                rows = rtaMapper.getRtaDuoComboStatsFromAgg(fetchSize, offset, sid, ratingId, ratingIds, minPick);
            } else if ("trio".equals(type)) {
                rows = rtaMapper.getRtaTrioComboStatsFromAgg(fetchSize, offset, sid, ratingId, ratingIds, minPick);
            } else {
                rows = rtaMapper.getRtaMonsterStatsFromAgg(fetchSize, offset, sid, ratingId, ratingIds, minPick);
            }
        }

        boolean hasMore = rows.size() > pageSize;
        if (hasMore) rows = rows.subList(0, pageSize);

        Map<String, Object> response = new HashMap<>();
        response.put("rows", rows);
        response.put("has_more", hasMore);
        response.put("type", type);
        response.put("limit", pageSize);
        response.put("offset", offset);
        response.put("seasonId", sid);
        response.put("ratingId", ratingId);
        response.put("ratingIds", ratingIds != null && !ratingIds.isEmpty() ? ratingIds : null);
        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaMonster", cacheManager = "rtaShortLivedCacheManager",
            key = "'md_' + #seasonId + '_' + #monsterId")
    public Map<String, Object> getRtaMonsterDetail(int monsterId, Long seasonId) {
        Long sid = doResolveSeasonId(seasonId);
        Map<String, Object> response = new HashMap<>();

        if (sid == null) {
            response.put("strong_against", Collections.emptyList());
            response.put("good_combos", Collections.emptyList());
            response.put("good_triple_combos", Collections.emptyList());
            response.put("recent_matches", Collections.emptyList());
            response.put("counter_matchups", Collections.emptyList());
            response.put("seasonId", null);
            return response;
        }

        Map<String, Object> basicInfo = rtaMapper.getRtaMonsterBasicInfo(monsterId, sid);
        response.putAll(basicInfo);

        response.put("strong_against", rtaMapper.getRtaMonsterStrongAgainst(monsterId, sid));
        response.put("good_combos", rtaMapper.getRtaMonsterGoodCombos(monsterId, sid));
        response.put("good_triple_combos", rtaMapper.getRtaMonsterGoodTripleCombos(monsterId, sid));
        response.put("recent_matches", rtaMapper.getRtaMonsterRecentMatches(monsterId, sid));

        List<Map<String, Object>> counters = rtaMapper.getRtaMonsterCounterMatchups(monsterId, sid);
        response.put("counter_matchups", counters != null ? counters : Collections.emptyList());
        response.put("seasonId", sid);
        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaSeasons", cacheManager = "rtaShortLivedCacheManager",
            key = "'gradeRules_' + #seasonId")
    public List<Map<String, Object>> listRtaRatingGradeReference(long seasonId) {
        List<Map<String, Object>> rows = rtaMapper.listRtaRatingGradeReference(seasonId);
        return rows != null ? rows : Collections.emptyList();
    }

    @Override
    public Map<String, Object> getRtaDashboard(Long seasonId) {
        Long sid = doResolveSeasonId(seasonId);
        Map<String, Object> response = new HashMap<>();
        response.putAll(rtaDashboardTierCacheService.getTierPart(sid));
        response.putAll(rtaDashboardRankCutoffCacheService.getRankCutoffPart(sid));
        return response;
    }

    @Override
    public Map<String, Object> getRtaDashboardTierDistribution(Long seasonId) {
        Long sid = doResolveSeasonId(seasonId);
        Map<String, Object> m = new HashMap<>(rtaDashboardTierCacheService.getTierPart(sid));
        m.put("seasonId", sid);
        return m;
    }

    @Override
    public Map<String, Object> getRtaDashboardRankCutoff(Long seasonId) {
        Long sid = doResolveSeasonId(seasonId);
        return rtaDashboardRankCutoffCacheService.getRankCutoffPart(sid);
    }

    @Override
    @Cacheable(cacheNames = "rtaMonster", cacheManager = "rtaShortLivedCacheManager",
            key = "'dlp_' + (#seasonId != null ? #seasonId : 'x') + '_' + #previewLimit")
    public Map<String, Object> getRtaDashboardLinkPreview(Long seasonId, int previewLimit) {
        int n = Math.max(1, Math.min(previewLimit, 50));
        Map<String, Object> out = new HashMap<>();
        out.put("previewLimit", n);
        out.put("seasonId", doResolveSeasonId(seasonId));
        try {
            CompletableFuture<Map<String, Object>> fSolo = CompletableFuture.supplyAsync(
                    () -> rtaServiceSelf.getRtaMonsterStats(n, 0, "solo", seasonId, null, null),
                    RTA_LINK_PREVIEW_EXECUTOR);
            CompletableFuture<Map<String, Object>> fDuo = CompletableFuture.supplyAsync(
                    () -> rtaServiceSelf.getRtaMonsterStats(n, 0, "duo", seasonId, null, null),
                    RTA_LINK_PREVIEW_EXECUTOR);
            CompletableFuture<Map<String, Object>> fTrio = CompletableFuture.supplyAsync(
                    () -> rtaServiceSelf.getRtaMonsterStats(n, 0, "trio", seasonId, null, null),
                    RTA_LINK_PREVIEW_EXECUTOR);
            CompletableFuture<Map<String, Object>> fRank = CompletableFuture.supplyAsync(
                    () -> rtaServiceSelf.getRtaSummonerRanking(n, 0, seasonId, null),
                    RTA_LINK_PREVIEW_EXECUTOR);
            CompletableFuture.allOf(fSolo, fDuo, fTrio, fRank).join();
            out.put("solo", fSolo.get());
            out.put("duo", fDuo.get());
            out.put("trio", fTrio.get());
            out.put("summoner_ranking", fRank.get());
        } catch (Exception e) {
            Throwable t = e;
            if (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null) {
                t = e.getCause();
            }
            if (t instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (t instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(t);
        }
        return out;
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "rtaShortLivedCacheManager",
            key = "'sr_' + #seasonId + '_' + #limit + '_' + #offset + '_' + (#countryFilter != null ? #countryFilter : 'all')")
    public Map<String, Object> getRtaSummonerRanking(int limit, int offset, Long seasonId, String countryFilter) {
        Long sid = doResolveSeasonId(seasonId);
        int lim = Math.max(1, Math.min(limit, RTA_SUMMONER_RANKING_PAGE_SIZE));
        int maxOffset = RTA_SUMMONER_RANKING_PAGE_SIZE * (RTA_SUMMONER_RANKING_MAX_PAGES - 1);
        int off = Math.max(0, Math.min(offset, Math.min(maxOffset, RTA_SUMMONER_RANKING_MAX_ROWS - lim)));
        String countryForMapper = normalizeCountryFilter(countryFilter);
        if (sid != null) {
            final int fetchLimit = (off < RTA_SUMMONER_RANKING_MAX_ROWS && lim > 0)
                    ? Math.min(lim, RTA_SUMMONER_RANKING_MAX_ROWS - off)
                    : 0;
            List<Map<String, Object>> raw = rtaMapper.getRtaSummonerRankingFromAgg(fetchLimit, off, sid, countryForMapper);
            return buildSummonerRankingResponse(raw, sid, countryForMapper);
        }
        return buildSummonerRankingResponse(Collections.emptyList(), sid, countryForMapper);
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "rtaShortLivedCacheManager",
            key = "'search_' + #seasonId + '_' + (#query != null ? #query.trim() : '')")
    public Map<String, Object> searchRtaSummoners(String query, Long seasonId) {
        Long sid = doResolveSeasonId(seasonId);
        Map<String, Object> response = new HashMap<>();
        response.put("seasonId", sid);
        if (sid == null || query == null) {
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
        List<Map<String, Object>> rows = rtaMapper.searchRtaSummonersInAgg(q, 20, sid);
        response.put("results", rows != null ? rows : Collections.emptyList());
        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "rtaShortLivedCacheManager",
            key = "'ps_' + #seasonId + '_' + #wizardId")
    public Map<String, Object> getRtaPlayerSummary(String wizardId, Long seasonId) {
        Long sid = doResolveSeasonId(seasonId);
        Map<String, Object> response = new HashMap<>();
        if (wizardId == null || wizardId.trim().isEmpty()) {
            response.put("found", false);
            return response;
        }
        String wid = wizardId.trim();
        Map<String, Object> row = null;
        if (sid != null) {
            row = rtaMapper.getRtaPlayerSummaryFromAgg(wid, sid);
        }
        if (row == null || row.isEmpty()) {
            response.put("found", false);
            return response;
        }
        response.putAll(row);
        response.put("found", true);
        response.put("seasonId", sid);
        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaSeasons", cacheManager = "rtaShortLivedCacheManager", key = "'list'")
    public Map<String, Object> getRtaSeasons() {
        Map<String, Object> response = new HashMap<>();
        response.put("seasons", rtaMapper.listRtaSeasons());
        return response;
    }

    @Override
    public Long resolveSeasonId(Long seasonId) {
        return doResolveSeasonId(seasonId);
    }
}
