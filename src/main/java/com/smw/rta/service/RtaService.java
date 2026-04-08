package com.smw.rta.service;

import java.util.List;
import java.util.Map;

public interface RtaService {
    
    /**
     * Get RTA match list
     * @param seasonCode rta_season.season_code (null/빈값이면 금일 기준 기본 시즌)
     * @param tierKey 세부 티어 Ch1~G3 — null/빈값이면 전체 (해당 티어 플레이어가 한 명이라도 있는 매치)
     */
    List<Map<String, Object>> getRtaMatches(int limit, int offset, String seasonCode, String tierKey);

    default List<Map<String, Object>> getRtaMatches(int limit, int offset, String seasonCode) {
        return getRtaMatches(limit, offset, seasonCode, null);
    }
    
    /**
     * Get player RTA match list
     */
    List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, String seasonCode);
    
    /** 오늘(UTC) 매치 건수 — {@code getRtaStats} 등에서 캐시와 함께 사용 */
    int countTodayRtaMatches(String seasonCode, String tierKey);

    /** 이번 주 매치 건수 — {@code getRtaListPage}·stats 에서 캐시와 함께 사용 */
    int countWeeklyRtaMatches(String seasonCode, String tierKey);
    
    /**
     * Get RTA statistics
     */
    Object getRtaStats(String seasonCode, String tierKey);

    default Object getRtaStats(String seasonCode) {
        return getRtaStats(seasonCode, null);
    }

    /**
     * /rta 목록 화면용: 매치 목록 + stats.hasMore(다음 페이지 여부). 전체 건수 COUNT는 하지 않음.
     */
    Map<String, Object> getRtaListPage(int limit, int offset, String seasonCode, String tierKey);

    default Map<String, Object> getRtaListPage(int limit, int offset, String seasonCode) {
        return getRtaListPage(limit, offset, seasonCode, null);
    }
    
    /**
     * Test RTA data
     */
    Map<String, Object> testRtaData();
    
    /**
     * Get RTA monster statistics
     */
    Map<String, Object> getRtaMonsterStats(int limit, int offset, String seasonCode);
    
    /**
     * Get RTA monster detail information
     */
    Map<String, Object> getRtaMonsterDetail(int monsterId, String seasonCode);

    /**
     * RTA 대시보드: 일별 티어 집계 + 날짜 범위 (클라이언트에서 기간 필터)
     */
    Map<String, Object> getRtaDashboard(String seasonCode);

    /**
     * RTA 소환사 랭킹 (수집 리플레이 중 소환사별 최신 경기 점수 기준)
     *
     * @param countryFilter 국가 코드(2자) 또는 미상 표시 {@code —}; null/빈값이면 전체
     */
    Map<String, Object> getRtaSummonerRanking(int limit, int offset, String seasonCode, String countryFilter);

    /** RTA 소환사 검색 (집계 랭킹 기준, 닉네임 부분 일치·위자드 ID 일치) */
    Map<String, Object> searchRtaSummoners(String query, String seasonCode);

    /** RTA 소환사 요약 (닉네임·점수·순위·승률 등) */
    Map<String, Object> getRtaPlayerSummary(String wizardId, String seasonCode);

    /** 등록된 RTA 시즌 목록 (기간·코드·표시명) */
    Map<String, Object> getRtaSeasons();

    /** DB 등록 공식 티어 규칙 참고 (rta_rating_grade) */
    List<Map<String, Object>> listRtaRatingGradeReference();
}
