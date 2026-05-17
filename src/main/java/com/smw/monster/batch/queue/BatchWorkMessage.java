package com.smw.monster.batch.queue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Redis BATCH_WORK_QUEUE에 저장되는 배치 일감 메시지.
 * <p>
 * 직렬화 형식: {@code "batId:jobClass:yyyyMMddHHmm"}
 * <ul>
 *   <li>batId       — sys_batch_config.bat_id</li>
 *   <li>jobClass    — sys_batch_config.job_class (Spring Bean 이름)</li>
 *   <li>executionTime — 발행 기준 실행 시각(분 단위 절삭)</li>
 * </ul>
 */
public record BatchWorkMessage(long batId, String jobClass, LocalDateTime executionTime) {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** Redis LIST 저장 형식으로 직렬화. */
    public String serialize() {
        return batId + ":" + jobClass + ":" + executionTime.format(FMT);
    }

    /** Redis에서 꺼낸 문자열을 파싱. */
    public static BatchWorkMessage deserialize(String raw) {
        // jobClass에 ':' 포함 가능 → 최대 3분할
        int first = raw.indexOf(':');
        int last  = raw.lastIndexOf(':');
        if (first < 0 || first == last) {
            throw new IllegalArgumentException("배치 메시지 형식 오류: " + raw);
        }
        long   batId         = Long.parseLong(raw.substring(0, first));
        String jobClass      = raw.substring(first + 1, last);
        LocalDateTime execTime = LocalDateTime.parse(raw.substring(last + 1), FMT);
        return new BatchWorkMessage(batId, jobClass, execTime);
    }

    /**
     * Redis SET(dedup) 저장 키.
     * jobClass 제외 — 같은 batId·시각이면 동일 일감.
     */
    public String dedupeKey() {
        return batId + ":" + executionTime.format(FMT);
    }
}
