package com.smw.monster.batch.queue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.smw.monster.batch.queue.BatchQueueMapper.BatchQueueConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Redis 큐 기반 배치 Producer.
 *
 * <p>1분마다 실행하되 ShedLock 으로 클러스터 전체에서 <strong>1대</strong>만 수행한다.
 *
 * <p><b>단순 배치</b>: {@link QueuedBatchJob} 구현 Bean → executionTime 단일 메시지 발행.
 *
 * <p><b>파티션 배치</b>: {@link PartitionedQueuedBatchJob} 구현 Bean → {@code createPartitions()}
 * 를 호출해 파티션 키 목록을 얻고, 각 키를 별도 메시지로 발행.
 * 여러 Worker(K8s + 로컬)가 병렬로 각 파티션을 처리한다.
 *
 * <p>dedup: {@code BATCH_ENQUEUED_SET}(Redis SET) 으로 동일 batId+시각(+파티션키) 중복 발행 차단.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smw.batch.queue.enabled", havingValue = "true")
public class BatchQueueProducerService {

    public static final String WORK_QUEUE   = "BATCH_WORK_QUEUE";
    public static final String ENQUEUED_SET = "BATCH_ENQUEUED_SET";
    private static final Duration DEDUP_TTL = Duration.ofHours(2);

    private final StringRedisTemplate redis;
    private final BatchQueueMapper    queueMapper;
    private final ApplicationContext  applicationContext;

    @Scheduled(fixedRate = 60_000)
    @SchedulerLock(name = "BatchQueueProducer", lockAtMostFor = "55s", lockAtLeastFor = "10s")
    public void produce() {
        List<BatchQueueConfig> configs = queueMapper.selectQueueEnabledBatches();
        if (configs.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        for (BatchQueueConfig cfg : configs) {
            try {
                enqueueMissed(cfg, now);
            } catch (Exception e) {
                log.error("[queue-producer] batId={} '{}' 발행 실패: {}", cfg.batId(), cfg.batNm(), e.getMessage(), e);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private
    // ──────────────────────────────────────────────────────────────────────────

    private void enqueueMissed(BatchQueueConfig cfg, LocalDateTime now) {
        LocalDateTime base = cfg.lastScheduledAt() != null
                ? cfg.lastScheduledAt()
                : now.minusMinutes(cfg.intervalMinutes());

        LocalDateTime cursor    = base.plusMinutes(cfg.intervalMinutes());
        LocalDateTime lastAdded = null;
        int           total     = 0;

        while (!cursor.isAfter(now)) {
            int pushed = enqueueForTime(cfg, cursor);
            if (pushed > 0) {
                lastAdded = cursor;
                total += pushed;
            }
            cursor = cursor.plusMinutes(cfg.intervalMinutes());
        }

        if (lastAdded != null) {
            queueMapper.updateLastScheduledAt(cfg.batId(), lastAdded);
            if (total > 1) {
                log.info("[queue-producer] batId={} '{}' 발행 {}건 (catch-up 포함)", cfg.batId(), cfg.batNm(), total);
            } else {
                log.debug("[queue-producer] batId={} '{}' 발행 time={}", cfg.batId(), cfg.batNm(), lastAdded);
            }
        }
    }

    /**
     * 하나의 executionTime에 대해 메시지를 발행한다.
     * 파티션 배치이면 파티션 수만큼, 단순 배치이면 1건 발행.
     *
     * @return 실제로 큐에 추가된 메시지 수
     */
    private int enqueueForTime(BatchQueueConfig cfg, LocalDateTime executionTime) {
        // 파티션 배치 여부 확인
        PartitionedQueuedBatchJob partitioned = resolvePartitioned(cfg.jobClass());

        if (partitioned != null) {
            return enqueuePartitioned(cfg, executionTime, partitioned);
        } else {
            BatchWorkMessage msg = BatchWorkMessage.of(cfg.batId(), cfg.jobClass(), executionTime);
            return enqueueIfAbsent(msg) ? 1 : 0;
        }
    }

    /**
     * 파티션 배치: createPartitions() 호출 → 각 파티션을 별도 메시지로 발행.
     */
    private int enqueuePartitioned(BatchQueueConfig cfg, LocalDateTime executionTime,
                                   PartitionedQueuedBatchJob job) {
        List<String> partitions;
        try {
            partitions = job.createPartitions(executionTime);
        } catch (Exception e) {
            log.error("[queue-producer] batId={} createPartitions 실패: {}", cfg.batId(), e.getMessage(), e);
            return 0;
        }

        if (partitions == null || partitions.isEmpty()) {
            log.debug("[queue-producer] batId={} '{}' 파티션 없음 (처리할 데이터 없음, time={})",
                    cfg.batId(), cfg.batNm(), executionTime);
            return 0;
        }

        int pushed = 0;
        for (String partitionKey : partitions) {
            BatchWorkMessage msg = BatchWorkMessage.ofPartition(
                    cfg.batId(), cfg.jobClass(), executionTime, partitionKey);
            if (enqueueIfAbsent(msg)) {
                pushed++;
                log.debug("[queue-producer] batId={} 파티션 발행 partition={}", cfg.batId(), partitionKey);
            }
        }
        log.info("[queue-producer] batId={} '{}' 파티션 {}건 발행 (time={})",
                cfg.batId(), cfg.batNm(), pushed, executionTime);
        return pushed;
    }

    private boolean enqueueIfAbsent(BatchWorkMessage msg) {
        Long added = redis.opsForSet().add(ENQUEUED_SET, msg.dedupeKey());
        if (added != null && added > 0) {
            redis.expire(ENQUEUED_SET, DEDUP_TTL);
            redis.opsForList().leftPush(WORK_QUEUE, msg.serialize());
            return true;
        }
        return false;
    }

    /** Bean이 PartitionedQueuedBatchJob 인지 확인. 아니거나 못 찾으면 null. */
    private PartitionedQueuedBatchJob resolvePartitioned(String beanName) {
        try {
            QueuedBatchJob bean = applicationContext.getBean(beanName, QueuedBatchJob.class);
            return bean instanceof PartitionedQueuedBatchJob p ? p : null;
        } catch (BeansException e) {
            return null;
        }
    }
}
