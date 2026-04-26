package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.cache.RtaRedisCacheWarmup;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * {@code rta_agg_monster_stats_tier_top_snap} 시즌 전체 합산 솔/듀/트 상위 100 재적재(티어 컬럼 없음) 및
 * {@code rtaMonster} Redis/Caffeine 무효화(필요 시 워밍업). 시너지·랭킹 Job 과 분리해 1시간 주기 권장.
 */
@DisallowConcurrentExecution
public class RtaMonsterStatsTierTopSnapJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		addLog("--- RTA 몬스터 솔/듀/트 티어별 상위 100 스냅 재적재 ---");
		RtaBatchAggregationService.MonsterStatsTierTopSnapRebuildResult tierTop = aggregationService.rebuildMonsterStatsTierTopSnap(rtaMapper);
		addLog("티어 top 스냅 INSERT 합계(솔+듀+트): %d", tierTop.totalInserts());

		rtaCacheEvictor.evictRtaMonsterReadCache();
		addLog("RTA rtaMonster 캐시 무효화(몬스터 전용 매니저)");

		applicationContext.getBeanProvider(RtaRedisCacheWarmup.class).ifAvailable(warmup -> {
			warmup.warmMonsterCachesAfterTierSnapJob();
			addLog("Redis 몬스터 캐시 워밍업");
		});
	}

	@Override
	protected String getBatchName() {
		return "RTA 몬스터 티어별 상위100 스냅·캐시";
	}
}
