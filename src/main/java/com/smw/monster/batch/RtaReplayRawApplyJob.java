package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.mapper.summonerswarMapper;
import com.smw.monster.service.summonerswarService;

/**
 * ranker_rtpvp_replay_raw 중 미적용(pending/failed) 건을 정규화 테이블로 재처리하는 배치.
 * <p>
 * 스케줄: DB {@code sys_batch_config.cron_expr} (기본 5분마다).
 */
public class RtaReplayRawApplyJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		summonerswarMapper mapper = applicationContext.getBean(summonerswarMapper.class);
		summonerswarService service = applicationContext.getBean(summonerswarService.class);
		int orphans = service.deleteArenaRtaOrphanChildrenGlobal();
		if (orphans > 0) {
			addLog("replay_list 부모 없는 고아 행 정리: %d행 삭제 (unit→pick→user)", orphans);
		}
		int notApplied = mapper.selectRtaReplayRawNotAppliedCount();
		addLog("미적용 raw 건수 (apply_status <> applied): %d", notApplied);
		if (notApplied <= 0) {
			return;
		}
		int batchSize = 100;
		try {
			String p = applicationContext.getEnvironment().getProperty("smw.rta.raw-apply.batch-size");
			if (p != null && !p.isBlank()) {
				batchSize = Math.min(500, Math.max(1, Integer.parseInt(p.trim())));
			}
		} catch (Exception e) {
			addLog("batch-size 파싱 실패, 기본 100 사용");
		}
		int done = service.applyPendingArenaReplayRawFromDb(batchSize);
		addLog("정규화 반영 완료 rid 수 (이번 실행): %d (한 번에 최대 %d건 조회)", done, batchSize);
	}

	@Override
	protected String getBatchName() {
		return "RTA 리플레이 원본(raw) 정규화 재처리";
	}
}
