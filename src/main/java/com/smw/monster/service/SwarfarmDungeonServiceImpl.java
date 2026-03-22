package com.smw.monster.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.monster.dto.SwarfarmDungeonResponse;
import com.smw.monster.mapper.SwarfarmDungeonMapper;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmDungeonServiceImpl implements SwarfarmDungeonService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/dungeons/";
    private static final String SWARFARM_IMAGE_BASE_URL = "https://swarfarm.com/static/herders/images/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기

    @Autowired
    private SwarfarmDungeonMapper swarfarmDungeonMapper;
    
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
    public int syncAllDungeons() {
        addBatchLog("===== Swarfarm 던전 동기화 시작 =====");
        int totalSynced = 0;
        SwarfarmSyncMetrics.SyncStats stats = swarfarmSyncMetrics.newStats();
        Timer.Sample syncTimerSample = swarfarmSyncMetrics.startTimer();
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmDungeonResponse firstResponse = fetchDungeonData(firstPageUrl);
            
            if (firstResponse == null) {
                log.error("첫 페이지 데이터를 가져올 수 없습니다.");
                throw new RuntimeException("첫 페이지 데이터를 가져올 수 없습니다.");
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            addBatchLog("전체 던전 수: %d, 예상 페이지 수: %d", totalCount, totalPages);
            Set<Integer> existingDungeonIds = loadExistingDungeonIds();
            
            // 모든 페이지 처리
            for (int page = 1; page <= totalPages; page++) {
                addBatchLog("페이지 %d 동기화 시작", page);
                int synced = syncDungeonsByPage(page, existingDungeonIds, stats);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                if (page < totalPages) {
                    pauseBetweenRequests();
                }
            }
            
            addBatchLog("===== Swarfarm 던전 동기화 완료. 총 %d개 동기화 =====", totalSynced);
            addBatchLog("처리=%d, 저장=%d, 스킵=%d, 실패=%d",
                    stats.getProcessed(), stats.getSaved(), stats.getSkipped(), stats.getFailed());
            swarfarmSyncMetrics.recordSummary("dungeon", "SUCCESS", stats, syncTimerSample);
            return totalSynced;
        } catch (Exception e) {
            log.error("던전 동기화 중 오류 발생", e);
            stats.addFailed(1);
            swarfarmSyncMetrics.recordSummary("dungeon", "FAILED", stats, syncTimerSample);
            throw new RuntimeException("던전 동기화 실패", e);
        }
    }
    
    @Override
    public int syncDungeonsByPage(int page) {
        return syncDungeonsByPage(page, new HashSet<>(), swarfarmSyncMetrics.newStats());
    }

    private int syncDungeonsByPage(int page, Set<Integer> existingDungeonIds, SwarfarmSyncMetrics.SyncStats stats) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmDungeonResponse response = fetchDungeonData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            stats.addProcessed(response.getResults().size());
            for (SwarfarmDungeonResponse.DungeonData dungeon : response.getResults()) {
                try {
                    // 이미 존재하는 던전인지 확인
                    if (existingDungeonIds.contains(dungeon.getId())) {
                        log.debug("던전 ID {}는 이미 존재합니다. 건너뜁니다.", dungeon.getId());
                        stats.addSkipped(1);
                        continue;
                    }
                    
                    // 던전 데이터 변환
                    Map<String, Object> dungeonData = convertToMap(dungeon);
                    
                    // 이미지 다운로드 (icon이 있는 경우만)
                    if (dungeon.getIcon() != null && !dungeon.getIcon().isEmpty()) {
                        try {
                            String imagePath = downloadDungeonImage(dungeon.getIcon());
                            dungeonData.put("icon_path", imagePath);
                        } catch (Exception e) {
                            log.error("이미지 다운로드 실패: {}", dungeon.getIcon(), e);
                            throw new RuntimeException("던전 이미지 다운로드 실패: " + dungeon.getIcon(), e);
                        }
                    }
                    
                    // DB 저장
                    if (saveDungeon(dungeonData)) {
                        syncedCount++;
                        stats.addSaved(1);
                        existingDungeonIds.add(dungeon.getId());
                        
                        // Levels 저장
                        if (dungeon.getLevels() != null && !dungeon.getLevels().isEmpty()) {
                            saveDungeonLevels(dungeon.getId(), dungeon.getLevels());
                        }
                    } else {
                        stats.addFailed(1);
                    }
                } catch (Exception e) {
                    stats.addFailed(1);
                    log.error("던전 저장 중 오류 발생: {}", dungeon.getId(), e);
                    throw new RuntimeException("던전 저장 실패: " + dungeon.getId(), e);
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

    private Set<Integer> loadExistingDungeonIds() {
        try {
            return new HashSet<>(swarfarmDungeonMapper.selectAllDungeonIds());
        } catch (Exception e) {
            log.warn("기존 던전 ID 조회 실패, 빈 Set 반환", e);
            return new HashSet<>();
        }
    }

    private void pauseBetweenRequests() {
        if (requestDelayMs > 0) {
            try {
                Thread.sleep(requestDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("던전 동기화 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
    }
    
    @Override
    public String downloadDungeonImage(String iconFilename) {
        try {
            String imageUrl = SWARFARM_IMAGE_BASE_URL + iconFilename;
            String cloudFrontUrl = swarfarmApiClient.downloadImageToS3(imageUrl, iconFilename, inferImageContentType(iconFilename));
            log.info("던전 이미지 S3 업로드 완료: {} -> {}", iconFilename, cloudFrontUrl);
            return cloudFrontUrl;
        } catch (Exception e) {
            log.error("이미지 다운로드 및 S3 업로드 중 오류 발생: {}", iconFilename, e);
            throw new RuntimeException("이미지 다운로드 실패", e);
        }
    }
    
    @Override
    public boolean saveDungeon(Map<String, Object> dungeonData) {
        try {
            // dungeon_id는 Swarfarm API ID를 그대로 사용
            Integer dungeonId = (Integer) dungeonData.get("dungeon_id");
            if (dungeonId == null) {
                log.warn("dungeon_id가 없어서 저장할 수 없습니다.");
                return false;
            }
            
            // 기존 데이터 업데이트 또는 신규 삽입
            int result = swarfarmDungeonMapper.upsertDungeon(dungeonData);
            
            return result > 0;
        } catch (Exception e) {
            log.error("던전 저장 중 오류 발생", e);
            return false;
        }
    }
    
    @Override
    public boolean existsDungeon(Integer dungeonId) {
        try {
            Integer count = swarfarmDungeonMapper.countByDungeonId(dungeonId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("던전 존재 확인 중 오류 발생", e);
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
    private SwarfarmDungeonResponse fetchDungeonData(String apiUrl) {
        try {
            log.debug("API 호출: {}", apiUrl);
            return swarfarmApiClient.fetchJson(apiUrl, SwarfarmDungeonResponse.class);
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            return null;
        }
    }

    private String inferImageContentType(String fileName) {
        String lowerFilename = fileName.toLowerCase();
        if (lowerFilename.endsWith(".png")) {
            return "image/png";
        }
        if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerFilename.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }
    
    /**
     * DungeonData를 Map으로 변환
     */
    private Map<String, Object> convertToMap(SwarfarmDungeonResponse.DungeonData dungeon) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("dungeon_id", dungeon.getId());
        map.put("enabled", dungeon.getEnabled());
        map.put("name", dungeon.getName());
        map.put("slug", dungeon.getSlug());
        map.put("category", dungeon.getCategory());
        map.put("icon", dungeon.getIcon());
        map.put("swarfarm_url", dungeon.getUrl());
        map.put("crt_user_id", "SYSTEM");
        map.put("upt_user_id", "SYSTEM");
        
        return map;
    }
    
    /**
     * 던전 레벨 저장
     */
    private void saveDungeonLevels(Integer dungeonId, List<Integer> levels) {
        try {
            // 기존 레벨 삭제
            swarfarmDungeonMapper.deleteDungeonLevels(dungeonId);

            List<Map<String, Object>> levelRows = new java.util.ArrayList<>();
            for (Integer levelId : levels) {
                Map<String, Object> levelData = new HashMap<>();
                levelData.put("dungeon_id", dungeonId);
                levelData.put("level_id", levelId);
                levelRows.add(levelData);
            }

            if (!levelRows.isEmpty()) {
                swarfarmDungeonMapper.insertDungeonLevelsBatch(levelRows);
            }
        } catch (Exception e) {
            log.error("던전 레벨 저장 중 오류 발생", e);
            throw new RuntimeException("던전 레벨 저장 실패: dungeonId=" + dungeonId, e);
        }
    }
}

