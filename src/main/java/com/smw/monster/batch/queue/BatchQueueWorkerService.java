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
 * <p>
 * <strong>모든 서버</strong>(K8s, 로컬)에서 상시 가동. {@code fixedDelay} 로 직전 실행이 끝난 후
 * 1초 대기한 뒤 다시 RPOP 을 시도한다.
 * <p>
 * 실행 흐름:
 * <ol>
 *   <li>RPOP — 큐가 비면 null 반환 → 즉시 복귀(다음 fixedDelay 대기)</li>
 *   <li>dedup SET 에서 해당 키 제거 — 이후 Producer 가 같은 키를 재발행 가능</li>
 *   <li>ApplicationContext 에서 jobClass Bean 조회 ({@link QueuedBatchJob} 타입)</li>
 *   <li>job.execute(executionTime) 실행</li>
 *   <li>last_executed_at 갱신 — 실패 시 미갱신(지연 모니터링 가능)</li>
 * </ol>
 * <p>
 * fixedDelay=1_000 은 <em>단일 스레드</em> 기준. 긴 Job 이 큐를 막지 않으려면
 * {@code @Async} + 별도 ThreadPoolTaskExecutor 를 추가로 적용할 것.
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

        // dedup SET 에서 즉시 제거 → 이 Job 이 실패해도 다음 Producer 주기에 재발행 가능
        redis.opsForSet().remove(BatchQueueProducerService.ENQUEUED_SET, msg.dedupeKey());

        QueuedBatchJob job = resolveJob(msg.jobClass());
        if (job == null) return;

        log.info("[queue-worker] 실행 시작 batId={} class={} executionTime={}", msg.batId(), msg.jobClass(), msg.executionTime());
        long startMs = System.currentTimeMillis();
        try {
            job.execute(msg.executionTime());
            queueMapper.updateLastExecutedAt(msg.batId(), msg.executionTime());
            log.info("[queue-worker] 완료 batId={} {}ms", msg.batId(), System.currentTimeMillis() - startMs);
        } catch (Exception e) {
            log.error("[queue-worker] 실패 batId={} class={} executionTime={}: {}",
                    msg.batId(), msg.jobClass(), msg.executionTime(), e.getMessage(), e);
            // last_executed_at 미갱신 → 다음 Producer 주기에 재발행됨
        }
    }

    /** ApplicationContext 에서 jobClass 이름으로 QueuedBatchJob Bean 조회. */
    private QueuedBatchJob resolveJob(String beanName) {
        try {
            return applicationContext.getBean(beanName, QueuedBatchJob.class);
        } catch (BeansException e) {
            log.error("[queue-worker] Bean 조회 실패: jobClass='{}' — sys_batch_config.job_class 와 @Component 이름 확인 필요. error={}",
                    beanName, e.getMessage());
            return null;
        }
    }
}
