package com.smw.monster.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.monster.dto.SwarfarmLevelResponse;
import com.smw.monster.mapper.SwarfarmLevelMapper;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmLevelServiceImpl implements SwarfarmLevelService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/levels/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    
    @Autowired
    private SwarfarmLevelMapper swarfarmLevelMapper;
    
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
    public int syncAllLevels() {
        addBatchLog("===== Swarfarm 레벨 동기화 시작 =====");
        int totalSynced = 0;
        SwarfarmSyncMetrics.SyncStats stats = swarfarmSyncMetrics.newStats();
        Timer.Sample syncTimerSample = swarfarmSyncMetrics.startTimer();
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmLevelResponse firstResponse = fetchLevelData(firstPageUrl);
            
            if (firstResponse == null) {
                addBatchLog("오류: 첫 페이지 데이터를 가져올 수 없습니다.");
                throw new RuntimeException("첫 페이지 데이터를 가져올 수 없습니다.");
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            addBatchLog("전체 레벨 수: %d, 예상 페이지 수: %d", totalCount, totalPages);
            Set<Integer> existingLevelIds = loadExistingLevelIds();
            
            // 모든 페이지 처리
            for (int page = 1; page <= totalPages; page++) {
                addBatchLog("페이지 %d 동기화 시작", page);
                int synced = syncLevelsByPage(page, existingLevelIds, stats);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                if (page < totalPages) {
                    pauseBetweenRequests();
                }
            }
            
            addBatchLog("===== Swarfarm 레벨 동기화 완료. 총 %d개 동기화 =====", totalSynced);
            addBatchLog("처리=%d, 저장=%d, 스킵=%d, 실패=%d",
                    stats.getProcessed(), stats.getSaved(), stats.getSkipped(), stats.getFailed());
            swarfarmSyncMetrics.recordSummary("level", "SUCCESS", stats, syncTimerSample);
        } catch (Exception e) {
            addBatchLog("오류 발생: %s", e.getMessage());
            log.error("레벨 동기화 중 오류 발생", e);
            stats.addFailed(1);
            swarfarmSyncMetrics.recordSummary("level", "FAILED", stats, syncTimerSample);
            throw new RuntimeException("레벨 동기화 실패", e);
        }
        
        return totalSynced;
    }
    
    @Override
    public int syncLevelsByPage(int page) {
        return syncLevelsByPage(page, new HashSet<>(), swarfarmSyncMetrics.newStats());
    }

    private int syncLevelsByPage(int page, Set<Integer> existingLevelIds, SwarfarmSyncMetrics.SyncStats stats) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmLevelResponse response = fetchLevelData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            stats.addProcessed(response.getResults().size());
            for (SwarfarmLevelResponse.LevelData level : response.getResults()) {
                try {
                    // 이미 존재하는 레벨인지 확인
                    if (existingLevelIds.contains(level.getId())) {
                        log.debug("레벨 ID {}는 이미 존재합니다. 건너뜁니다.", level.getId());
                        stats.addSkipped(1);
                        continue;
                    }
                    
                    // 레벨 데이터 변환
                    Map<String, Object> levelData = convertToMap(level);
                    
                    if (!saveLevel(levelData)) {
                        throw new IllegalStateException("레벨 저장 실패 (DB 반환 false): " + level.getId());
                    }
                    syncedCount++;
                    stats.addSaved(1);
                    existingLevelIds.add(level.getId());
                    if (level.getWaves() != null && !level.getWaves().isEmpty()) {
                        saveLevelWaves(level.getId(), level.getWaves());
                    }
                } catch (Exception e) {
                    stats.addFailed(1);
                    log.error("레벨 저장 중 오류 발생: {}", level.getId(), e);
                    throw new RuntimeException("레벨 저장 실패: " + level.getId(), e);
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

    private Set<Integer> loadExistingLevelIds() {
        return new HashSet<>(swarfarmLevelMapper.selectAllLevelIds());
    }

    private void pauseBetweenRequests() {
        if (requestDelayMs > 0) {
            try {
                Thread.sleep(requestDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("레벨 동기화 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
    }
    
    @Override
    public boolean saveLevel(Map<String, Object> levelData) {
        try {
            // level_id는 Swarfarm API ID를 그대로 사용
            Integer levelId = (Integer) levelData.get("level_id");
            if (levelId == null) {
                log.warn("level_id가 없어서 저장할 수 없습니다.");
                return false;
            }
            
            // 기존 데이터 업데이트 또는 신규 삽입
            int result = swarfarmLevelMapper.upsertLevel(levelData);
            
            return result > 0;
        } catch (Exception e) {
            log.error("레벨 저장 중 오류 발생", e);
            return false;
        }
    }
    
    @Override
    public boolean existsLevel(Integer levelId) {
        try {
            Integer count = swarfarmLevelMapper.countByLevelId(levelId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("레벨 존재 확인 중 오류 발생", e);
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
    private SwarfarmLevelResponse fetchLevelData(String apiUrl) {
        try {
            log.debug("API 호출: {}", apiUrl);
            SwarfarmLevelResponse res = swarfarmApiClient.fetchJson(apiUrl, SwarfarmLevelResponse.class);
            if (res == null) {
                throw new IllegalStateException("API 응답이 null입니다: " + apiUrl);
            }
            return res;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            throw new RuntimeException("Swarfarm 레벨 API 호출 실패: " + apiUrl, e);
        }
    }
    
    /**
     * LevelData를 Map으로 변환
     */
    private Map<String, Object> convertToMap(SwarfarmLevelResponse.LevelData level) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("level_id", level.getId());
        map.put("dungeon_id", level.getDungeon());
        map.put("floor", level.getFloor());
        map.put("difficulty", level.getDifficulty());
        map.put("energy_cost", level.getEnergyCost());
        map.put("xp", level.getXp());
        map.put("frontline_slots", level.getFrontlineSlots());
        map.put("backline_slots", level.getBacklineSlots());
        map.put("total_slots", level.getTotalSlots());
        map.put("swarfarm_url", level.getUrl());
        map.put("crt_user_id", "SYSTEM");
        map.put("upt_user_id", "SYSTEM");
        
        return map;
    }
    
    /**
     * 레벨 웨이브 및 적 몬스터 저장
     */
    private void saveLevelWaves(Integer levelId, List<SwarfarmLevelResponse.WaveData> waves) {
        try {
            // 기존 웨이브 및 적 삭제
            swarfarmLevelMapper.deleteLevelWaves(levelId);

            List<Map<String, Object>> waveRows = new ArrayList<>();
            List<Map<String, Object>> enemyRows = new ArrayList<>();
            for (int waveIndex = 0; waveIndex < waves.size(); waveIndex++) {
                SwarfarmLevelResponse.WaveData wave = waves.get(waveIndex);
                int waveNumber = waveIndex + 1;
                
                Map<String, Object> waveData = new HashMap<>();
                waveData.put("level_id", levelId);
                waveData.put("wave_number", waveNumber);
                waveRows.add(waveData);

                if (wave.getEnemies() != null && !wave.getEnemies().isEmpty()) {
                    for (SwarfarmLevelResponse.EnemyData enemy : wave.getEnemies()) {
                        Map<String, Object> enemyData = new HashMap<>();
                        enemyData.put("enemy_id", enemy.getId());
                        enemyData.put("level_id", levelId);
                        enemyData.put("wave_number", waveNumber);
                        enemyData.put("monster_swarfarm_id", enemy.getMonster());
                        enemyData.put("stars", enemy.getStars());
                        enemyData.put("level", enemy.getLevel());
                        enemyData.put("hp", enemy.getHp());
                        enemyData.put("attack", enemy.getAttack());
                        enemyData.put("defense", enemy.getDefense());
                        enemyData.put("speed", enemy.getSpeed());
                        enemyData.put("resist", enemy.getResist());
                        enemyData.put("crit_bonus", enemy.getCritBonus());
                        enemyData.put("crit_damage_reduction", enemy.getCritDamageReduction());
                        enemyData.put("accuracy_bonus", enemy.getAccuracyBonus());
                        enemyRows.add(enemyData);
                    }
                }
            }

            if (!waveRows.isEmpty()) {
                swarfarmLevelMapper.insertLevelWavesBatch(waveRows);
            }
            if (!enemyRows.isEmpty()) {
                swarfarmLevelMapper.insertLevelEnemiesBatch(enemyRows);
            }
        } catch (Exception e) {
            log.error("레벨 웨이브 저장 중 오류 발생", e);
            throw new RuntimeException("레벨 웨이브 저장 실패: levelId=" + levelId, e);
        }
    }
}

