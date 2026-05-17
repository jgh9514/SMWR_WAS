package com.smw.monster.batch.queue;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Redis 큐 기반 배치 시스템 전용 DB 조작 Mapper.
 * <p>
 * 기존 {@link com.admin.batch.mapper.BatchMapper}와 네임스페이스를 분리하여
 * queue_yn='Y' 조회 / last_scheduled_at·last_executed_at 갱신만 담당한다.
 */
@Mapper
public interface BatchQueueMapper {

    /** queue_yn='Y' 이고 use_yn='Y' 인 배치 설정 전체 조회. */
    List<BatchQueueConfig> selectQueueEnabledBatches();

    /**
     * Producer가 발행 완료 후 last_scheduled_at을 갱신.
     * catch-up 계산의 기준이 된다.
     */
    void updateLastScheduledAt(@Param("batId") long batId,
                               @Param("lastScheduledAt") LocalDateTime lastScheduledAt);

    /**
     * Worker가 job 실행 완료 후 last_executed_at을 갱신.
     * 지연 감지·모니터링용.
     */
    void updateLastExecutedAt(@Param("batId") long batId,
                              @Param("lastExecutedAt") LocalDateTime lastExecutedAt);

    /**
     * 시너지 파티션용: synergy_applied_at IS NULL 인 replay_id 의 MIN/MAX 조회.
     * 처리할 데이터가 없으면 null 반환.
     */
    SynergyPendingRange selectSynergyPendingRidRange();

    /** 시너지 pending rid 범위. */
    record SynergyPendingRange(long minRid, long maxRid) {}

    /** 배치 설정 Row 매핑용 내부 레코드. */
    record BatchQueueConfig(
            long   batId,
            String batNm,
            String jobClass,
            int    intervalMinutes,
            LocalDateTime lastScheduledAt
    ) {}
}
