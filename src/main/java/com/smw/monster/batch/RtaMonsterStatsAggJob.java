package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * RTA 몬스터 통계(시즌별 픽/승/벤 메타·단일 몬스터)를 재적재한다. 2·3마리 조합은 {@code rta_agg_synergy_combo}(시너지 배치)에서 관리.
 * <p>
 * 운영 스케줄은 {@link RtaUnifiedPipelineAggJob} 로 통합하는 것을 권장한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 비활성화, bat_id 10007).
 */
public class RtaMonsterStatsAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);

		addLog("rta_monster_stats_* TRUNCATE 후 시즌별 재적재");
		RtaBatchAggregationService.MonsterStatsRebuildResult mon = aggregationService.rebuildMonsterStatsAgg(rtaMapper);
		addLog("rta_monster_stats_* 합계: meta=%d, pick=%d",
				mon.metaRows(),
				mon.pickRows());

		rtaCacheEvictor.evictAllRtaCaches();
		addLog("RTA 조회 캐시 무효화 (몬스터 통계 집계 갱신)");
	}

	@Override
	protected String getBatchName() {
		return "RTA 몬스터 통계 집계 갱신";
	}
}
