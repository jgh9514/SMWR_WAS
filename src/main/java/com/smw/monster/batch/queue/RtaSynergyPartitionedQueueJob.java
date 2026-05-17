package com.smw.monster.batch.queue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaSynergyAggService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 시너지·카운터 집계 — 파티션(rid 구간 분할) 분산 처리 버전.
 *
 * <p><b>Producer 단계</b> ({@link #createPartitions}):
 * <ol>
 *   <li>{@code BatchQueueMapper.selectSynergyPendingRidRange()} 로 미집계 rid 의 MIN/MAX 조회</li>
 *   <li>전체 구간을 {@code PARTITION_RID_SIZE}(기본 50,000) 단위로 분할</li>
 *   <li>각 구간을 {@code "minRid-maxRid"} 형식의 파티션 키로 반환</li>
 * </ol>
 *
 * <p><b>Worker 단계</b> ({@link #executePartition}):
 * <ol>
 *   <li>파티션 키 파싱 → minRid / maxRid 추출</li>
 *   <li>{@code selectPendingSynergyAggRidsBetween()} 로 해당 구간의 pending rid만 조회</li>
 *   <li>{@link RtaSynergyAggService#applySynergyBatch} 로 시너지·카운터 집계 적용</li>
 *   <li>K8s Worker + 로컬 Worker 가 서로 다른 구간을 병렬 처리</li>
 * </ol>
 *
 * <p><b>DB 등록</b>:
 * <pre>
 * INSERT INTO sys_batch_config (bat_nm, job_class, queue_yn, interval_minutes, use_yn, sort_sn)
 * VALUES ('RTA 시너지 파티션 집계', 'rtaSynergyPartitionedQueueJob', 'Y', 1, 'Y', 11);
 * </pre>
 */
@Slf4j
@Component("rtaSynergyPartitionedQueueJob")
@RequiredArgsConstructor
public class RtaSynergyPartitionedQueueJob implements PartitionedQueuedBatchJob {

    /** rid 구간당 처리할 최대 replay_id 범위 크기. 서버 성능에 맞게 조정. */
    private static final long PARTITION_RID_SIZE = 50_000L;

    private final BatchQueueMapper      queueMapper;
    private final RtaMapper             rtaMapper;
    private final RtaSynergyAggService  synergyAggService;
    private final RtaCacheEvictor       cacheEvictor;
    private final RtaBatchProperties    batchProperties;

    // ──────────────────────────────────────────────────────────────────────────
    // Producer 단계
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public List<String> createPartitions(LocalDateTime executionTime) {
        BatchQueueMapper.SynergyPendingRange range = queueMapper.selectSynergyPendingRidRange();

        if (range == null || range.minRid() == 0 && range.maxRid() == 0) {
            log.debug("[synergy-partition] pending 없음 — 파티션 생성 생략");
            return List.of();
        }

        long minRid = range.minRid();
        long maxRid = range.maxRid();
        List<String> partitions = new ArrayList<>();

        long cursor = minRid;
        while (cursor <= maxRid) {
            long end = Math.min(cursor + PARTITION_RID_SIZE - 1, maxRid);
            partitions.add(cursor + "-" + end);
            cursor = end + 1;
        }

        log.info("[synergy-partition] rid 범위 {}~{}, 파티션 {}개 생성 (구간 {}건)",
                minRid, maxRid, partitions.size(), PARTITION_RID_SIZE);
        return partitions;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Worker 단계
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void executePartition(LocalDateTime executionTime, String partitionKey) {
        long[] range = parsePartitionKey(partitionKey);
        long minRid  = range[0];
        long maxRid  = range[1];
        int  batchSize = Math.max(1, batchProperties.getSynergyBatchSize());

        log.info("[synergy-partition] 실행 rid {}~{} batchSize={}", minRid, maxRid, batchSize);

        // idx_rta_match_synergy_pending 강제 사용 (Seq Scan 방지)
        rtaMapper.hintBatchDisableSeqScan();

        int totalOk   = 0;
        int totalFail = 0;
        int rounds    = 0;

        while (true) {
            List<Long> rids = rtaMapper.selectPendingSynergyAggRidsBetween(minRid, maxRid, batchSize);
            if (rids == null || rids.isEmpty()) break;

            RtaSynergyAggService.SynergyBatchApplyResult result = synergyAggService.applySynergyBatch(rids);
            totalOk   += result.ok();
            totalFail += result.fail();
            rounds++;

            // 한 구간을 다 처리했는지 확인 (처리된 rid 중 maxRid 이하만 있으므로 rids.size() < batchSize = 소진)
            if (rids.size() < batchSize) break;
        }

        cacheEvictor.evictAllRtaCaches();

        log.info("[synergy-partition] 완료 rid {}~{} rounds={} ok={} fail={}",
                minRid, maxRid, rounds, totalOk, totalFail);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // private
    // ──────────────────────────────────────────────────────────────────────────

    private static long[] parsePartitionKey(String key) {
        int dash = key.indexOf('-');
        if (dash < 0) throw new IllegalArgumentException("파티션 키 형식 오류(minRid-maxRid): " + key);
        return new long[]{
                Long.parseLong(key.substring(0, dash)),
                Long.parseLong(key.substring(dash + 1))
        };
    }
}
