package com.smw.rta.service;

import java.util.List;
import java.util.Map;

public interface RtaService {

    /**
     * RTA 매치 목록 (페이지네이션).
     *
     * @param seasonId  rta_season.season_id — null이면 현재 활성 시즌
     * @param ratingId  세부 티어 rating_id — null이면 전체
     */
    List<Map<String, Object>> getRtaMatches(int limit, int offset, Long seasonId, Integer ratingId,
            List<Integer> ratingIds);

    /** 플레이어별 RTA 매치 목록 */
    List<Map<String, Object>> getPlayerRtaMatches(String wizardId, int limit, int offset, Long seasonId);

    /** 오늘(UTC) 매치 건수 */
    int countTodayRtaMatches(Long seasonId, Integer ratingId, List<Integer> ratingIds);

    /** 이번 주 매치 건수 */
    int countWeeklyRtaMatches(Long seasonId, Integer ratingId, List<Integer> ratingIds);

    /** RTA 통계 (오늘·주간 매치 수) */
    Object getRtaStats(Long seasonId, Integer ratingId, List<Integer> ratingIds);

    /** /rta 목록 화면용: 매치 목록 + stats.hasMore */
    Map<String, Object> getRtaListPage(int limit, int offset, Long seasonId, Integer ratingId, List<Integer> ratingIds);

    /** Test RTA data */
    Map<String, Object> testRtaData();

    /**
     * RTA 몬스터별 통계 (솔로·듀오·트리오 각각 pageSize·오프셋 독립).
     * type: "solo" | "duo" | "trio".
     * {@code ratingId} 단일 필터(우선) 또는 {@code ratingIds} 복수(구간 합산). 둘 다 없으면 전체 티어 합산.
     */
    Map<String, Object> getRtaMonsterStats(int pageSize, int offset, String type, Long seasonId, Integer ratingId,
            List<Integer> ratingIds);

    /** RTA 몬스터 상세 */
    Map<String, Object> getRtaMonsterDetail(int monsterId, Long seasonId);

    /** RTA 대시보드: 일별 티어 + 랭크 컷(한 응답, 호환용) */
    Map<String, Object> getRtaDashboard(Long seasonId);

    /** RTA 대시보드 — 소환사 티어별 분포(일별×티어)만 */
    Map<String, Object> getRtaDashboardTierDistribution(Long seasonId);

    /** RTA 대시보드 — 랭크 컷 앵커·스냅샷만 */
    Map<String, Object> getRtaDashboardRankCutoff(Long seasonId);

    /**
     * RTA 소환사 랭킹
     *
     * @param countryFilter 국가 코드(2자); null/빈값이면 전체
     */
    Map<String, Object> getRtaSummonerRanking(int limit, int offset, Long seasonId, String countryFilter);

    /** RTA 소환사 랭킹 풀(최대 500행) — Redis 캐시 후 페이지 슬라이스용 */
    Map<String, Object> getRtaSummonerRankingPool(Long seasonId, String countryFilter);

    /**
     * {@code rta_season.season_id} 한 건 조회 — 짧은 TTL 캐시.
     */
    Map<String, Object> getRtaSeasonBoundsRowByIdCached(Long seasonId);

    /** RTA 소환사 검색 (닉네임 부분 일치·위자드 ID 일치) */
    Map<String, Object> searchRtaSummoners(String query, Long seasonId);

    /** RTA 소환사 요약 (닉네임·점수·순위·승률 등) */
    Map<String, Object> getRtaPlayerSummary(String wizardId, Long seasonId);

    /** 등록된 RTA 시즌 목록 */
    Map<String, Object> getRtaSeasons();

    /** DB 등록 공식 티어 규칙 참고 */
    List<Map<String, Object>> listRtaRatingGradeReference(long seasonId);

    /**
     * seasonId resolve — null이면 현재 활성 시즌의 season_id. 실패 시 null.
     */
    Long resolveSeasonId(Long seasonId);
}
