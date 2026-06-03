package com.smw.monster.batch;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;

import com.smw.rta.cache.RtaRedisCacheWarmup;
import com.smw.rta.mapper.RtaMapper;
/**
 * 전체 소환사(약 17만 명) 시즌 summary 를 Redis 에 사전 적재.
 * <p>
 * fight_snap 재집계(강제) → Redis 전체 PUT 순서로 실행하여
 * 소환사 조회 첫 요청부터 캐시 히트를 보장한다.
 * fight_snap 쓰로틀({@code selectFightSnapMaxComputedAtForSeason}) 을 우회하여
 * 항상 전체 재집계를 수행한다.
 */
@DisallowConcurrentExecution
public class RtaAllPlayerRedisCacheWarmJob extends BaseBatchJob {

    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        RtaMapper rtaMapper = applicationContext.getBean(RtaMapper.class);
        RtaRedisCacheWarmup warmup = applicationContext.getBean(RtaRedisCacheWarmup.class);

        Long seasonId = rtaMapper.selectDefaultSeasonIdForNow();
        if (seasonId == null) {
            addLog("현재 시즌 없음 — 스킵");
            return;
        }
        addLog("fight_snap 전체 재집계 시작");
        rtaMapper.insertRtaSummonerSeasonFightSnapForSeason(seasonId);
        addLog("fight_snap 재집계 완료");

        addLog("Redis summary 워밍 시작");
        warmup.warmAllPlayerSummaries(seasonId);
        addLog("Redis summary 워밍 완료");

        addLog("Redis 랭킹 page-data 워밍 시작");
        warmup.warmTopPlayerPageData(seasonId, 500);
        addLog("Redis page-data 워밍 완료");
    }

    @Override
    protected String getBatchName() {
        return "RTA 전체 소환사 Redis 캐시 워밍";
    }
}
