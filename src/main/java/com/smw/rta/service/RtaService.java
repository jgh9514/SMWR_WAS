package com.smw.rta.service;

import java.util.List;
import java.util.Map;

public interface RtaService {

    /**
     * Get RTA match list
     *
     * @param seasonCode    rta_season.season_code (null/빈값이면 금일 기준 기본 시즌) — {@code seasonId}가 있으면 무시
     * @param seasonId      rta_season.season_id (우선). 있으면 코드로 rta_season 조회 생략
     * @param tierKey       세부 티어 Ch1~G3 — null/빈값이면 전체 (해당 티어 플레이어가 한 명이라도 있는 매치)
     */
    List<Map<String, Object>> getRtaMatches(int limit, int offset, String seasonCode, Long seasonId, String tierKey);

    default List<Map<String, Object>> getRtaMatches(int limit, int offset, String seasonCode) {
        return getRtaMatches(limit, offset, seasonCode, null, null);
    }

    default List<Map<String, Object>> getRtaMatches(int limit, int offset, String seasonCode, String tierKey) {
        return getRtaMatches(limit, offset, seasonCode, null, tierKey);
    }

    /**
     * Get player RTA match list
     */
    List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, String seasonCode,
            Long seasonId);

    default List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, String seasonCode) {
        return getPlayerRtaMatches(wizardId, limit, offset, seasonCode, null);
    }

    /** 오늘(UTC) 매치 건수 — {@code getRtaStats} 등에서 캐시와 함께 사용 */
    int countTodayRtaMatches(String seasonCode, Long seasonId, String tierKey);

    default int countTodayRtaMatches(String seasonCode, String tierKey) {
        return countTodayRtaMatches(seasonCode, null, tierKey);
    }

    /** 이번 주 매치 건수 — {@code getRtaListPage}·stats 에서 캐시와 함께 사용 */
    int countWeeklyRtaMatches(String seasonCode, Long seasonId, String tierKey);

    default int countWeeklyRtaMatches(String seasonCode, String tierKey) {
        return countWeeklyRtaMatches(seasonCode, null, tierKey);
    }

    /**
     * Get RTA statistics
     */
    Object getRtaStats(String seasonCode, Long seasonId, String tierKey);

    default Object getRtaStats(String seasonCode) {
        return getRtaStats(seasonCode, null, null);
    }

    default Object getRtaStats(String seasonCode, String tierKey) {
        return getRtaStats(seasonCode, null, tierKey);
    }

    /**
     * /rta 목록 화면용: 매치 목록 + stats.hasMore(다음 페이지 여부). 전체 건수 COUNT는 하지 않음.
     */
    Map<String, Object> getRtaListPage(int limit, int offset, String seasonCode, Long seasonId, String tierKey);

    default Map<String, Object> getRtaListPage(int limit, int offset, String seasonCode) {
        return getRtaListPage(limit, offset, seasonCode, null, null);
    }

    default Map<String, Object> getRtaListPage(int limit, int offset, String seasonCode, String tierKey) {
        return getRtaListPage(limit, offset, seasonCode, null, tierKey);
    }

    /**
     * Test RTA data
     */
    Map<String, Object> testRtaData();

    /**
     * Get RTA monster statistics (솔로·듀오·트리오 각각 pageSize·오프셋 독립)
     *
     * @param pageSize 한 탭당 행 수(기본 20)
     * @param statsOffset 솔로 목록 오프셋
     * @param duoOffset 듀오 목록 오프셋
     * @param trioOffset 트리오 목록 오프셋
     */
    Map<String, Object> getRtaMonsterStats(int pageSize, int statsOffset, int duoOffset, int trioOffset,
            String seasonCode, Long seasonId, String tierKey);

    default Map<String, Object> getRtaMonsterStats(int pageSize, int statsOffset, int duoOffset, int trioOffset,
            String seasonCode, String tierKey) {
        return getRtaMonsterStats(pageSize, statsOffset, duoOffset, trioOffset, seasonCode, null, tierKey);
    }

    /**
     * Get RTA monster detail information
     */
    Map<String, Object> getRtaMonsterDetail(int monsterId, String seasonCode, Long seasonId);

    default Map<String, Object> getRtaMonsterDetail(int monsterId, String seasonCode) {
        return getRtaMonsterDetail(monsterId, seasonCode, null);
    }

    /**
     * RTA 대시보드: 일별 티어 집계 + 날짜 범위 (클라이언트에서 기간 필터)
     */
    Map<String, Object> getRtaDashboard(String seasonCode, Long seasonId);

    default Map<String, Object> getRtaDashboard(String seasonCode) {
        return getRtaDashboard(seasonCode, null);
    }

    /**
     * RTA 소환사 랭킹 (수집 리플레이 중 소환사별 최신 경기 점수 기준)
     *
     * @param countryFilter 국가 코드(2자) 또는 미상 표시 {@code —}; null/빈값이면 전체
     */
    Map<String, Object> getRtaSummonerRanking(int limit, int offset, String seasonCode, Long seasonId,
            String countryFilter);

    default Map<String, Object> getRtaSummonerRanking(int limit, int offset, String seasonCode, String countryFilter) {
        return getRtaSummonerRanking(limit, offset, seasonCode, null, countryFilter);
    }

    /**
     * 소환사 랭킹 총 건수(노출 상한 적용). 시즌·국가·{@code season_id} 기준이며 페이지(offset)와 무관 —
     * {@link #getRtaSummonerRanking}에서 별도 캐시 키로 호출해 페이지 이동 시 COUNT 반복을 막는다.
     */
    int getRtaSummonerRankingTotalBounded(String aggKey, String countryForMapper, Long seasonId);

    /**
     * {@code rta_season.season_code} 한 건 조회 — {@code season_id}·기간 등 resolve 용. 짧은 TTL 캐시(동일 코드 반복 조회 방지).
     */
    Map<String, Object> getRtaSeasonBoundsRowCached(String seasonCode);

    /**
     * {@code rta_season.season_id} 한 건 조회 — 클라이언트가 PK를 넘길 때 코드 조회 생략. 짧은 TTL 캐시.
     */
    Map<String, Object> getRtaSeasonBoundsRowByIdCached(Long seasonId);

    /** RTA 소환사 검색 (집계 랭킹 기준, 닉네임 부분 일치·위자드 ID 일치) */
    Map<String, Object> searchRtaSummoners(String query, String seasonCode, Long seasonId);

    default Map<String, Object> searchRtaSummoners(String query, String seasonCode) {
        return searchRtaSummoners(query, seasonCode, null);
    }

    /** RTA 소환사 요약 (닉네임·점수·순위·승률 등) */
    Map<String, Object> getRtaPlayerSummary(String wizardId, String seasonCode, Long seasonId);

    default Map<String, Object> getRtaPlayerSummary(String wizardId, String seasonCode) {
        return getRtaPlayerSummary(wizardId, seasonCode, null);
    }

    /** 등록된 RTA 시즌 목록 (기간·코드·표시명) */
    Map<String, Object> getRtaSeasons();

    /** DB 등록 공식 티어 규칙 참고 (rta_rating_grade) */
    List<Map<String, Object>> listRtaRatingGradeReference();
}
