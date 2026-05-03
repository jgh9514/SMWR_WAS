package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * 몬스터 일별 집계 ({@code rta_agg_monster_daily_snap}) 및
 * 슬롯별 집계 ({@code rta_agg_monster_pick_slot_snap}) 재적재.
 * <p>
 * 매일 00:30 등 한 번만 돌리면 충분하다.
 * DB 등록: {@code sql/migrate_add_rta_monster_daily_snap.sql} 에서 {@code sys_batch_config} 삽입.
 */
@DisallowConcurrentExecution
public class RtaMonsterDailySnapJob extends BaseBatchJob {

    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
        RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
        RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

        addLog("--- rta_agg_monster_daily_snap 재적재 (시즌 시작일~오늘 누락분) ---");
        long t0 = System.currentTimeMillis();
        int inserted = aggregationService.rebuildMonsterDailySnap(rtaMapper);
        addLog("daily snap 완료: insertedDates=%d, elapsed=%dms", inserted, System.currentTimeMillis() - t0);

        addLog("--- rta_agg_monster_pick_slot_snap 재적재 ---");
        long t1 = System.currentTimeMillis();
        int slotSeasons = aggregationService.rebuildMonsterPickSlotSnap(rtaMapper);
        addLog("pick slot snap 완료: seasonCount=%d, elapsed=%dms", slotSeasons, System.currentTimeMillis() - t1);

        rtaCacheEvictor.evictAllRtaCaches();
        addLog("RTA 조회 캐시 무효화 완료");
    }

    @Override
    protected String getBatchName() {
        return "RTA 몬스터 일별·슬롯 집계";
    }
}
