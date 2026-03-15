package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.service.SwarfarmLeaderSkillService;

/**
 * Swarfarm API에서 리더 스킬 데이터를 동기화하는 배치 Job
 * 하루에 한 번 실행되도록 스케줄링
 */
public class SwarfarmLeaderSkillSyncJob extends BaseBatchJob {
    
    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        SwarfarmLeaderSkillService swarfarmLeaderSkillService = applicationContext.getBean(SwarfarmLeaderSkillService.class);
        
        addLog("===== Swarfarm 리더 스킬 동기화 시작 =====");
        addLog("API 조회 시작...");
        int syncedCount = swarfarmLeaderSkillService.syncAllLeaderSkills();
        
        addLog("===== Swarfarm 리더 스킬 동기화 완료 =====");
        addLog("총 동기화된 리더 스킬 수: %d개", syncedCount);
    }
    
    @Override
    protected String getBatchName() {
        return "Swarfarm 리더 스킬 동기화";
    }
}

