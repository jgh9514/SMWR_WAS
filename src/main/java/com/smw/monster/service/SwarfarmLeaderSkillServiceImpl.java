package com.smw.monster.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.monster.dto.SwarfarmLeaderSkillResponse;
import com.smw.monster.mapper.SwarfarmLeaderSkillMapper;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmLeaderSkillServiceImpl implements SwarfarmLeaderSkillService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/leader-skills/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    
    @Autowired
    private SwarfarmLeaderSkillMapper swarfarmLeaderSkillMapper;
    
    @Autowired
    private SwarfarmApiClient swarfarmApiClient;

    @Autowired
    private SwarfarmSyncMetrics swarfarmSyncMetrics;

    @Value("${smw.swarfarm.request-delay-ms:250}")
    private long requestDelayMs;

    private Consumer<String> logCallback;

    @Override
    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    private void addBatchLog(String message, Object... args) {
        String logMessage = args != null && args.length > 0 ? String.format(message, args) : message;
        if (logCallback != null) {
            logCallback.accept(logMessage);
        } else {
            log.info(logMessage);
        }
    }
    
    @Override
    public int syncAllLeaderSkills() {
        addBatchLog("===== Swarfarm 리더 스킬 동기화 시작 =====");
        int totalSynced = 0;
        SwarfarmSyncMetrics.SyncStats stats = swarfarmSyncMetrics.newStats();
        Timer.Sample syncTimerSample = swarfarmSyncMetrics.startTimer();
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmLeaderSkillResponse firstResponse = fetchLeaderSkillData(firstPageUrl);
            
            if (firstResponse == null) {
                addBatchLog("오류: 첫 페이지 데이터를 가져올 수 없습니다.");
                throw new RuntimeException("첫 페이지 데이터를 가져올 수 없습니다.");
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            addBatchLog("전체 리더 스킬 수: %d, 예상 페이지 수: %d", totalCount, totalPages);
            Set<Integer> existingLeaderSkillIds = loadExistingLeaderSkillIds();
            
            // 모든 페이지 처리
            for (int page = 1; page <= totalPages; page++) {
                addBatchLog("페이지 %d 동기화 시작", page);
                int synced = syncLeaderSkillsByPage(page, existingLeaderSkillIds, stats);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                if (page < totalPages) {
                    pauseBetweenRequests();
                }
            }
            
            addBatchLog("===== Swarfarm 리더 스킬 동기화 완료. 총 %d개 동기화 =====", totalSynced);
            addBatchLog("처리=%d, 저장=%d, 스킵=%d, 실패=%d",
                    stats.getProcessed(), stats.getSaved(), stats.getSkipped(), stats.getFailed());
            swarfarmSyncMetrics.recordSummary("leader_skill", "SUCCESS", stats, syncTimerSample);
        } catch (Exception e) {
            addBatchLog("오류 발생: %s", e.getMessage());
            log.error("리더 스킬 동기화 중 오류 발생", e);
            stats.addFailed(1);
            swarfarmSyncMetrics.recordSummary("leader_skill", "FAILED", stats, syncTimerSample);
            throw new RuntimeException("리더 스킬 동기화 실패", e);
        }
        
        return totalSynced;
    }
    
    @Override
    public int syncLeaderSkillsByPage(int page) {
        return syncLeaderSkillsByPage(page, new HashSet<>(), swarfarmSyncMetrics.newStats());
    }

    private int syncLeaderSkillsByPage(int page, Set<Integer> existingLeaderSkillIds, SwarfarmSyncMetrics.SyncStats stats) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmLeaderSkillResponse response = fetchLeaderSkillData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            stats.addProcessed(response.getResults().size());
            for (SwarfarmLeaderSkillResponse.LeaderSkillData leaderSkill : response.getResults()) {
                try {
                    // 이미 존재하는 리더 스킬인지 확인
                    if (existingLeaderSkillIds.contains(leaderSkill.getId())) {
                        log.debug("리더 스킬 ID {}는 이미 존재합니다. 건너뜁니다.", leaderSkill.getId());
                        stats.addSkipped(1);
                        continue;
                    }
                    
                    // 리더 스킬 데이터 변환
                    Map<String, Object> leaderSkillData = convertToMap(leaderSkill);
                    
                    if (!saveLeaderSkill(leaderSkillData)) {
                        throw new IllegalStateException("리더 스킬 저장 실패 (DB 반환 false): " + leaderSkill.getId());
                    }
                    syncedCount++;
                    stats.addSaved(1);
                    existingLeaderSkillIds.add(leaderSkill.getId());
                } catch (Exception e) {
                    stats.addFailed(1);
                    log.error("리더 스킬 저장 중 오류 발생: {}", leaderSkill.getId(), e);
                    throw new RuntimeException("리더 스킬 저장 실패: " + leaderSkill.getId(), e);
                }
            }
            
            addBatchLog("페이지 %d 동기화 완료: 저장=%d, 누적 처리=%d, 누적 스킵=%d, 누적 실패=%d",
                    page, syncedCount, stats.getProcessed(), stats.getSkipped(), stats.getFailed());
            return syncedCount;
            
        } catch (Exception e) {
            stats.addFailed(1);
            log.error("페이지 {} 동기화 중 오류 발생", page, e);
            throw new RuntimeException("페이지 " + page + " 동기화 실패", e);
        }
    }

    private Set<Integer> loadExistingLeaderSkillIds() {
        return new HashSet<>(swarfarmLeaderSkillMapper.selectAllLeaderSkillIds());
    }

    private void pauseBetweenRequests() {
        if (requestDelayMs > 0) {
            try {
                Thread.sleep(requestDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("리더 스킬 동기화 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
    }
    
    @Override
    public boolean saveLeaderSkill(Map<String, Object> leaderSkillData) {
        try {
            // leader_skill_id는 Swarfarm API ID를 그대로 사용
            Integer leaderSkillId = (Integer) leaderSkillData.get("leader_skill_id");
            if (leaderSkillId == null) {
                log.warn("leader_skill_id가 없어서 저장할 수 없습니다.");
                return false;
            }
            
            // 기존 데이터 업데이트 또는 신규 삽입 (ON CONFLICT DO UPDATE는 변경 없으면 0 반환 가능)
            swarfarmLeaderSkillMapper.upsertLeaderSkill(leaderSkillData);
            return true;
        } catch (Exception e) {
            log.error("리더 스킬 저장 중 오류 발생", e);
            return false;
        }
    }
    
    @Override
    public boolean existsLeaderSkill(Integer leaderSkillId) {
        try {
            Integer count = swarfarmLeaderSkillMapper.countByLeaderSkillId(leaderSkillId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("리더 스킬 존재 확인 중 오류 발생", e);
            return false;
        }
    }
    
    @Override
    public int calculateTotalPages(int totalCount, int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalCount / pageSize);
    }
    
    /**
     * Swarfarm API에서 데이터 가져오기
     */
    private SwarfarmLeaderSkillResponse fetchLeaderSkillData(String apiUrl) {
        try {
            log.debug("API 호출: {}", apiUrl);
            SwarfarmLeaderSkillResponse res = swarfarmApiClient.fetchJson(apiUrl, SwarfarmLeaderSkillResponse.class);
            if (res == null) {
                throw new IllegalStateException("API 응답이 null입니다: " + apiUrl);
            }
            return res;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            throw new RuntimeException("Swarfarm 리더 스킬 API 호출 실패: " + apiUrl, e);
        }
    }
    
    /**
     * LeaderSkillData를 Map으로 변환
     */
    private Map<String, Object> convertToMap(SwarfarmLeaderSkillResponse.LeaderSkillData leaderSkill) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("leader_skill_id", leaderSkill.getId());
        map.put("attribute", leaderSkill.getAttribute());
        map.put("amount", leaderSkill.getAmount());
        map.put("area", leaderSkill.getArea());
        map.put("element", leaderSkill.getElement());
        map.put("swarfarm_url", leaderSkill.getUrl());
        map.put("crt_user_id", "SYSTEM");
        map.put("upt_user_id", "SYSTEM");
        
        return map;
    }
}

