package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * 레거시 스냅샷 배치. v2 스키마에서 평탄화 스냅샷 테이블·구 replay_list 는 제거되었고,
 * {@link RtaMapper} 의 스냅샷 SQL 은 no-op 스텁이므로 실질 처리 없음(호환용).
 * <p>
 * 한 트리거당 <strong>1라운드</strong>만 실행(배치 크기 {@link RtaBatchAggregationService#SNAPSHOT_BATCH_SIZE}).
 * 운영 스케줄은 {@link RtaUnifiedPipelineAggJob} 로 통합하는 것을 권장한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 비활성화, bat_id 10002).
 */
public class RtaReplayMatchSnapshotAggJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);

		RtaBatchAggregationService.SnapshotDrainResult snap = aggregationService.drainPendingSnapshots(
				rtaMapper,
				rtaCacheEvictor,
				RtaBatchAggregationService.SNAPSHOT_BATCH_SIZE,
				1,
				true);
		if (snap.rounds() > 0) {
			addLog("스냅샷 1라운드: 선택 rids %d건, upsert %d, done %d, 종료: %s",
					snap.totalRidsTouched(),
					snap.totalUpserted(),
					snap.totalMarked(),
					snap.stopReason());
		} else {
			addLog("스냅샷 대상 pending rid 없음");
		}
	}

	@Override
	protected String getBatchName() {
		return "RTA 매치 스냅샷 집계 (조회용 평탄화)";
	}
}
