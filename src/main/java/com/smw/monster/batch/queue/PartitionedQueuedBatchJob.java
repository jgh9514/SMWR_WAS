package com.smw.monster.batch.queue;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 데이터를 구간별로 쪼개 여러 Worker가 병렬 처리하는 배치 인터페이스.
 *
 * <p><b>동작 흐름</b>
 * <ol>
 *   <li>Producer가 {@link #createPartitions}를 호출 → 파티션 키 목록 반환</li>
 *   <li>Producer가 각 파티션 키를 별도 메시지로 BATCH_WORK_QUEUE에 LPUSH</li>
 *   <li>여러 Worker(K8s + 로컬)가 RPOP으로 각자 다른 파티션을 가져가 병렬 실행</li>
 *   <li>Worker가 {@link #executePartition}을 파티션 키와 함께 호출</li>
 * </ol>
 *
 * <p><b>파티션 키 설계 예시</b>
 * <ul>
 *   <li>rid 구간: {@code "100000-200000"} → minRid-maxRid 범위의 row만 처리</li>
 *   <li>모드 분할: {@code "mod:0:4"} → replay_id % 4 == 0 인 row만 처리</li>
 *   <li>날짜 분할: {@code "2025-05-01"} → 해당 날짜 데이터만 처리</li>
 * </ul>
 *
 * <p>{@link #execute}는 파티션 배치에서 호출되지 않으므로 기본 구현으로 막아둔다.
 * 단순 배치가 필요하면 {@link QueuedBatchJob}을 직접 구현할 것.
 */
public interface PartitionedQueuedBatchJob extends QueuedBatchJob {

    /**
     * Producer가 발행 시점에 호출. DB를 조회해 현재 처리할 파티션 키 목록을 결정한다.
     *
     * <p>빈 리스트를 반환하면 이번 주기에는 아무 메시지도 발행하지 않는다(처리할 데이터 없음).
     *
     * @param executionTime 발행 기준 시각
     * @return 파티션 키 목록 — 각각이 독립 Queue 메시지로 발행됨
     */
    List<String> createPartitions(LocalDateTime executionTime);

    /**
     * Worker가 RPOP 후 호출. 주어진 파티션 키에 해당하는 데이터만 처리한다.
     *
     * @param executionTime 발행 기준 시각
     * @param partitionKey  {@link #createPartitions}가 반환한 키 중 하나
     */
    void executePartition(LocalDateTime executionTime, String partitionKey);

    /** 파티션 배치는 execute() 직접 호출 불가 — Worker가 executePartition()을 사용한다. */
    @Override
    default void execute(LocalDateTime executionTime) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " 은 PartitionedQueuedBatchJob 입니다. "
                + "executePartition(executionTime, partitionKey) 를 사용하세요.");
    }
}
