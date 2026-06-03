package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;
import com.smw.rta.service.RtaBatchBacklogScaler;
import com.smw.rta.service.RtaSynergyAggService;

/**
 * 시너지·카운터 집계만 수행하는 단독 배치.
 * <p>
 * {@link RtaUnifiedPipelineAggJob}(raw 정규화 → 시너지 → 부가)와 달리 <b>시너지 drain 만</b> 실행한다.
 * 한 실행당 라운드 수는 {@code smw.rta.batch.synergy-max-rounds-per-job}(기본 1); 잔여 pending 은 다음 스케줄에서 이어 처리한다.
 * 작업 후 RTA 조회 캐시를 무효화한다.
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
		RtaBatchBacklogScaler backlogScaler       = applicationContext.getBean(RtaBatchBacklogScaler.class);

		int     synergyBatch      = Math.max(1, rtaBatchProperties.getSynergyBatchSize());
		int     synPause          = Math.max(0, rtaBatchProperties.getSynergyPauseMsBetweenRounds());
		boolean evictEachRound    = rtaBatchProperties.isSynergyOnlyEvictCachesEachRound();
		int     maxRoundsConfigured = rtaBatchProperties.getSynergyMaxRoundsPerJob();

		RtaBatchBacklogScaler.RtaBatchBacklogCounts backlog = backlogScaler.snapshot();
		int maxRounds = backlogScaler.resolveSynergyMaxRounds(
				backlog.synergyPending(), synergyBatch, maxRoundsConfigured);
		synPause = backlogScaler.resolveSynergyPauseMs(backlog.synergyPending(), synPause, synergyBatch);

		addLog("[시작] RTA 시너지·카운터 단독 집계 — 배치 %d건/라운드, Job당 라운드상한=%s, 라운드별캐시무효화=%s",
				synergyBatch,
				maxRounds <= 0 ? "무제한(pending 소진까지)" : String.valueOf(maxRounds),
				evictEachRound ? "Y" : "N");
		addLog("· backlog: synergy pending %,d (설정 라운드 %s → 이번 Job %s)",
				backlog.synergyPending(),
				maxRoundsConfigured <= 0 ? "무제한" : String.valueOf(maxRoundsConfigured),
				maxRounds <= 0 ? "무제한" : String.valueOf(maxRounds));
		if (synPause > 0) {
			addLog("· 라운드 간 대기: %dms", synPause);
		}
		long wallMs = rtaBatchProperties.getSynergyMaxWallClockMsPerJob();
		if (wallMs > 0) {
			addLog("· Job 벽시계 상한: %d분", wallMs / 60_000L);
		}

		long pendingAfter = backlog.synergyPending();
		long jobStart = System.currentTimeMillis();

		RtaBatchAggregationService.SynergyDrainResult syn = aggregationService.drainSynergyPending(
				rtaMapper,
				synergyAggService,
				rtaCacheEvictor,
				synergyBatch,
				evictEachRound,
				synPause,
				maxRounds,
				wallMs);

		long pendingRemain = backlogScaler.snapshot().synergyPending();
		long elapsedMs = System.currentTimeMillis() - jobStart;
		double ridsPerHour = elapsedMs > 0 && syn.totalOk() > 0
				? syn.totalOk() * 3_600_000.0 / elapsedMs
				: 0.0;

		addLog("· 완료 — 라운드 %d, ok %d, fail %d, %s",
				syn.rounds(),
				syn.totalOk(),
				syn.totalFail(),
				syn.stopReason());
		addLog("· pending %,d → %,d (처리 %,d경기, %.0f경기/시간, Job %d분)",
				pendingAfter,
				pendingRemain,
				syn.totalOk(),
				ridsPerHour,
				elapsedMs / 60_000L);
		if (pendingRemain > pendingAfter && syn.totalOk() > 0) {
			addLog("· 주의: pending 이 줄지 않음 — raw 유입이 drain 보다 빠르거나 다른 Job 이 겹칠 수 있음");
		}

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
