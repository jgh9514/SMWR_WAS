package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.mapper.summonerswarMapper;
import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * ranker_rtpvp_replay_raw 중 미적용(pending/failed) 건을 정규화 테이블로 재처리하는 배치.
 * <p>
 * 운영 스케줄은 {@link RtaUnifiedPipelineAggJob} 로 통합하는 것을 권장한다.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 비활성화, bat_id 10001).
 */
public class RtaReplayRawApplyJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		summonerswarMapper mapper = applicationContext.getBean(summonerswarMapper.class);
		summonerswarService service = applicationContext.getBean(summonerswarService.class);
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);
		RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);

		int notApplied = mapper.selectRtaReplayRawNotAppliedCount();
		addLog("미적용 raw 건수 (apply_status <> applied): %d", notApplied);
		if (notApplied <= 0) {
			RtaBatchAggregationService.RawApplyDrainResult raw = aggregationService.drainReplayRawPending(service, 1);
			if (raw.orphansDeleted() > 0) {
				addLog("replay_list 부모 없는 고아 행 정리: %d행 삭제 (unit→pick→user)", raw.orphansDeleted());
				rtaCacheEvictor.evictAllRtaCaches();
				addLog("RTA 조회 캐시 무효화 (고아 정리 반영)");
			}
			return;
		}
		int pendingPf = mapper.countRtaReplayRawPendingPf();
		addLog("정규화 대상 raw 건수 (pending + failed): %d", pendingPf);
		RtaBatchAggregationService.RawApplyDrainResult raw = aggregationService.drainReplayRawPending(service, 1);
		addLog("raw 적용: 라운드 %d, 적용 %d건, 고아 삭제 %d, 종료: %s",
				raw.rounds(),
				raw.totalApplied(),
				raw.orphansDeleted(),
				raw.stopReason());
		if (raw.orphansDeleted() > 0 || raw.totalApplied() > 0) {
			rtaCacheEvictor.evictAllRtaCaches();
			addLog("RTA 조회 캐시 무효화 (정규화·고아 정리 반영)");
		}
	}

	@Override
	protected String getBatchName() {
		return "RTA 리플레이 원본(raw) 정규화 재처리";
	}
}
