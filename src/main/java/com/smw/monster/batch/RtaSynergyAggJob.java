package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;
import com.smw.rta.service.RtaSynergyAggService;

/**
 * {@code rta_match.synergy_applied_at IS NULL} 인 rid 를 {@code rta_agg_synergy_combo}에 반영한다.
 * <p>
 * 운영 스케줄은 {@link RtaUnifiedPipelineAggJob} 로 통합하는 것을 권장한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 비활성화, bat_id 10003).
 */
public class RtaSynergyAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaSynergyAggService synergyAggService = applicationContext.getBean(RtaSynergyAggService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);

		RtaBatchAggregationService.SynergyDrainResult syn = aggregationService.drainSynergyPending(
				rtaMapper,
				synergyAggService,
				rtaCacheEvictor,
				RtaBatchAggregationService.SYNERGY_BATCH_SIZE,
				1,
				true);
		if (syn.rounds() == 0) {
			addLog("시너지 집계 대상 pending rid 없음");
			return;
		}
		addLog("시너지 집계 완료 ok=%d fail=%d, 종료: %s", syn.totalOk(), syn.totalFail(), syn.stopReason());
	}

	@Override
	protected String getBatchName() {
		return "RTA 시너지 집계 (솔로·듀오·트리오)";
	}
}
