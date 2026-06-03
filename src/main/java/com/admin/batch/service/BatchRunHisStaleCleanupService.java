package com.admin.batch.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.admin.batch.mapper.BatchMapper;
import com.smw.rta.config.BatchLogProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * {@code sys_batch_run_his.rslt_cd=RUNNING} 이 장시간 갱신되지 않은 행을 ABORTED 로 정리한다.
 * Pod OOM·이력 UPDATE 실패 등으로 남은 고착 RUNNING 방지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smw.batch.run-his.stale-cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class BatchRunHisStaleCleanupService {

	private final BatchMapper batchMapper;
	private final BatchLogProperties batchLogProperties;

	@Scheduled(cron = "0 */15 * * * ?")
	@SchedulerLock(name = "BatchRunHisStaleCleanup", lockAtMostFor = "14m", lockAtLeastFor = "1m")
	public void abortStaleRunningRows() {
		int hours = Math.max(1, batchLogProperties.getRunHis().getStaleRunningHours());
		try {
			int updated = batchMapper.abortStaleBatchRunHis(hours);
			if (updated > 0) {
				log.warn("[batch-run-his] stale RUNNING {}건 → ABORTED (threshold={}h)", updated, hours);
			}
		} catch (Exception e) {
			log.error("[batch-run-his] stale RUNNING 정리 실패 thresholdHours={}", hours, e);
		}
	}
}
