package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import org.springframework.cache.CacheManager;

import com.smw.monster.service.MonsterCacheService;
import com.smw.monster.service.SwarfarmMonsterService;

/**
 * Swarfarm API에서 몬스터 데이터를 동기화하는 배치 Job
 * 하루에 한 번 실행되도록 스케줄링
 */
public class SwarfarmMonsterSyncJob extends BaseBatchJob {
    
    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        SwarfarmMonsterService swarfarmMonsterService = applicationContext.getBean(SwarfarmMonsterService.class);
        attachServiceLogCallback(swarfarmMonsterService);
        
        // 서비스 메서드 호출 (서비스 내부에서 로그를 남김)
        int syncedCount = swarfarmMonsterService.syncAllMonsters();

        addLog("===== Swarfarm 몬스터 동기화 완료 =====");
        addLog("총 동기화된 몬스터 수: %d개", syncedCount);

        if (syncedCount > 0) {
            addLog("신규 몬스터 %d개 감지 — MonsterCache·monsterList 캐시 갱신", syncedCount);
            applicationContext.getBean(MonsterCacheService.class).reload();
            CacheManager monsterListCacheManager = applicationContext.getBean("monsterListCacheManager", CacheManager.class);
            org.springframework.cache.Cache c = monsterListCacheManager.getCache("monsterList");
            if (c != null) c.clear();
            addLog("MonsterCache·monsterList 캐시 갱신 완료");
        }
    }
    
    @Override
    protected String getBatchName() {
        return "Swarfarm 몬스터 동기화";
    }
}

