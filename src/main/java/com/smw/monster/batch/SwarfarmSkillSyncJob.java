package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.service.SwarfarmSkillService;

/**
 * Swarfarm API에서 스킬 데이터를 동기화하는 배치 Job
 * 하루에 한 번 실행되도록 스케줄링
 */
public class SwarfarmSkillSyncJob extends BaseBatchJob {
    
    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        SwarfarmSkillService swarfarmSkillService = applicationContext.getBean(SwarfarmSkillService.class);
        
        addLog("===== Swarfarm 스킬 동기화 시작 =====");
        addLog("API 조회 시작...");
        int syncedCount = swarfarmSkillService.syncAllSkills();
        
        addLog("===== Swarfarm 스킬 동기화 완료 =====");
        addLog("총 동기화된 스킬 수: %d개", syncedCount);
    }
    
    @Override
    protected String getBatchName() {
        return "Swarfarm 스킬 동기화";
    }
}

