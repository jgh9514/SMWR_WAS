package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.service.SwarfarmDungeonService;

/**
 * Swarfarm API에서 던전 데이터를 동기화하는 배치 Job
 * 하루에 한 번 실행되도록 스케줄링
 */
public class SwarfarmDungeonSyncJob extends BaseBatchJob {
    
    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        SwarfarmDungeonService swarfarmDungeonService = applicationContext.getBean(SwarfarmDungeonService.class);
        
        addLog("===== Swarfarm 던전 동기화 시작 =====");
        addLog("API 조회 시작...");
        int syncedCount = swarfarmDungeonService.syncAllDungeons();
        
        addLog("===== Swarfarm 던전 동기화 완료 =====");
        addLog("총 동기화된 던전 수: %d개", syncedCount);
    }
    
    @Override
    protected String getBatchName() {
        return "Swarfarm 던전 동기화";
    }
}

