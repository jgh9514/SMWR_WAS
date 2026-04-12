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

    /** 소환사 랭킹 API·화면: 상위 N명 풀 (글로벌 순위·국가 필터 기준) */
    private static final int RTA_SUMMONER_RANKING_MAX_ROWS = 500;

    /** 소환사 랭킹: 페이지당 행 수 (API·화면 공통) */
    private static final int RTA_SUMMONER_RANKING_PAGE_SIZE = 50;

    /** 소환사 랭킹: 최대 페이지 수 (50×10=500) */
    private static final int RTA_SUMMONER_RANKING_MAX_PAGES = 10;

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

    /**
     * 클라이언트가 {@code seasonId}(PK)를 넘기면 해당 행만 조회하고, 아니면 시즌 코드(또는 기본 시즌)로
     * {@code rta_season}에서 {@code season_id}·표시용 코드·기간을 맞춘다.
     */
    private ResolvedSeason resolveSeason(String seasonCode, Long seasonId) {
        if (seasonId != null) {
            Map<String, Object> row = rtaServiceSelf.getRtaSeasonBoundsRowByIdCached(seasonId);
            if (row.isEmpty()) {
                return new ResolvedSeason(null, null, null, null);
            }
            return resolvedSeasonFromBoundsRow(row);
        }
        String code = seasonCode != null ? seasonCode.trim() : "";
        if (code.isEmpty()) {
            code = normalizeSeasonCodeOrEmpty(rtaMapper.selectDefaultSeasonCodeForNow());
        }
        Map<String, Object> row = !code.isEmpty()
                ? rtaServiceSelf.getRtaSeasonBoundsRowCached(code)
                : Collections.emptyMap();
        if (row.isEmpty()) {
            String fb = normalizeSeasonCodeOrEmpty(rtaMapper.selectDefaultSeasonCodeForNow());
            if (!fb.isEmpty() && !fb.equals(code)) {
                code = fb;
                row = rtaServiceSelf.getRtaSeasonBoundsRowCached(code);
            }
        }
        if (row.isEmpty()) {
            return new ResolvedSeason(null, null, null, null);
        }
        return resolvedSeasonFromBoundsRow(row);
    }

    private ResolvedSeason resolvedSeasonFromBoundsRow(Map<String, Object> row) {
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
        String c = sc != null ? String.valueOf(sc) : "";
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

    private static String normalizeSeasonCodeOrEmpty(String c) {
        return c != null ? c.trim() : "";
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "shortLivedCacheManager", key = "'rsb_' + #seasonCode")
    public Map<String, Object> getRtaSeasonBoundsRowCached(String seasonCode) {
        String c = normalizeSeasonCodeOrEmpty(seasonCode);
        if (c.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> row = rtaMapper.selectRtaSeasonBounds(c);
        return row != null && !row.isEmpty() ? row : Collections.emptyMap();
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "shortLivedCacheManager", key = "'rsbi_' + #seasonId")
    public Map<String, Object> getRtaSeasonBoundsRowByIdCached(Long seasonId) {
        if (seasonId == null || seasonId < 1L) {
            return Collections.emptyMap();
        }
        Map<String, Object> row = rtaMapper.selectRtaSeasonBoundsBySeasonId(seasonId.longValue());
        return row != null && !row.isEmpty() ? row : Collections.emptyMap();
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "shortLivedCacheManager",
            key = "'sr_tot_' + #aggKey + '_' + (#countryForMapper != null ? #countryForMapper : 'all') + '_' + #seasonId")
    public int getRtaSummonerRankingTotalBounded(String aggKey, String countryForMapper, Long seasonId) {
        if (aggKey == null || aggKey.isEmpty() || seasonId == null) {
            return 0;
        }
        int raw = rtaMapper.getRtaSummonerRankingAggCount(aggKey, countryForMapper, seasonId);
        return Math.min(raw, RTA_SUMMONER_RANKING_MAX_ROWS);
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
            key = "'m_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + #limit + '_' + #offset + '_' + (#tierKey != null ? #tierKey : 'all')")
    public List<Map<String, Object>> getRtaMatches(int limit, int offset, String seasonCode, Long seasonId,
            String tierKey) {
        if (limit > 0 && offset >= (long) RTA_MATCH_LIST_MAX_PAGES * limit) {
            return Collections.emptyList();
        }
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        String tk = RtaTierKeyUtil.normalize(tierKey);
        return rtaMapper.getRtaMatches(limit, offset, se.seasonId, tk);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'p_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + #wizardId + '_' + #limit + '_' + #offset")
    public List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, String seasonCode,
            Long seasonId) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        return rtaMapper.getPlayerRtaMatches(wizardId, limit, offset, se.seasonId);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'today_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + (#tierKey != null ? #tierKey : 'all')")
    public int countTodayRtaMatches(String seasonCode, Long seasonId, String tierKey) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        String tk = RtaTierKeyUtil.normalize(tierKey);
        return rtaMapper.getTodayRtaMatches(se.seasonId, tk);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'week_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + (#tierKey != null ? #tierKey : 'all')")
    public int countWeeklyRtaMatches(String seasonCode, Long seasonId, String tierKey) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        String tk = RtaTierKeyUtil.normalize(tierKey);
        return rtaMapper.getWeeklyRtaMatches(se.seasonId, tk);
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'stats_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + (#tierKey != null ? #tierKey : 'all')")
    public Object getRtaStats(String seasonCode, Long seasonId, String tierKey) {
        Map<String, Object> stats = new HashMap<>();

        int todayMatches = rtaServiceSelf.countTodayRtaMatches(seasonCode, seasonId, tierKey);
        stats.put("todayMatches", todayMatches);

        int weeklyMatches = rtaServiceSelf.countWeeklyRtaMatches(seasonCode, seasonId, tierKey);
        stats.put("weeklyMatches", weeklyMatches);

        return stats;
    }

    @Override
    @Cacheable(cacheNames = "rtaMatchList", cacheManager = "rtaListReadCacheManager",
            key = "'page_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + #limit + '_' + #offset + '_' + (#tierKey != null ? #tierKey : 'all')")
    public Map<String, Object> getRtaListPage(int limit, int offset, String seasonCode, Long seasonId, String tierKey) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        String tk = RtaTierKeyUtil.normalize(tierKey);
        List<Map<String, Object>> matches;
        if (limit > 0 && offset >= (long) RTA_MATCH_LIST_MAX_PAGES * limit) {
            matches = Collections.emptyList();
        } else {
            matches = rtaMapper.getRtaMatches(limit, offset, se.seasonId, tk);
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
            key = "'ms_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + #pageSize + '_' + #statsOffset + '_' + #duoOffset + '_' + #trioOffset + '_' + (#tierKey != null ? #tierKey : 'all')")
    public Map<String, Object> getRtaMonsterStats(int pageSize, int statsOffset, int duoOffset, int trioOffset,
            String seasonCode, Long seasonId, String tierKey) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        String aggKey = se.code != null ? se.code.trim() : "";
        String tk = tierKey != null ? tierKey.trim() : "";
        String tierParam = tk.isEmpty() ? null : tk;

        long totalMatches = 0L;
        List<Map<String, Object>> stats = Collections.emptyList();
        List<Map<String, Object>> duoStats = Collections.emptyList();
        List<Map<String, Object>> trioStats = Collections.emptyList();
        long statsTotal = 0L;
        long duoTotal = 0L;
        long trioTotal = 0L;

        if (!aggKey.isEmpty()) {
            Long tm = rtaMapper.getRtaMonsterStatsTotalFromAgg(aggKey);
            totalMatches = tm != null ? tm.longValue() : 0L;

            Long cntAgg = rtaMapper.countRtaMonsterStatsFromAgg(aggKey, tierParam);
            stats = rtaMapper.getRtaMonsterStatsFromAgg(pageSize, statsOffset, aggKey, tierParam);
            boolean usedLive = false;
            if ((stats == null || stats.isEmpty()) && se.seasonId != null && tierParam == null) {
                stats = rtaMapper.getRtaMonsterStatsLive(pageSize, statsOffset, aggKey, se.seasonId);
                usedLive = true;
            }
            if (usedLive) {
                Long cLive = rtaMapper.countRtaMonsterStatsLive(aggKey, se.seasonId);
                statsTotal = cLive != null ? cLive.longValue() : 0L;
            } else {
                statsTotal = cntAgg != null ? cntAgg.longValue() : 0L;
            }

            Long dCnt = rtaMapper.countRtaDuoComboStatsFromAgg(aggKey, tierParam);
            duoTotal = dCnt != null ? dCnt.longValue() : 0L;
            duoStats = rtaMapper.getRtaDuoComboStatsFromAgg(pageSize, duoOffset, aggKey, tierParam);

            Long tCnt = rtaMapper.countRtaTrioComboStatsFromAgg(aggKey, tierParam);
            trioTotal = tCnt != null ? tCnt.longValue() : 0L;
            trioStats = rtaMapper.getRtaTrioComboStatsFromAgg(pageSize, trioOffset, aggKey, tierParam);
        }

        int statN = stats != null ? stats.size() : 0;
        boolean hasMore = statN > 0 && (long) statsOffset + statN < statsTotal;

        Map<String, Object> response = new HashMap<>();
        response.put("stats", stats != null ? stats : Collections.emptyList());
        response.put("duo_stats", duoStats != null ? duoStats : Collections.emptyList());
        response.put("trio_stats", trioStats != null ? trioStats : Collections.emptyList());
        response.put("total_matches", totalMatches);
        response.put("stats_total", statsTotal);
        response.put("duo_total", duoTotal);
        response.put("trio_total", trioTotal);
        response.put("limit", pageSize);
        response.put("stats_offset", statsOffset);
        response.put("duo_offset", duoOffset);
        response.put("trio_offset", trioOffset);
        response.put("has_more", hasMore);
        response.put("seasonCode", se.code);
        response.put("tierKey", tierParam);

        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaMonster", cacheManager = "shortLivedCacheManager",
            key = "'md_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + #monsterId")
    public Map<String, Object> getRtaMonsterDetail(int monsterId, String seasonCode, Long seasonId) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        Map<String, Object> response = new HashMap<>();

        Map<String, Object> basicInfo = rtaMapper.getRtaMonsterBasicInfo(monsterId, se.seasonId);
        response.putAll(basicInfo);

        List<Map<String, Object>> strongAgainst = rtaMapper.getRtaMonsterStrongAgainst(monsterId, se.seasonId);
        response.put("strong_against", strongAgainst);

        List<Map<String, Object>> goodCombos = rtaMapper.getRtaMonsterGoodCombos(monsterId, se.seasonId);
        response.put("good_combos", goodCombos);

        List<Map<String, Object>> goodTripleCombos = rtaMapper.getRtaMonsterGoodTripleCombos(monsterId, se.seasonId);
        response.put("good_triple_combos", goodTripleCombos);

        List<Map<String, Object>> recentMatches = rtaMapper.getRtaMonsterRecentMatches(monsterId, se.seasonId);
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
    public Map<String, Object> getRtaDashboard(String seasonCode, Long seasonId) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        Map<String, Object> tierPart = rtaDashboardTierCacheService.getTierPart(se.code);
        List<Map<String, Object>> rankCutoffAnchors = rtaRankCutoffLiveCacheService.getAnchors(se.code, se.seasonId);

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
     * 소환사 랭킹: 시즌 {@code season_id} 내 특정 {@code rating_id} 행만 대상으로 위자드별 MAX(점수)·MAX(replay_id) 후 상세 조인.
     */
    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "shortLivedCacheManager",
            key = "'sr_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + #limit + '_' + #offset + '_' + (#countryFilter != null ? #countryFilter : 'all')")
    public Map<String, Object> getRtaSummonerRanking(int limit, int offset, String seasonCode, Long seasonId,
            String countryFilter) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        String aggKey = se.code != null ? se.code.trim() : "";
        String cf = countryFilter != null ? countryFilter.trim() : "";
        String countryForMapper = cf.isEmpty() ? null : cf;
        int lim = Math.max(1, Math.min(limit, RTA_SUMMONER_RANKING_PAGE_SIZE));
        int maxOffset = RTA_SUMMONER_RANKING_PAGE_SIZE * (RTA_SUMMONER_RANKING_MAX_PAGES - 1);
        int off = Math.max(0, Math.min(offset, Math.min(maxOffset, RTA_SUMMONER_RANKING_MAX_ROWS - lim)));
        int total = 0;
        List<Map<String, Object>> rows = Collections.emptyList();
        if (!aggKey.isEmpty()) {
            total = rtaServiceSelf.getRtaSummonerRankingTotalBounded(aggKey, countryForMapper, se.seasonId);
            final int fetchLimit = (off < RTA_SUMMONER_RANKING_MAX_ROWS && lim > 0)
                    ? Math.min(lim, RTA_SUMMONER_RANKING_MAX_ROWS - off)
                    : 0;
            CompletableFuture<List<Map<String, Object>>> rowsF = fetchLimit > 0
                    ? CompletableFuture.supplyAsync(
                            () -> rtaMapper.getRtaSummonerRankingFromAgg(
                                    fetchLimit, off, aggKey, countryForMapper, se.seasonId),
                            RTA_LIST_PAGE_ASYNC)
                    : CompletableFuture.completedFuture(Collections.<Map<String, Object>>emptyList());
            rows = rowsF.join();
            if (total == 0) {
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
    public Map<String, Object> searchRtaSummoners(String query, String seasonCode, Long seasonId) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
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
        List<Map<String, Object>> rows = rtaMapper.searchRtaSummonersInAgg(aggKey, q, 20, se.seasonId);
        response.put("results", rows != null ? rows : Collections.emptyList());
        return response;
    }

    @Override
    @Cacheable(cacheNames = "rtaRanking", cacheManager = "shortLivedCacheManager",
            key = "'ps_' + (#seasonId != null ? #seasonId : (#seasonCode != null ? #seasonCode : '_')) + '_' + #wizardId")
    public Map<String, Object> getRtaPlayerSummary(String wizardId, String seasonCode, Long seasonId) {
        ResolvedSeason se = resolveSeason(seasonCode, seasonId);
        Map<String, Object> response = new HashMap<>();
        if (wizardId == null || wizardId.trim().isEmpty()) {
            response.put("found", false);
            return response;
        }
        String wid = wizardId.trim();
        String aggKey = se.code != null ? se.code.trim() : "";
        Map<String, Object> row = null;
        if (!aggKey.isEmpty()) {
            row = rtaMapper.getRtaPlayerSummaryFromAgg(wid, aggKey, se.seasonId);
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
