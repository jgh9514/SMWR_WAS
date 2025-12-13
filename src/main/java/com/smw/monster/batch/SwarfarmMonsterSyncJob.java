package com.smw.monster.batch;

import org.quartz.JobExecutionContext;

import com.smw.monster.service.SwarfarmMonsterService;

/**
 * Swarfarm API에서 몬스터 데이터를 동기화하는 배치 Job
 * 하루에 한 번 실행되도록 스케줄링
 */
public class SwarfarmMonsterSyncJob extends BaseBatchJob {
    
    @Override
    protected void executeBatch(JobExecutionContext context) throws Exception {
        SwarfarmMonsterService swarfarmMonsterService = applicationContext.getBean(SwarfarmMonsterService.class);
        
        // 로그 콜백 설정 (서비스에서 로그를 받을 수 있도록)
        // 서비스의 addBatchLog가 호출되면 BaseBatchJob의 addLog를 통해 로그 수집
        swarfarmMonsterService.setLogCallback((msg) -> {
            // 서비스에서 받은 로그를 BaseBatchJob의 로그에 추가
            // addLog는 이미 타임스탬프를 추가하므로 메시지만 전달
            String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logContent.append("[").append(timestamp).append("] ").append(msg).append("\n");
        });
        
        // 서비스 메서드 호출 (서비스 내부에서 로그를 남김)
        int syncedCount = swarfarmMonsterService.syncAllMonsters();
        
        addLog("===== Swarfarm 몬스터 동기화 완료 =====");
        addLog("총 동기화된 몬스터 수: %d개", syncedCount);
    }
    
    @Override
    protected String getBatchName() {
        return "Swarfarm 몬스터 동기화";
    }
}

