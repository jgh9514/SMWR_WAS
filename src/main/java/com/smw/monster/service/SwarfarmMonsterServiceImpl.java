package com.smw.monster.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.monster.dto.SwarfarmMonsterResponse;
import com.smw.monster.mapper.SwarfarmMonsterMapper;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmMonsterServiceImpl implements SwarfarmMonsterService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/monsters/";
    private static final String SWARFARM_IMAGE_BASE_URL = "https://swarfarm.com/static/herders/images/monsters/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    private static final int DEFAULT_IMAGE_DOWNLOAD_CONCURRENCY = 4;

    private static final class ExistingMonsterSnapshot {
        private final String imageFilename;
        private final String imageUrl;

        private ExistingMonsterSnapshot(String imageFilename, String imageUrl) {
            this.imageFilename = imageFilename;
            this.imageUrl = imageUrl;
        }
    }
    @Autowired
    private SwarfarmMonsterMapper swarfarmMonsterMapper;
    
    @Autowired
    private SwarfarmApiClient swarfarmApiClient;

    @Autowired
    private SwarfarmSyncMetrics swarfarmSyncMetrics;
    
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${smw.swarfarm.image-download-concurrency:4}")
    private int imageDownloadConcurrency;
    
    // 로그 콜백 (배치 실행 시 상세 로그 수집용)
    private Consumer<String> logCallback = null;

    private ExecutorService imageDownloadExecutor;
    
    @Override
    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    @PreDestroy
    public void shutdownExecutors() {
        if (imageDownloadExecutor != null) {
            imageDownloadExecutor.shutdown();
        }
    }
    
    /**
     * 로그 출력 (콜백이 있으면 콜백으로, 없으면 기본 로그로)
     */
    private void addBatchLog(String message, Object... args) {
        String logMessage = args.length > 0 ? String.format(message, args) : message;
        if (logCallback != null) {
            logCallback.accept(logMessage);
        } else {
            log.info(logMessage);
        }
    }
    
    @Override
    public int syncAllMonsters() {
        addBatchLog("===== Swarfarm 몬스터 동기화 시작 =====");
        long startTime = System.currentTimeMillis();
        int totalSynced = 0;
        SwarfarmSyncMetrics.SyncStats stats = swarfarmSyncMetrics.newStats();
        Timer.Sample syncTimerSample = swarfarmSyncMetrics.startTimer();
        
        try {
            // 첫 페이지로 전체 개수 확인
            addBatchLog("API 조회 시작: 첫 페이지 데이터 가져오기");
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmMonsterResponse firstResponse = fetchMonsterData(firstPageUrl);
            
            if (firstResponse == null) {
                addBatchLog("오류: 첫 페이지 데이터를 가져올 수 없습니다.");
                throw new RuntimeException("첫 페이지 데이터를 가져올 수 없습니다.");
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            addBatchLog("전체 몬스터 수: %d개, 예상 페이지 수: %d페이지", totalCount, totalPages);
            
            // 기존 몬스터 스냅샷 (신규/갱신 구분·이미지 재업로드 생략용)
            addBatchLog("기존 몬스터 데이터 조회 시작...");
            Map<Integer, ExistingMonsterSnapshot> existingSnapshots = loadExistingMonsterSnapshots();
            addBatchLog("기존 몬스터 수: %d개 (갱신 대상 포함)", existingSnapshots.size());
            
            int insertedCount = 0;
            int updatedCount = 0;
            
            // 모든 페이지 순차 처리 (에러 발생 시 즉시 중단 및 롤백)
            addBatchLog("페이지 순차 처리 시작 (신규·갱신 모두 반영)...");
            for (int page = 1; page <= totalPages; page++) {
                addBatchLog("페이지 %d 처리 시작...", page);
                PageSyncResult pageResult = syncMonstersByPage(page, existingSnapshots, stats);
                totalSynced += pageResult.savedCount();
                insertedCount += pageResult.insertedCount();
                updatedCount += pageResult.updatedCount();
                addBatchLog("페이지 %d 처리 완료: 저장=%d (신규=%d, 갱신=%d)",
                        page, pageResult.savedCount(), pageResult.insertedCount(), pageResult.updatedCount());
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            addBatchLog("===== Swarfarm 몬스터 동기화 완료 =====");
            addBatchLog("총 저장: %d개 (신규 %d, 갱신 %d)", totalSynced, insertedCount, updatedCount);
            addBatchLog("처리=%d, 저장=%d, 스킵=%d, 실패=%d",
                    stats.getProcessed(), stats.getSaved(), stats.getSkipped(), stats.getFailed());
            addBatchLog("소요 시간: %.2f초", elapsedTime / 1000.0);
            swarfarmSyncMetrics.recordSummary("monster", "SUCCESS", stats, syncTimerSample);
        } catch (Exception e) {
            addBatchLog("오류 발생: %s", e.getMessage());
            log.error("몬스터 동기화 중 오류 발생", e);
            stats.addFailed(1);
            swarfarmSyncMetrics.recordSummary("monster", "FAIL", stats, syncTimerSample);
            throw e; // 예외를 다시 던져서 트랜잭션 롤백 유도
        }
        
        return totalSynced;
    }
    
    /**
     * 기존 몬스터 Swarfarm ID별 스냅샷 (갱신·이미지 재업로드 생략 판단용)
     */
    private Map<Integer, ExistingMonsterSnapshot> loadExistingMonsterSnapshots() {
        List<Map<String, Object>> rows = swarfarmMonsterMapper.selectSwarfarmImageSnapshots();
        Map<Integer, ExistingMonsterSnapshot> snapshots = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Integer swarfarmId = toInteger(row.get("swarfarm_id"));
            if (swarfarmId == null) {
                continue;
            }
            snapshots.put(swarfarmId, new ExistingMonsterSnapshot(
                    row.get("image_filename") != null ? String.valueOf(row.get("image_filename")) : null,
                    row.get("image_url") != null ? String.valueOf(row.get("image_url")) : null));
        }
        return snapshots;
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record PageSyncResult(int savedCount, int insertedCount, int updatedCount) {}
    
    @Override
    public int syncMonstersByPage(int page) {
        return syncMonstersByPage(page, loadExistingMonsterSnapshots(), swarfarmSyncMetrics.newStats()).savedCount();
    }
    
    private PageSyncResult syncMonstersByPage(int page, Map<Integer, ExistingMonsterSnapshot> existingSnapshots,
            SwarfarmSyncMetrics.SyncStats stats) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmMonsterResponse response = fetchMonsterData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return new PageSyncResult(0, 0, 0);
            }
            
            stats.addProcessed(response.getResults().size());
            List<SwarfarmMonsterResponse.MonsterData> monsters = response.getResults();
            
            if (monsters.isEmpty()) {
                return new PageSyncResult(0, 0, 0);
            }
            
            log.info("페이지 {}: {}개 몬스터 동기화 (신규·갱신)", page, monsters.size());
            
            List<Map<String, Object>> monsterDataList = buildMonsterDataList(monsters, existingSnapshots);
            int skippedCount = monsters.size() - monsterDataList.size();
            
            if (skippedCount > 0) {
                addBatchLog("이미지 없음 또는 오류로 인해 %d개 몬스터 패스됨", skippedCount);
                stats.addSkipped(skippedCount);
            }
            
            SaveBatchResult batchResult = saveMonstersBatch(monsterDataList, existingSnapshots);
            stats.addSaved(batchResult.savedCount());
            
            addBatchLog("페이지 %d 처리 요약: 저장=%d (신규=%d, 갱신=%d), 누적 처리=%d, 누적 스킵=%d, 누적 실패=%d",
                    page, batchResult.savedCount(), batchResult.insertedCount(), batchResult.updatedCount(),
                    stats.getProcessed(), stats.getSkipped(), stats.getFailed());
            return new PageSyncResult(batchResult.savedCount(), batchResult.insertedCount(), batchResult.updatedCount());
            
        } catch (Exception e) {
            stats.addFailed(1);
            log.error("페이지 {} 동기화 중 오류 발생", page, e);
            throw new RuntimeException("페이지 " + page + " 동기화 실패", e);
        }
    }

    private record SaveBatchResult(int savedCount, int insertedCount, int updatedCount) {}

    private List<Map<String, Object>> buildMonsterDataList(List<SwarfarmMonsterResponse.MonsterData> monsters,
            Map<Integer, ExistingMonsterSnapshot> existingSnapshots) {
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        ExecutorService executor = getImageDownloadExecutor();

        for (SwarfarmMonsterResponse.MonsterData monster : monsters) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> prepareMonsterData(monster, existingSnapshots), executor));
        }

        List<Map<String, Object>> monsterDataList = new ArrayList<>();
        for (CompletableFuture<Map<String, Object>> future : futures) {
            try {
                Map<String, Object> monsterData = future.join();
                if (monsterData != null) {
                    monsterDataList.add(monsterData);
                }
            } catch (Exception e) {
                log.error("병렬 이미지 처리 결과 수집 중 오류 발생 (몬스터 저장 건너뜀): {}", e.getMessage(), e);
            }
        }
        return monsterDataList;
    }

    private Map<String, Object> prepareMonsterData(SwarfarmMonsterResponse.MonsterData monster,
            Map<Integer, ExistingMonsterSnapshot> existingSnapshots) {
        try {
            if (monster.getCom2usId() == null) {
                addBatchLog("com2us_id 없음으로 패스: swarfarm_id=%d, name=%s", monster.getId(), monster.getName());
                return null;
            }
            if (monster.getImageFilename() == null || monster.getElement() == null) {
                addBatchLog("이미지 정보 없음으로 패스: swarfarm_id=%d, name=%s, image_filename=%s, element=%s",
                        monster.getId(), monster.getName(), monster.getImageFilename(), monster.getElement());
                return null;
            }

            ExistingMonsterSnapshot existing = existingSnapshots.get(monster.getId());
            boolean isNew = existing == null;

            Map<String, Object> monsterData = convertToMap(monster);
            String imageUrl = resolveMonsterImageUrl(monster, existing);
            if (imageUrl == null || imageUrl.isEmpty()) {
                addBatchLog("이미지 다운로드 실패로 패스: swarfarm_id=%d, name=%s, image_filename=%s",
                        monster.getId(), monster.getName(), monster.getImageFilename());
                return null;
            }

            monsterData.put("image_url", imageUrl);
            monsterData.put("_skills", monster.getSkills());
            monsterData.put("_sources", monster.getSource());
            monsterData.put("_swarfarm_id", monster.getId());
            monsterData.put("_is_new", isNew);
            return monsterData;
        } catch (Exception e) {
            addBatchLog("몬스터 데이터 변환/이미지 처리 실패: swarfarm_id=%d, name=%s", monster.getId(), monster.getName());
            throw new RuntimeException("몬스터 데이터 준비 실패: swarfarm_id=" + monster.getId(), e);
        }
    }

    /**
     * 이미지 파일명이 같으면 기존 URL 재사용(S3 재업로드 생략), 변경·신규만 S3 업로드
     */
    private String resolveMonsterImageUrl(SwarfarmMonsterResponse.MonsterData monster,
            ExistingMonsterSnapshot existing) {
        if (existing != null
                && existing.imageFilename != null
                && existing.imageFilename.equals(monster.getImageFilename())
                && existing.imageUrl != null
                && !existing.imageUrl.isEmpty()) {
            return existing.imageUrl;
        }
        return downloadMonsterImage(monster.getImageFilename(), monster.getElement());
    }
    
    /**
     * 여러 몬스터를 배치로 저장 (성능 최적화)
     * 커넥션 풀 부족 방지를 위해 각 저장 작업 후 커넥션을 즉시 반환하도록 처리
     */
    private SaveBatchResult saveMonstersBatch(List<Map<String, Object>> monsterDataList,
            Map<Integer, ExistingMonsterSnapshot> existingSnapshots) {
        int savedCount = 0;
        int insertedCount = 0;
        int updatedCount = 0;
        for (Map<String, Object> monsterData : monsterDataList) {
            boolean isNew = Boolean.TRUE.equals(monsterData.get("_is_new"));
            String monsterId = saveMonsterInternal(monsterData);
            savedCount++;
            if (isNew) {
                insertedCount++;
            } else {
                updatedCount++;
            }

            Integer swarfarmId = (Integer) monsterData.get("swarfarm_id");
            if (swarfarmId != null && isNew) {
                existingSnapshots.put(swarfarmId, new ExistingMonsterSnapshot(
                        (String) monsterData.get("image_filename"),
                        (String) monsterData.get("image_url")));
            }

            @SuppressWarnings("unchecked")
            List<Integer> skills = (List<Integer>) monsterData.get("_skills");
            saveMonsterSkills(monsterId, skills != null ? skills : List.of());

            @SuppressWarnings("unchecked")
            List<SwarfarmMonsterResponse.SourceData> sources =
                    (List<SwarfarmMonsterResponse.SourceData>) monsterData.get("_sources");
            saveMonsterSources(monsterId, sources != null ? sources : List.of());
        }
        return new SaveBatchResult(savedCount, insertedCount, updatedCount);
    }
    
    @Override
    public String downloadMonsterImage(String imageFilename, String element) {
        try {
            String imageUrl = SWARFARM_IMAGE_BASE_URL + imageFilename;
            String cloudFrontUrl = swarfarmApiClient.downloadImageToS3(imageUrl, imageFilename, inferImageContentType(imageFilename));
            log.info("이미지 S3 업로드 완료: {} -> {}", imageFilename, cloudFrontUrl);
            return cloudFrontUrl;
        } catch (Exception e) {
            log.error("이미지 다운로드 및 S3 업로드 중 오류 발생: {} - URL: {}", imageFilename, SWARFARM_IMAGE_BASE_URL + imageFilename, e);
            throw new RuntimeException("이미지 다운로드 및 S3 업로드 실패: " + imageFilename, e);
        }
    }
    
    @Override
    public boolean saveMonster(Map<String, Object> monsterData) {
        return saveMonsterInternal(monsterData) != null;
    }

    private String saveMonsterInternal(Map<String, Object> monsterData) {
        try {
            Integer com2usId = (Integer) monsterData.get("com2us_id");
            if (com2usId == null) {
                throw new IllegalStateException("com2us_id가 없어서 저장할 수 없습니다.");
            }
            String monsterId = String.valueOf(com2usId);
            monsterData.put("monster_id", monsterId);
            
            // image_url이 null인 경우 기본값 설정 (NOT NULL 제약조건 대응)
            if (monsterData.get("image_url") == null) {
                String imageFilename = (String) monsterData.get("image_filename");
                String element = (String) monsterData.get("monster_elemental");
                
                if (imageFilename != null && element != null) {
                    // S3 경로로 기본값 설정
                    String defaultImageUrl = "https://dyjduzi8vf2k4.cloudfront.net/monster/" + imageFilename;
                    monsterData.put("image_url", defaultImageUrl);
                    log.debug("image_url이 null이어서 기본 경로 설정: {}", defaultImageUrl);
                } else {
                    // 최종 기본값
                    monsterData.put("image_url", "https://dyjduzi8vf2k4.cloudfront.net/monster/default/monster_default.png");
                    log.debug("image_url이 null이고 이미지 정보도 없어서 최종 기본값 설정");
                }
            }
            
            swarfarmMonsterMapper.upsertMonster(monsterData);
            return monsterId;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("몬스터 저장 중 오류 발생", e);
            throw new RuntimeException("몬스터 저장 실패", e);
        }
    }
    
    @Override
    public boolean existsMonster(Integer swarfarmId) {
        try {
            Integer count = swarfarmMonsterMapper.countBySwarfarmId(swarfarmId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("몬스터 존재 확인 중 오류 발생", e);
            return false;
        }
    }
    
    /**
     * 스레드 풀 종료 메서드 제거 (순차 처리로 변경하여 스레드 풀 불필요)
     */
    
    /**
     * Swarfarm API에서 데이터 가져오기
     */
    private SwarfarmMonsterResponse fetchMonsterData(String apiUrl) {
        try {
            log.debug("API 호출: {}", apiUrl);
            SwarfarmMonsterResponse res = swarfarmApiClient.fetchJson(apiUrl, SwarfarmMonsterResponse.class);
            if (res == null) {
                throw new IllegalStateException("API 응답이 null입니다: " + apiUrl);
            }
            return res;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            throw new RuntimeException("Swarfarm 몬스터 API 호출 실패: " + apiUrl, e);
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
        return "image/jpeg";
    }
    
    /**
     * MonsterData를 Map으로 변환
     */
    private Map<String, Object> convertToMap(SwarfarmMonsterResponse.MonsterData monster) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("swarfarm_id", monster.getId());
        map.put("com2us_id", monster.getCom2usId());
        map.put("family_id", monster.getFamilyId());
        map.put("skill_group_id", monster.getSkillGroupId());
        map.put("bestiary_slug", monster.getBestiarySlug());
        map.put("name", monster.getName());
        map.put("un_name", monster.getName()); // 영문명
        map.put("image_filename", monster.getImageFilename());
        map.put("monster_elemental", monster.getElement());
        map.put("archetype", monster.getArchetype());
        map.put("base_stars", monster.getBaseStars());
        map.put("natural_stars", monster.getNaturalStars());
        map.put("star", monster.getNaturalStars() != null ? monster.getNaturalStars() : monster.getBaseStars());
        map.put("obtainable", monster.getObtainable());
        map.put("can_awaken", monster.getCanAwaken());
        map.put("awaken_level", monster.getAwakenLevel());
        map.put("awaken_bonus", monster.getAwakenBonus());
        map.put("skill_ups_to_max", monster.getSkillUpsToMax());
        map.put("fusion_food", monster.getFusionFood());
        map.put("homunculus", monster.getHomunculus());
        
        // 스탯 정보
        map.put("base_hp", monster.getBaseHp());
        map.put("base_attack", monster.getBaseAttack());
        map.put("base_defense", monster.getBaseDefense());
        map.put("speed", monster.getSpeed());
        map.put("crit_rate", monster.getCritRate());
        map.put("crit_damage", monster.getCritDamage());
        map.put("resistance", monster.getResistance());
        map.put("accuracy", monster.getAccuracy());
        map.put("raw_hp", monster.getRawHp());
        map.put("raw_attack", monster.getRawAttack());
        map.put("raw_defense", monster.getRawDefense());
        map.put("max_lvl_hp", monster.getMaxLvlHp());
        map.put("max_lvl_attack", monster.getMaxLvlAttack());
        map.put("max_lvl_defense", monster.getMaxLvlDefense());
        
        // 각성 관련
        map.put("awakens_from_id", monster.getAwakensFrom());
        map.put("awakens_to_id", monster.getAwakensTo());
        map.put("transforms_to_id", monster.getTransformsTo());
        
        // URL
        map.put("swarfarm_url", monster.getUrl());
        
        // 기본값 설정
        map.put("kr_name", monster.getName()); // 한글명은 일단 영문명과 동일하게
        map.put("un_name_status", "BATCH"); // 배치 수집 데이터, 영문명 검증 필요
        map.put("usg_yn", "Y"); // 사용여부 기본값
        map.put("star_type", "Normal"); // 기본값
        map.put("arousal_type", monster.getAwakenLevel() != null && monster.getAwakenLevel() > 0 ? "Awakened" : "Normal");
        // image_url은 이미지 다운로드 후 설정됨 (downloadMonsterImage에서 반환된 경로 사용)
        map.put("crt_user_id", "SYSTEM");
        map.put("upt_user_id", "SYSTEM");
        
        return map;
    }
    
    /**
     * 몬스터 스킬 저장
     */
    private void saveMonsterSkills(String monsterId, List<Integer> skills) {
        try {
            // 기존 스킬 삭제
            swarfarmMonsterMapper.deleteMonsterSkillsByMonsterId(monsterId);

            List<Map<String, Object>> skillRows = new ArrayList<>();
            for (int i = 0; i < skills.size(); i++) {
                Map<String, Object> skillData = new HashMap<>();
                skillData.put("monster_id", monsterId);
                skillData.put("skill_id", skills.get(i));
                skillData.put("skill_order", i + 1);
                skillRows.add(skillData);
            }
            if (!skillRows.isEmpty()) {
                swarfarmMonsterMapper.insertMonsterSkillsBatch(skillRows);
            }
        } catch (Exception e) {
            log.error("스킬 저장 중 오류 발생", e);
            throw new RuntimeException("몬스터 스킬 저장 실패: monsterId=" + monsterId, e);
        }
    }
    
    /**
     * 몬스터 획득 경로 저장
     */
    private void saveMonsterSources(String monsterId, List<SwarfarmMonsterResponse.SourceData> sources) {
        try {
            // 기존 획득 경로 삭제
            swarfarmMonsterMapper.deleteMonsterSourcesByMonsterId(monsterId);

            List<Map<String, Object>> sourceRows = new ArrayList<>();
            for (int i = 0; i < sources.size(); i++) {
                SwarfarmMonsterResponse.SourceData source = sources.get(i);
                Map<String, Object> sourceData = new HashMap<>();
                sourceData.put("monster_id", monsterId);
                sourceData.put("source_id", source.getId());
                sourceData.put("source_name", source.getName());
                sourceData.put("source_description", source.getDescription());
                sourceData.put("farmable_source", source.getFarmableSource());
                sourceData.put("source_order", i + 1);
                sourceRows.add(sourceData);
            }
            if (!sourceRows.isEmpty()) {
                swarfarmMonsterMapper.insertMonsterSourcesBatch(sourceRows);
            }
        } catch (Exception e) {
            log.error("획득 경로 저장 중 오류 발생", e);
            throw new RuntimeException("몬스터 획득 경로 저장 실패: monsterId=" + monsterId, e);
        }
    }

    private ExecutorService getImageDownloadExecutor() {
        if (imageDownloadExecutor == null) {
            synchronized (this) {
                if (imageDownloadExecutor == null) {
                    int poolSize = Math.max(1, imageDownloadConcurrency > 0
                            ? imageDownloadConcurrency
                            : DEFAULT_IMAGE_DOWNLOAD_CONCURRENCY);
                    imageDownloadExecutor = Executors.newFixedThreadPool(poolSize);
                }
            }
        }
        return imageDownloadExecutor;
    }
    
    /**
     * 총 페이지 수 계산
     */
    @Override
    public int calculateTotalPages(int totalCount, int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalCount / pageSize);
    }
}

