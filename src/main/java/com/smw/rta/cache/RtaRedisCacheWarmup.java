package com.smw.rta.cache;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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

	private static final int PLAYER_WARMUP_PAGE_SIZE = 500;

	@Autowired
	private RtaMapper rtaMapper;

	@Autowired
	private RtaService rtaService;

	@Autowired
	@Qualifier("rtaPlayerCacheManager")
	private CacheManager rtaPlayerCacheManager;

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
	 * 전체 소환사 summary 를 Redis 에 직접 적재 (GET 없이 PUT만).
	 * fight_snap 에 행이 있는 소환사 전체를 keyset 페이징으로 순회하며
	 * {@code rtaPlayerData::ps_{seasonId}_{wizardId}} 키로 씀.
	 */
	public void warmAllPlayerSummaries(long seasonId) {
		Cache cache = rtaPlayerCacheManager.getCache("rtaPlayerData");
		if (cache == null) {
			log.warn("[rta-cache] warmAllPlayerSummaries: rtaPlayerData cache not found, skipped");
			return;
		}
		long afterWizardId = 0L;
		int total = 0;
		try {
			while (true) {
				List<Map<String, Object>> page = rtaMapper.selectAllPlayerSummaryPageByFightSnap(
						seasonId, afterWizardId, PLAYER_WARMUP_PAGE_SIZE);
				if (page.isEmpty()) break;
				for (Map<String, Object> row : page) {
					String wizardId = (String) row.get("wizard_id");
					String cacheKey = "ps_" + seasonId + "_" + wizardId;
					cache.put(cacheKey, row);
					afterWizardId = Long.parseLong(wizardId);
				}
				total += page.size();
				if (page.size() < PLAYER_WARMUP_PAGE_SIZE) break;
			}
			log.debug("[rta-cache] warmAllPlayerSummaries done: seasonId={} total={}", seasonId, total);
		} catch (Exception e) {
			log.warn("[rta-cache] warmAllPlayerSummaries failed (non-fatal): seasonId={} total={} err={}",
					seasonId, total, e.getMessage());
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
