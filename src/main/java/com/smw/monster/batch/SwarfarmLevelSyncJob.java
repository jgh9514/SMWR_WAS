package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.service.SwarfarmLevelService;

/**
 * Swarfarm API에서 레벨(던전 웨이브별 몬스터 정보) 데이터를 동기화하는 배치 Job
 * 하루에 한 번 실행되도록 스케줄링
 */
public class SwarfarmLevelSyncJob extends BaseBatchJob {
    
    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        SwarfarmLevelService swarfarmLevelService = applicationContext.getBean(SwarfarmLevelService.class);
        
        addLog("===== Swarfarm 레벨 동기화 시작 =====");
        addLog("API 조회 시작...");
        int syncedCount = swarfarmLevelService.syncAllLevels();
        
        addLog("===== Swarfarm 레벨 동기화 완료 =====");
        addLog("총 동기화된 레벨 수: %d개", syncedCount);
    }
    
    @Override
    protected String getBatchName() {
        return "Swarfarm 레벨 동기화";
    }
}

