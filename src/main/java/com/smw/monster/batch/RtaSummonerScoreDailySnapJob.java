package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * 소환사 일별 래더 점수 스냅 ({@code rta_agg_summoner_score_daily_snap}).
 * <p>
 * 시즌 시작일~오늘(KST) 중 스냅이 없는 모든 일자를 소급 적재(경기 없는 날 포함).
 * 당일 경기 없는 소환사는 match_cnt=0, 직전 점수 carry-forward로 적재.
 * 오늘·어제(KST)는 매 실행마다 UPSERT 한다.
 * <p>
 * DB 등록: {@code sql/dml/migrate_rta_summoner_score_daily_batch_config.sql}
 * (크론 예: 매시 15분 {@code 0 15 * * * ?}). {@code use_yn=Y}, {@code smw.batch.quartz.enabled=true} 필요.
 */
@DisallowConcurrentExecution
public class RtaSummonerScoreDailySnapJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		addLog("--- rta_agg_summoner_score_daily_snap (누락 일자 소급 + 오늘/어제 KST 재적재) ---");
		long t0 = System.currentTimeMillis();
		int dateRuns = aggregationService.rebuildSummonerScoreDailySnap(rtaMapper);
		addLog("score daily snap 완료: dateProcessRuns=%d, elapsed=%dms", dateRuns, System.currentTimeMillis() - t0);

		if (dateRuns > 0) {
			rtaCacheEvictor.evictAllRtaCaches();
			addLog("RTA 조회 캐시 무효화 완료");
		} else {
			addLog("--- 캐시: 스냅 변경 없음 — 스킵 ---");
		}
	}

	@Override
	protected String getBatchName() {
		return "RTA 소환사 일별 점수 스냅";
	}
}
