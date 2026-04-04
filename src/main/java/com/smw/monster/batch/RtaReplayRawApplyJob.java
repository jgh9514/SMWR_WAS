package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.mapper.summonerswarMapper;
import com.smw.monster.service.summonerswarService;
import com.smw.rta.cache.RtaCacheEvictor;

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
		RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

		int orphans = service.deleteArenaRtaOrphanChildrenGlobal();
		if (orphans > 0) {
			addLog("replay_list 부모 없는 고아 행 정리: %d행 삭제 (unit→pick→user)", orphans);
		}
		int notApplied = mapper.selectRtaReplayRawNotAppliedCount();
		addLog("미적용 raw 건수 (apply_status <> applied): %d", notApplied);
		if (notApplied <= 0) {
			if (orphans > 0) {
				rtaCacheEvictor.evictAllRtaCaches();
				addLog("RTA 조회 캐시 무효화 (고아 정리 반영)");
			}
			return;
		}
		int pendingPf = mapper.countRtaReplayRawPendingPf();
		addLog("정규화 대상 raw 건수 (pending + failed): %d", pendingPf);
		int done = service.applyPendingArenaReplayRawFromDb();
		addLog("정규화 반영 완료 rid 수 (이번 실행): %d (상세는 로그 [rta-raw-apply])", done);
		if (orphans > 0 || done > 0) {
			rtaCacheEvictor.evictAllRtaCaches();
			addLog("RTA 조회 캐시 무효화 (정규화·고아 정리 반영)");
		}
	}

	@Override
	protected String getBatchName() {
		return "RTA 리플레이 원본(raw) 정규화 재처리";
	}
}
