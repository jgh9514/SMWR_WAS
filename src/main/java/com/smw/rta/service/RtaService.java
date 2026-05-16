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

    List<Map<String, Object>> getPlayerRtaMatchesCached(String wizardId, int limit, int offset, Long sid);

    /** 두 소환사 간 맞대결 경기 목록 */
    List<Map<String, Object>> getPlayerVsOpponentMatches(String wizardId, String opponentWizardId, int limit, int offset, Long seasonId);

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
    Map<String, Object> getRtaMonsterDetail(int monsterId, Long seasonId, int ratingId);

    /** RTA 몬스터 개요: 개요 통계 + 7일 추이 + 슬롯별 픽 + 장인 랭킹 */
    Map<String, Object> getRtaMonsterOverview(int monsterId, Long seasonId, Integer ratingId, List<Integer> ratingIds);

    /** RTA 몬스터 요약 통계 (Win/Pick/Ban/Lead Rate) */
    Map<String, Object> getRtaMonsterSummaryStats(int monsterId, Long seasonId, Integer ratingId);

    /** RTA 몬스터 7일 추이 */
    Map<String, Object> getRtaMonsterDailyTrend(int monsterId, Long seasonId, Integer ratingId);

    /** RTA 몬스터 슬롯별 픽 통계 */
    Map<String, Object> getRtaMonsterPickSlots(int monsterId, Long seasonId, Integer ratingId);

    /** RTA 몬스터 장인 랭킹 */
    Map<String, Object> getRtaMonsterTopSummoners(int monsterId, Long seasonId);

    /** RTA 대시보드: 일별 티어 + 랭크 컷(한 응답, 호환용) */
    Map<String, Object> getRtaDashboard(Long seasonId);

    /** RTA 대시보드 — 소환사 티어별 분포(일별×티어)만 */
    Map<String, Object> getRtaDashboardTierDistribution(Long seasonId);

    /** RTA 대시보드 — 랭크 컷 6개 시점(현재·3h·6h·12h·3d·7d) */
    Map<String, Object> getRtaDashboardRankCutoff(Long seasonId);

    /** RTA 랭크 컷 상세 — 시즌 전체 일별 히스토리 */
    Map<String, Object> getRtaRankCutDetail(Long seasonId);

    /**
     * 메인/대시보드 4패널(솔·듀·트·소환사 랭킹)을 한 응답으로 — 내부는 가상 스레드로 4쿼리 병렬.
     */
    Map<String, Object> getRtaDashboardLinkPreview(Long seasonId, int previewLimit);

    /** 대시보드 프리뷰 — 솔로 TOP N (스냅 테이블 전용) */
    Map<String, Object> getDashboardPreviewSolo(Long seasonId, int limit);

    /** 대시보드 프리뷰 — 듀오 TOP N (스냅 테이블 전용) */
    Map<String, Object> getDashboardPreviewDuo(Long seasonId, int limit);

    /** 대시보드 프리뷰 — 트리오 TOP N (스냅 테이블 전용) */
    Map<String, Object> getDashboardPreviewTrio(Long seasonId, int limit);

    /**
     * RTA 소환사 랭킹
     *
     * @param countryFilter 국가 코드(2자); null/빈값이면 전체
     */
    Map<String, Object> getRtaSummonerRanking(int limit, int offset, Long seasonId, String countryFilter);

    /**
     * {@code rta_season.season_id} 한 건 조회 — 짧은 TTL 캐시.
     */
    Map<String, Object> getRtaSeasonBoundsRowByIdCached(Long seasonId);

    /** RTA 소환사 검색 (닉네임 부분 일치·위자드 ID 일치) */
    Map<String, Object> searchRtaSummoners(String query, Long seasonId);

    /** RTA 소환사 요약 (닉네임·점수·순위·승률 등) */
    Map<String, Object> getRtaPlayerSummary(String wizardId, Long seasonId);

    /**
     * RTA 소환사별 몬스터 사용(픽/밴/승/선첫비밴/보유) — 배치
     * {@code rta_agg_summoner_monster_snap}, 분모 {@code rta_agg_summoner_season_fight_snap}.
     */
    Map<String, Object> getRtaPlayerMonsterUsage(String wizardId, Long seasonId);

    /**
     * 시즌×소환사×몬스터별 드래프트 슬롯 묶음(1·2–3…) 픽 분포 및 구간 승률 — 배치 {@code rta_agg_summoner_pick_turn_snap} 롤업.
     */
    Map<String, Object> getRtaPlayerMonsterPickBreakdown(String wizardId, Long seasonId, int unitMasterId);

    /** 소환사 x 몬스터 특정 픽 슬롯 경기 목록 (teamSide 1=선픽/2=후픽, pickSlotNo 1~5). */
    Map<String, Object> getRtaPlayerMonsterPickSlotMatches(String wizardId, Long seasonId,
            int unitMasterId, int teamSide, int pickSlotNo, int limit);

    /**
     * RTA 소환사 보유 몬스터(박스) — 배치 {@code rta_agg_summoner_owned_box_snap}.
     * 수집 리플레이에서 해당 소환사가 픽·밴으로 노출한 몬스터 DISTINCT(RTA 무거운 스냅 Job과 동일 주기 재적재).
     */
    Map<String, Object> getRtaPlayerOwnedBox(String wizardId);

    /** 시즌 전체 상대(위자드)별 전적 — {@code rta_agg_summoner_opponent_h2h_snap}만 조회(배치 적재) */
    Map<String, Object> getRtaPlayerOpponentHeadToHead(String wizardId, Long seasonId, int limit, int offset);

    /** 등록된 RTA 시즌 목록 */
    Map<String, Object> getRtaSeasons();

    /** DB 등록 공식 티어 규칙 참고 */
    List<Map<String, Object>> listRtaRatingGradeReference(long seasonId);

    /** 특정 몬스터가 픽·밴된 최근 N경기 */
    Map<String, Object> getMonsterRecentMatches(int monsterId, Long seasonId, int limit, Long ratingId, List<Long> ratingIds);

    /**
     * seasonId resolve — null이면 현재 활성 시즌의 season_id. 실패 시 null.
     */
    Long resolveSeasonId(Long seasonId);
}
