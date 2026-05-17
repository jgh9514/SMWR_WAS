package com.smw.monster.batch.queue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Redis BATCH_WORK_QUEUE에 저장되는 배치 일감 메시지.
 *
 * <p>직렬화 형식:
 * <ul>
 *   <li>단순 배치: {@code "batId:jobClass:yyyyMMddHHmm"}</li>
 *   <li>파티션 배치: {@code "batId:jobClass:yyyyMMddHHmm:partitionKey"}</li>
 * </ul>
 * {@code partitionKey}는 Job이 자유롭게 정의. 예: {@code "100000-200000"} (minRid-maxRid)
 */
public record BatchWorkMessage(
        long          batId,
        String        jobClass,
        LocalDateTime executionTime,
        String        partitionKey       // null = 단순 배치
) {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 단순 배치 생성 */
    public static BatchWorkMessage of(long batId, String jobClass, LocalDateTime executionTime) {
        return new BatchWorkMessage(batId, jobClass, executionTime, null);
    }

    /** 파티션 배치 생성 */
    public static BatchWorkMessage ofPartition(long batId, String jobClass,
                                               LocalDateTime executionTime, String partitionKey) {
        return new BatchWorkMessage(batId, jobClass, executionTime, partitionKey);
    }

    public boolean isPartitioned() {
        return partitionKey != null;
    }

    /** Redis LIST 저장 형식으로 직렬화. */
    public String serialize() {
        String base = batId + ":" + jobClass + ":" + executionTime.format(FMT);
        return partitionKey != null ? base + ":" + partitionKey : base;
    }

    /**
     * Redis에서 꺼낸 문자열을 파싱.
     * split limit=4 — jobClass(콜론 없음), time(12자리 숫자), 이후 전체가 partitionKey.
     */
    public static BatchWorkMessage deserialize(String raw) {
        String[] parts = raw.split(":", 4);
        if (parts.length < 3) {
            throw new IllegalArgumentException("배치 메시지 형식 오류(최소 3세그먼트): " + raw);
        }
        long          batId         = Long.parseLong(parts[0]);
        String        jobClass      = parts[1];
        LocalDateTime executionTime = LocalDateTime.parse(parts[2], FMT);
        String        partitionKey  = parts.length == 4 ? parts[3] : null;
        return new BatchWorkMessage(batId, jobClass, executionTime, partitionKey);
    }

    /**
     * dedup용 Redis SET 키.
     * 파티션 배치는 파티션키까지 포함해야 같은 구간 중복 발행을 막는다.
     */
    public String dedupeKey() {
        String base = batId + ":" + executionTime.format(FMT);
        return partitionKey != null ? base + ":" + partitionKey : base;
    }
}
