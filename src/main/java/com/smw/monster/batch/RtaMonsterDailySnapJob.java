package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.cache.RtaRedisCacheWarmup;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.service.RtaBatchBacklogScaler;
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
        RtaBatchProperties batchProps = applicationContext.getBean(RtaBatchProperties.class);
        RtaBatchBacklogScaler backlogScaler = applicationContext.getBean(RtaBatchBacklogScaler.class);
        int pickSlotDrainBatchSize = batchProps.getPickSlotDrainBatchSize();

        addLog("--- rta_agg_monster_daily_snap 재적재 ---");
        int inserted = aggregationService.rebuildMonsterDailySnap(rtaMapper);
        addLog("daily snap 완료 — insertedDates=%d", inserted);

        RtaBatchBacklogScaler.RtaBatchBacklogCounts backlog = backlogScaler.snapshot();
        int pickSlotMaxRounds = backlogScaler.resolvePickSlotMaxRounds(
                backlog.pickSlotPending(), pickSlotDrainBatchSize, 1);

        addLog("--- pick slot drain (pending %,d, maxRounds=%d) ---", backlog.pickSlotPending(), pickSlotMaxRounds);
        int slotRids = aggregationService.drainPickSlotSnap(rtaMapper, pickSlotDrainBatchSize, pickSlotMaxRounds);
        addLog("pick slot snap 완료 — processedRids=%d", slotRids);

        addLog("--- monster top summoner snap ---");
        int topSummonerSeasons = aggregationService.rebuildMonsterTopSummonerSnap(rtaMapper, 5, 10);
        addLog("top summoner snap 완료 — seasonCount=%d", topSummonerSeasons);

        rtaCacheEvictor.evictAllRtaCaches();
        addLog("RTA 조회 캐시 무효화 완료");

        try {
            RtaRedisCacheWarmup warmup = applicationContext.getBean(RtaRedisCacheWarmup.class);
            warmup.warmMonsterDetailCaches();
            addLog("몬스터 daily-trend·pick-slot 캐시 워밍 완료");
        } catch (Exception e) {
            addLog("몬스터 캐시 워밍 skip (non-redis mode or error): %s", sanitizeErrorMessage(e));
        }
    }

    @Override
    protected String getBatchName() {
        return "RTA 몬스터 일별·슬롯 집계";
    }
}
