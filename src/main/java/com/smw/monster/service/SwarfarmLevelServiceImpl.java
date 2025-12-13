package com.smw.monster.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.SwarfarmLevelResponse;
import com.smw.monster.mapper.SwarfarmLevelMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SwarfarmLevelServiceImpl implements SwarfarmLevelService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/levels/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    
    @Autowired
    private SwarfarmLevelMapper swarfarmLevelMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public int syncAllLevels() {
        log.info("===== Swarfarm 레벨 동기화 시작 =====");
        int totalSynced = 0;
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmLevelResponse firstResponse = fetchLevelData(firstPageUrl);
            
            if (firstResponse == null) {
                log.error("첫 페이지 데이터를 가져올 수 없습니다.");
                return 0;
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            log.info("전체 레벨 수: {}, 예상 페이지 수: {}", totalCount, totalPages);
            
            // 모든 페이지 처리
            for (int page = 1; page <= totalPages; page++) {
                log.info("페이지 {} 동기화 시작", page);
                int synced = syncLevelsByPage(page);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                if (page < totalPages) {
                    Thread.sleep(500);
                }
            }
            
            log.info("===== Swarfarm 레벨 동기화 완료. 총 {}개 동기화 =====", totalSynced);
        } catch (Exception e) {
            log.error("레벨 동기화 중 오류 발생", e);
        }
        
        return totalSynced;
    }
    
    @Override
    public int syncLevelsByPage(int page) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmLevelResponse response = fetchLevelData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            for (SwarfarmLevelResponse.LevelData level : response.getResults()) {
                try {
                    // 이미 존재하는 레벨인지 확인
                    if (existsLevel(level.getId())) {
                        log.debug("레벨 ID {}는 이미 존재합니다. 건너뜁니다.", level.getId());
                        continue;
                    }
                    
                    // 레벨 데이터 변환
                    Map<String, Object> levelData = convertToMap(level);
                    
                    // DB 저장
                    if (saveLevel(levelData)) {
                        syncedCount++;
                        
                        // Waves 저장
                        if (level.getWaves() != null && !level.getWaves().isEmpty()) {
                            saveLevelWaves(level.getId(), level.getWaves());
                        }
                    }
                } catch (Exception e) {
                    log.error("레벨 저장 중 오류 발생: {}", level.getId(), e);
                }
            }
            
            log.info("페이지 {} 동기화 완료. {}개 저장", page, syncedCount);
            return syncedCount;
            
        } catch (Exception e) {
            log.error("페이지 {} 동기화 중 오류 발생", page, e);
            return 0;
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
            String response = restTemplate.getForObject(apiUrl, String.class);
            return objectMapper.readValue(response, SwarfarmLevelResponse.class);
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            return null;
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
            
            // 새 웨이브 및 적 저장
            for (int waveIndex = 0; waveIndex < waves.size(); waveIndex++) {
                SwarfarmLevelResponse.WaveData wave = waves.get(waveIndex);
                int waveNumber = waveIndex + 1;
                
                // 웨이브 저장
                Map<String, Object> waveData = new HashMap<>();
                waveData.put("level_id", levelId);
                waveData.put("wave_number", waveNumber);
                swarfarmLevelMapper.insertLevelWave(waveData);
                
                // 적 몬스터 저장
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
                        swarfarmLevelMapper.insertLevelEnemy(enemyData);
                    }
                }
            }
        } catch (Exception e) {
            log.error("레벨 웨이브 저장 중 오류 발생", e);
        }
    }
}

