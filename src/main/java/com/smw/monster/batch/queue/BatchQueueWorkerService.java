package com.smw.monster.batch.queue;

import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis 큐 기반 배치 Worker.
 *
 * <p>모든 서버(K8s, 로컬)에서 상시 가동. RPOP으로 일감을 꺼내 실행한다.
 *
 * <p><b>단순 배치</b> ({@link QueuedBatchJob}): {@code job.execute(executionTime)} 호출.
 *
 * <p><b>파티션 배치</b> ({@link PartitionedQueuedBatchJob}): {@code job.executePartition(executionTime, partitionKey)} 호출.
 * K8s Worker와 로컬 Worker가 서로 다른 파티션을 RPOP 해 병렬 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smw.batch.queue.enabled", havingValue = "true")
public class BatchQueueWorkerService {

    private final StringRedisTemplate redis;
    private final BatchQueueMapper    queueMapper;
    private final ApplicationContext  applicationContext;

    @Scheduled(fixedDelay = 1_000)
    public void consume() {
        String raw = redis.opsForList().rightPop(BatchQueueProducerService.WORK_QUEUE);
        if (raw == null) return;

        BatchWorkMessage msg;
        try {
            msg = BatchWorkMessage.deserialize(raw);
        } catch (Exception e) {
            log.error("[queue-worker] 메시지 파싱 실패 — 폐기: raw='{}' error={}", raw, e.getMessage());
            return;
        }

        // dedup SET 에서 즉시 제거 → Worker 실패 시 다음 Producer 주기에 재발행 가능
        redis.opsForSet().remove(BatchQueueProducerService.ENQUEUED_SET, msg.dedupeKey());

        QueuedBatchJob job = resolveJob(msg.jobClass());
        if (job == null) return;

        if (msg.isPartitioned()) {
            runPartitioned(job, msg);
        } else {
            runSimple(job, msg);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private
    // ──────────────────────────────────────────────────────────────────────────

    private void runSimple(QueuedBatchJob job, BatchWorkMessage msg) {
        log.info("[queue-worker] 실행 batId={} class={} time={}", msg.batId(), msg.jobClass(), msg.executionTime());
        long start = System.currentTimeMillis();
        try {
            job.execute(msg.executionTime());
            queueMapper.updateLastExecutedAt(msg.batId(), msg.executionTime());
            log.info("[queue-worker] 완료 batId={} {}ms", msg.batId(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[queue-worker] 실패 batId={} class={}: {}", msg.batId(), msg.jobClass(), e.getMessage(), e);
        }
    }

    private void runPartitioned(QueuedBatchJob job, BatchWorkMessage msg) {
        if (!(job instanceof PartitionedQueuedBatchJob partitionedJob)) {
            log.error("[queue-worker] 파티션 메시지인데 Bean이 PartitionedQueuedBatchJob 아님: class={}", msg.jobClass());
            return;
        }
        log.info("[queue-worker] 파티션 실행 batId={} class={} partition={} time={}",
                msg.batId(), msg.jobClass(), msg.partitionKey(), msg.executionTime());
        long start = System.currentTimeMillis();
        try {
            partitionedJob.executePartition(msg.executionTime(), msg.partitionKey());
            queueMapper.updateLastExecutedAt(msg.batId(), msg.executionTime());
            log.info("[queue-worker] 파티션 완료 batId={} partition={} {}ms",
                    msg.batId(), msg.partitionKey(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[queue-worker] 파티션 실패 batId={} partition={}: {}",
                    msg.batId(), msg.partitionKey(), e.getMessage(), e);
        }
    }

    private QueuedBatchJob resolveJob(String beanName) {
        try {
            return applicationContext.getBean(beanName, QueuedBatchJob.class);
        } catch (BeansException e) {
            log.error("[queue-worker] Bean 조회 실패: jobClass='{}' — sys_batch_config.job_class 와 @Component 이름 확인 필요",
                    beanName);
            return null;
        }
    }
}
