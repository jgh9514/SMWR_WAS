package com.smw.monster.batch.queue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.smw.monster.batch.queue.BatchQueueMapper.BatchQueueConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Redis 큐 기반 배치 Producer.
 * <p>
 * 1분마다 실행하되 ShedLock 으로 클러스터 전체에서 <strong>1대</strong>만 수행한다.
 * 각 배치의 {@code last_scheduled_at} ~ 현재 시각 사이 누락된 주기를 모두 계산해
 * {@code BATCH_WORK_QUEUE}(Redis LIST)에 LPUSH 한다 — Catch-up 로직.
 * <p>
 * dedup: {@code BATCH_ENQUEUED_SET}(Redis SET)으로 동일 batId+실행시각 중복 발행을 차단한다.
 * Worker가 RPOP 시 SET 에서 즉시 제거하므로, Worker 실패 후 재기동하면 다음 Producer 주기에 재발행된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchQueueProducerService {

    /** Redis LIST 키 — Worker 가 RPOP 으로 소비. */
    public static final String WORK_QUEUE   = "BATCH_WORK_QUEUE";
    /** Redis SET 키 — 중복 발행 방지. TTL 로 자동 정리. */
    public static final String ENQUEUED_SET = "BATCH_ENQUEUED_SET";
    /** dedup SET TTL: 가장 긴 interval * 3 정도면 안전. 2시간으로 고정. */
    private static final Duration DEDUP_TTL = Duration.ofHours(2);

    private final StringRedisTemplate  redis;
    private final BatchQueueMapper     queueMapper;

    /**
     * 1분 주기 Producer.
     * <ul>
     *   <li>lockAtMostFor: 비정상 종료 시 55초 후 락 자동 해제 → 다음 주기에 다른 서버가 획득 가능</li>
     *   <li>lockAtLeastFor: 빠른 완료 후에도 10초는 다른 서버가 획득 못 함 → 동시 발행 방지</li>
     * </ul>
     */
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

    // ──────────────────────────────────────────────────────────
    // private
    // ──────────────────────────────────────────────────────────

    private void enqueueMissed(BatchQueueConfig cfg, LocalDateTime now) {
        // last_scheduled_at 이 없으면 현재 주기 1개만 발행 (최초 기동)
        LocalDateTime base = cfg.lastScheduledAt() != null
                ? cfg.lastScheduledAt()
                : now.minusMinutes(cfg.intervalMinutes());

        LocalDateTime cursor    = base.plusMinutes(cfg.intervalMinutes());
        LocalDateTime lastAdded = null;
        int           pushed    = 0;

        while (!cursor.isAfter(now)) {
            BatchWorkMessage msg = new BatchWorkMessage(cfg.batId(), cfg.jobClass(), cursor);
            if (enqueueIfAbsent(msg)) {
                pushed++;
                lastAdded = cursor;
            }
            cursor = cursor.plusMinutes(cfg.intervalMinutes());
        }

        if (lastAdded != null) {
            queueMapper.updateLastScheduledAt(cfg.batId(), lastAdded);
            if (pushed > 1) {
                // catch-up: 누락 주기가 있었다는 의미
                log.warn("[queue-producer] batId={} '{}' catch-up 발행 {}건 ({}~{})",
                        cfg.batId(), cfg.batNm(), pushed, base.plusMinutes(cfg.intervalMinutes()), lastAdded);
            } else {
                log.debug("[queue-producer] batId={} '{}' 발행 time={}", cfg.batId(), cfg.batNm(), lastAdded);
            }
        }
    }

    /**
     * dedup SET에 키가 없을 때만 LIST에 LPUSH.
     *
     * @return true: 새로 발행 / false: 이미 발행됨(skip)
     */
    private boolean enqueueIfAbsent(BatchWorkMessage msg) {
        Boolean isNew = redis.opsForSet().add(ENQUEUED_SET, msg.dedupeKey());
        if (Boolean.TRUE.equals(isNew)) {
            redis.expire(ENQUEUED_SET, DEDUP_TTL);
            redis.opsForList().leftPush(WORK_QUEUE, msg.serialize());
            return true;
        }
        return false;
    }
}
