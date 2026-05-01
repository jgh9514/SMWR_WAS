package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaCacheEvictor;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.service.RtaBatchAggregationService;

/**
 * {@code rta_agg_summoner_owned_box_snap} 수동 전량 재적재.
 * 정식 주기에서는 {@link RtaSummonerRankingAggJob}(매치 청크마다 증분 MERGE) 과 동선이 갈린다 —
 * 전량으로 맞출 때만 실행한다.
 * 원천: 수집 리플레이 {@code rta_match_unit_pick}(픽·밴 포함 DISTINCT 몬스터).
 */
@DisallowConcurrentExecution
public class RtaSummonerOwnedBoxSnapJob extends BaseBatchJob {

    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
        RtaBatchAggregationService aggregationService = applicationContext.getBean(RtaBatchAggregationService.class);
        RtaCacheEvictor rtaCacheEvictor = applicationContext.getBean(RtaCacheEvictor.class);

        addLog("--- rta_agg_summoner_owned_box_snap 전량 재적재(RTA 픽 기준) ---");
        RtaBatchAggregationService.SummonerOwnedBoxSnapRebuildResult box =
                aggregationService.rebuildSummonerOwnedBoxSnap(rtaMapper);
        addLog("rta_agg_summoner_owned_box_snap %d행", box.rows());

        rtaCacheEvictor.evictAllRtaCaches();
        addLog("RTA 조회 캐시 무효화 완료");
    }

    @Override
    protected String getBatchName() {
        return "RTA 소환사 보유 박스 스냅 수동 재적재(RTA 픽)";
    }
}
