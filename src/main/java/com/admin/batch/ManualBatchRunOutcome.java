package com.admin.batch;

/**
 * 수동 배치 동기 실행({@link com.sysconf.config.BatchConfig#runOnceAndWait}) 결과.
 */
public record ManualBatchRunOutcome(
		boolean triggered,
		boolean completed,
		boolean timedOut,
		Long runSn,
		String rsltCd,
		String rsltTxt,
		long elapsedMs) {

	public static ManualBatchRunOutcome notTriggered() {
		return new ManualBatchRunOutcome(false, false, false, null, null, null, 0L);
	}
}
