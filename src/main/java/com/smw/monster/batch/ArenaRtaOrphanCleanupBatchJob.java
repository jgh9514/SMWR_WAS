package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.monster.service.summonerswarService;

/**
 * {@code rta_match} 부모 없이 남은 {@code rta_match_unit_pick} / {@code rta_match_participant} 고아 행만 정리한다.
 * <p>
 * 통합 {@link RtaUnifiedPipelineAggJob} 에서는 호출하지 않는다. Quartz {@code sys_batch_config} 에 본 클래스를
 * 등록한 별도 스케줄(예: 일 1회)로 실행한다.
 */
@DisallowConcurrentExecution
public class ArenaRtaOrphanCleanupBatchJob extends BaseBatchJob {

	@Override
	protected void executeBatch(JobExecutionContext context) throws Exception {
		summonerswarService service = applicationContext.getBean(summonerswarService.class);
		addLog("rta_match 부모 없는 unit → participant 순 고아 점검·삭제 시작");
		com.smw.monster.service.ArenaRtaOrphanCleanupResult result = service.deleteArenaRtaOrphanChildrenGlobal();
		if ("FULL".equals(result.checkMode())) {
			addLog("점검 모드: 전수 스캔 (full-scan-day-of-week)");
		} else {
			addLog("점검 모드: 증분 (replay_id >= %s)",
					result.floorReplayId() != null ? result.floorReplayId() : 0L);
		}
		addLog("고아 행 삭제 완료 — unit %d건 · participant %d건 · 합계 %d건 (replay 배치 %d회)",
				result.unitsDeleted(), result.participantsDeleted(), result.totalDeleted(),
				result.orphanReplayIdBatches());
		if (result.totalDeleted() > 0) {
			addLog("※ 동일 건수가 반복되면 RTA 정규화가 match 없이 unit 을 재적재하는지 점검(배포 후 unit INSERT 는 match JOIN)");
		}
	}

	@Override
	protected String getBatchName() {
		return "RTA 고아 행 정리";
	}
}
