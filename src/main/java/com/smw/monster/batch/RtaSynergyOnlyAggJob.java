package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;
import com.smw.rta.service.RtaSynergyAggService;

/**
 * 시너지·카운터 집계만 수행하는 단독 배치.
 * <p>
 * {@link RtaUnifiedPipelineAggJob}(raw 정규화 → 시너지 → 부가)와 달리 <b>시너지 drain 만</b> 실행한다.
 * pending 이 완전히 소진될 때까지 라운드를 반복하며, 완료 후 RTA 조회 캐시를 무효화한다.
 * <p>
 * 대상 테이블: {@code rta_agg_synergy_solo / duo / trio}, {@code rta_agg_counter_solo / duo / trio}
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} — 통합 Job 과 같은 주기로 등록하거나,
 * 통합 Job 의 시너지 단계를 끄고 이 Job 으로 대체할 수 있다.
 * <p>
 * 동일 Job 이 겹쳐 실행되면 staging 테이블 충돌·DB 부하가 배가 될 수 있어
 * {@link DisallowConcurrentExecution} 으로 한 번에 하나만 실행한다.
 */
@DisallowConcurrentExecution
public class RtaSynergyOnlyAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper                       = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor           = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaSynergyAggService synergyAggService    = applicationContext.getBean(RtaSynergyAggService.class);
		RtaBatchProperties rtaBatchProperties     = applicationContext.getBean(RtaBatchProperties.class);

		int     synergyBatch      = Math.max(1, rtaBatchProperties.getSynergyBatchSize());
		int     synPause          = Math.max(0, rtaBatchProperties.getSynergyPauseMsBetweenRounds());
		boolean evictEachRound    = rtaBatchProperties.isSynergyOnlyEvictCachesEachRound();

		addLog("[시작] RTA 시너지·카운터 단독 집계 — 배치 %d건/라운드, 라운드별캐시무효화=%s",
				synergyBatch, evictEachRound ? "Y" : "N");
		if (synPause > 0) {
			addLog("· 라운드 간 대기: %dms", synPause);
		}

		RtaBatchAggregationService.SynergyDrainResult syn = aggregationService.drainSynergyPending(
				rtaMapper,
				synergyAggService,
				rtaCacheEvictor,
				synergyBatch,
				evictEachRound,
				synPause);

		addLog("· 완료 — 라운드 %d, ok %d, fail %d, %s",
				syn.rounds(),
				syn.totalOk(),
				syn.totalFail(),
				syn.stopReason());

		if (!evictEachRound) {
			rtaCacheEvictor.evictAllRtaCaches();
		}
		addLog("[종료] RTA 조회 캐시 무효화 완료");
	}

	@Override
	protected String getBatchName() {
		return "RTA 시너지·카운터 단독 집계";
	}
}
