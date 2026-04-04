package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.mapper.summonerswarMapper;

/**
 * ranker_rtpvp_replay_raw 중 미적용(pending/failed) 건을 정규화 테이블로 재처리하는 배치.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 5분마다).
 * 재처리 본로직은 추후 {@code summonerswarService} 등과 연동.
 */
public class RtaReplayRawApplyJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		summonerswarMapper mapper = applicationContext.getBean(summonerswarMapper.class);
		int notApplied = mapper.selectRtaReplayRawNotAppliedCount();
		addLog("미적용 raw 건수 (apply_status <> applied): %d", notApplied);
		if (notApplied > 0) {
			addLog("정규화 재처리 파이프라인은 추후 연동 예정입니다.");
		}
	}

	@Override
	protected String getBatchName() {
		return "RTA 리플레이 원본(raw) 정규화 재처리";
	}
}
