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
		addLog("rta_match 부모 없는 unit → participant 순 고아 행 삭제 시작");
		int n = service.deleteArenaRtaOrphanChildrenGlobal();
		addLog("고아 행 삭제 완료: %d건", n);
	}

	@Override
	protected String getBatchName() {
		return "RTA 고아 행 정리";
	}
}
