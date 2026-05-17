package com.smw.monster.batch.queue;

import java.time.LocalDateTime;

/**
 * Redis 큐 기반 배치 워커가 실행하는 Job 인터페이스.
 * <p>
 * {@code sys_batch_config.job_class} 컬럼값과 동일한 이름으로 {@code @Component} 등록한 빈이
 * 이 인터페이스를 구현하면 {@link BatchQueueWorkerService}가 동적으로 찾아 실행한다.
 * <p>
 * 기존 Quartz 기반 {@link com.smw.monster.batch.BaseBatchJob} 과 공존 가능.
 * {@code queue_yn='Y'} 인 배치만 이 인터페이스 경로로 실행되며, Quartz 크론 등록은 제거하거나 유지해도 무방하다.
 */
public interface QueuedBatchJob {

    /**
     * 배치를 실행한다.
     *
     * @param executionTime Producer가 발행한 실행 기준 시각(분 단위 절삭). 집계 범위 계산 등에 활용.
     */
    void execute(LocalDateTime executionTime);
}
