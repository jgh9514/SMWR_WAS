package com.smw.rta.cache;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaService;

import lombok.extern.slf4j.Slf4j;

/**
 * 배치가 RTA 캐시를 비운 직후 Redis 에 자주 쓰는 키를 다시 채운다. 화면/API Pod 는 곧바로 Redis 히트를 기대할 수 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "smw.cache.rta", name = "use-redis", havingValue = "true")
public class RtaRedisCacheWarmup {

	@Autowired
	private RtaMapper rtaMapper;

	@Autowired
	private RtaService rtaService;

	public void warmAfterEviction() {
		try {
			rtaService.getRtaSeasons();
			Long sid = rtaMapper.selectDefaultSeasonIdForNow();
			if (sid == null) {
				log.debug("[rta-cache] warmup: no default season, skipped detail keys");
				return;
			}
			rtaService.getRtaSeasonBoundsRowByIdCached(sid);
			rtaService.listRtaRatingGradeReference(sid);
			rtaService.getRtaDashboard(sid);
			rtaService.getRtaSummonerRanking(50, 0, sid, null);
			rtaService.getRtaListPage(20, 0, sid, null, null);
			rtaService.getRtaMonsterStats(30, 0, "solo", sid, null, null);
			rtaService.getRtaMonsterStats(30, 0, "duo", sid, null, null);
			rtaService.getRtaMonsterStats(30, 0, "trio", sid, null, null);
			rtaService.getRtaDashboardLinkPreview(sid, 5);
			rtaService.getRtaStats(sid, null, null);
			log.debug("[rta-cache] Redis warmup finished for seasonId={}", sid);
		} catch (Exception e) {
			log.warn("[rta-cache] Redis warmup after eviction failed (non-fatal): {}", e.getMessage());
		}
	}

	/**
	 * 몬스터 일별 추이·픽 슬롯 캐시 워밍.
	 * daily_snap 테이블의 (unit_master_id, rating_id) 쌍을 순회하며 캐시를 미리 채운다.
	 * RtaMonsterDailySnapJob 완료 후 호출.
	 */
	public void warmMonsterDetailCaches() {
		try {
			Long sid = rtaMapper.selectDefaultSeasonIdForNow();
			if (sid == null) {
				log.debug("[rta-cache] monster detail warmup: no default season, skipped");
				return;
			}
			List<Map<String, Object>> pairs = rtaMapper.selectDistinctMonsterRatingPairsFromDailySnap(sid);
			int ok = 0, fail = 0;
			for (Map<String, Object> pair : pairs) {
				int monsterId = ((Number) pair.get("unit_master_id")).intValue();
				Integer ratingId = ((Number) pair.get("rating_id")).intValue();
				try {
					rtaService.getRtaMonsterDailyTrend(monsterId, sid, ratingId);
					rtaService.getRtaMonsterPickSlots(monsterId, sid, ratingId);
					ok++;
				} catch (Exception e) {
					fail++;
					log.warn("[rta-cache] monster detail warmup fail: monster={} rating={}: {}", monsterId, ratingId, e.getMessage());
				}
			}
			log.debug("[rta-cache] monster detail warmup done: ok={} fail={} seasonId={}", ok, fail, sid);
		} catch (Exception e) {
			log.warn("[rta-cache] monster detail warmup failed (non-fatal): {}", e.getMessage());
		}
	}

	/**
	 * {@link com.smw.monster.batch.RtaMonsterStatsTierTopSnapJob} 전용: 몬스터 통계/링크 프리뷰 JSON 만 Redis에 선채움.
	 */
	public void warmMonsterCachesAfterTierSnapJob() {
		try {
			Long sid = rtaMapper.selectDefaultSeasonIdForNow();
			if (sid == null) {
				log.debug("[rta-cache] tier-snap job warmup: no default season");
				return;
			}
			rtaService.getRtaMonsterStats(30, 0, "solo", sid, null, null);
			rtaService.getRtaMonsterStats(30, 0, "duo", sid, null, null);
			rtaService.getRtaMonsterStats(30, 0, "trio", sid, null, null);
			rtaService.getRtaDashboardLinkPreview(sid, 5);
			log.debug("[rta-cache] tier-snap job Redis warmup for seasonId={}", sid);
		} catch (Exception e) {
			log.warn("[rta-cache] tier-snap job Redis warmup failed (non-fatal): {}", e.getMessage());
		}
	}
}
