package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.service.SwarfarmSkillEffectService;

/**
 * Swarfarm API에서 스킬 이펙트 데이터를 동기화하는 배치 Job
 * 하루에 한 번 실행되도록 스케줄링
 */
public class SwarfarmSkillEffectSyncJob extends BaseBatchJob {
    
    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        SwarfarmSkillEffectService swarfarmSkillEffectService = applicationContext.getBean(SwarfarmSkillEffectService.class);
        
        addLog("===== Swarfarm 스킬 이펙트 동기화 시작 =====");
        addLog("API 조회 시작...");
        int syncedCount = swarfarmSkillEffectService.syncAllSkillEffects();
        
        addLog("===== Swarfarm 스킬 이펙트 동기화 완료 =====");
        addLog("총 동기화된 스킬 이펙트 수: %d개", syncedCount);
    }
    
    @Override
    protected String getBatchName() {
        return "Swarfarm 스킬 이펙트 동기화";
    }
}

