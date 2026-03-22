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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.SwarfarmSkillResponse;
import com.smw.monster.mapper.SwarfarmSkillMapper;

import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmSkillServiceImpl implements SwarfarmSkillService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/skills/";
    private static final String SWARFARM_IMAGE_BASE_URL = "https://swarfarm.com/static/herders/images/skills/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    
    @Autowired
    private SwarfarmSkillMapper swarfarmSkillMapper;
    
    @Autowired
    private SwarfarmApiClient swarfarmApiClient;

    @Autowired
    private SwarfarmSyncMetrics swarfarmSyncMetrics;
    
    @Autowired
    private ObjectMapper objectMapper;

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
    public int syncAllSkills() {
        addBatchLog("===== Swarfarm 스킬 동기화 시작 =====");
        int totalSynced = 0;
        SwarfarmSyncMetrics.SyncStats stats = swarfarmSyncMetrics.newStats();
        Timer.Sample syncTimerSample = swarfarmSyncMetrics.startTimer();
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmSkillResponse firstResponse = fetchSkillData(firstPageUrl);
            
            if (firstResponse == null) {
                log.error("첫 페이지 데이터를 가져올 수 없습니다.");
                throw new RuntimeException("첫 페이지 데이터를 가져올 수 없습니다.");
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            addBatchLog("전체 스킬 수: %d, 예상 페이지 수: %d", totalCount, totalPages);
            Set<Integer> existingSwarfarmIds = loadExistingSwarfarmIds();
            
            // 첫 페이지 처리
            int synced = syncSkillsByPage(1, existingSwarfarmIds, stats);
            totalSynced += synced;
            
            // 나머지 페이지 처리
            for (int page = 2; page <= totalPages; page++) {
                addBatchLog("페이지 %d 동기화 시작", page);
                synced = syncSkillsByPage(page, existingSwarfarmIds, stats);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                pauseBetweenRequests();
            }
            
            addBatchLog("===== Swarfarm 스킬 동기화 완료. 총 %d개 동기화 =====", totalSynced);
            addBatchLog("처리=%d, 저장=%d, 스킵=%d, 실패=%d",
                    stats.getProcessed(), stats.getSaved(), stats.getSkipped(), stats.getFailed());
            swarfarmSyncMetrics.recordSummary("skill", "SUCCESS", stats, syncTimerSample);
            return totalSynced;
        } catch (Exception e) {
            log.error("스킬 동기화 중 오류 발생", e);
            stats.addFailed(1);
            swarfarmSyncMetrics.recordSummary("skill", "FAILED", stats, syncTimerSample);
            throw new RuntimeException("스킬 동기화 실패", e);
        }
    }
    
    @Override
    public int syncSkillsByPage(int page) {
        return syncSkillsByPage(page, new HashSet<>(), swarfarmSyncMetrics.newStats());
    }

    private int syncSkillsByPage(int page, Set<Integer> existingSwarfarmIds, SwarfarmSyncMetrics.SyncStats stats) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmSkillResponse response = fetchSkillData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            stats.addProcessed(response.getResults().size());
            for (SwarfarmSkillResponse.SkillData skill : response.getResults()) {
                try {
                    // 이미 존재하는 스킬인지 확인
                    if (existingSwarfarmIds.contains(skill.getId())) {
                        log.debug("스킬 ID {}는 이미 존재합니다. 건너뜁니다.", skill.getId());
                        stats.addSkipped(1);
                        continue;
                    }
                    
                    // 스킬 데이터 변환
                    Map<String, Object> skillData = convertToMap(skill);
                    
                    // 이미지 다운로드
                    if (skill.getIconFilename() != null && !skill.getIconFilename().isEmpty()) {
                        try {
                            String imagePath = downloadSkillImage(skill.getIconFilename());
                            skillData.put("icon_path", imagePath);
                        } catch (Exception e) {
                            log.error("이미지 다운로드 실패: {}", skill.getIconFilename(), e);
                            throw new RuntimeException("스킬 이미지 다운로드 실패: " + skill.getIconFilename(), e);
                        }
                    }
                    
                    // DB 저장
                    String skillId = saveSkillInternal(skillData);
                    if (skillId != null) {
                        syncedCount++;
                        stats.addSaved(1);
                        existingSwarfarmIds.add(skill.getId());
                        
                        // Upgrades 저장
                        if (skill.getUpgrades() != null && !skill.getUpgrades().isEmpty()) {
                            saveSkillUpgrades(skillId, skill.getUpgrades());
                        }
                        
                        // Effects 저장
                        if (skill.getEffects() != null && !skill.getEffects().isEmpty()) {
                            saveSkillEffects(skillId, skill.getEffects());
                        }
                        
                        // Used On 저장
                        if (skill.getUsedOn() != null && !skill.getUsedOn().isEmpty()) {
                            saveSkillUsedOn(skillId, skill.getUsedOn());
                        }
                    } else {
                        stats.addFailed(1);
                    }
                } catch (Exception e) {
                    stats.addFailed(1);
                    log.error("스킬 저장 중 오류 발생: {}", skill.getId(), e);
                    throw new RuntimeException("스킬 저장 실패: " + skill.getId(), e);
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

    private Set<Integer> loadExistingSwarfarmIds() {
        try {
            return new HashSet<>(swarfarmSkillMapper.selectAllSwarfarmIds());
        } catch (Exception e) {
            log.warn("기존 스킬 ID 조회 실패, 빈 Set 반환", e);
            return new HashSet<>();
        }
    }

    private void pauseBetweenRequests() {
        if (requestDelayMs > 0) {
            try {
                Thread.sleep(requestDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("스킬 동기화 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
    }
    
    @Override
    public String downloadSkillImage(String iconFilename) {
        try {
            String imageUrl = SWARFARM_IMAGE_BASE_URL + iconFilename;
            String cloudFrontUrl = swarfarmApiClient.downloadImageToS3(imageUrl, iconFilename, inferImageContentType(iconFilename));
            log.info("스킬 이미지 S3 업로드 완료: {} -> {}", iconFilename, cloudFrontUrl);
            return cloudFrontUrl;
        } catch (Exception e) {
            log.error("이미지 다운로드 및 S3 업로드 중 오류 발생: {}", iconFilename, e);
            throw new RuntimeException("이미지 다운로드 실패", e);
        }
    }
    
    @Override
    public boolean saveSkill(Map<String, Object> skillData) {
        return saveSkillInternal(skillData) != null;
    }

    private String saveSkillInternal(Map<String, Object> skillData) {
        try {
            // skill_id 생성 (com2us_id를 문자열로 변환)
            Integer com2usId = (Integer) skillData.get("com2us_id");
            if (com2usId == null) {
                log.warn("com2us_id가 없어서 저장할 수 없습니다.");
                return null;
            }
            
            String skillId = String.valueOf(com2usId);
            skillData.put("skill_id", skillId);
            
            // 기존 데이터 업데이트 또는 신규 삽입
            int result = swarfarmSkillMapper.upsertSkill(skillData);
            
            return result > 0 ? skillId : null;
        } catch (Exception e) {
            log.error("스킬 저장 중 오류 발생", e);
            return null;
        }
    }
    
    @Override
    public boolean existsSkill(Integer swarfarmId) {
        try {
            Integer count = swarfarmSkillMapper.countBySwarfarmId(swarfarmId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("스킬 존재 확인 중 오류 발생", e);
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
    private SwarfarmSkillResponse fetchSkillData(String apiUrl) {
        try {
            log.debug("API 호출: {}", apiUrl);
            return swarfarmApiClient.fetchJson(apiUrl, SwarfarmSkillResponse.class);
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
     * SkillData를 Map으로 변환
     */
    private Map<String, Object> convertToMap(SwarfarmSkillResponse.SkillData skill) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("swarfarm_id", skill.getId());
        map.put("com2us_id", skill.getCom2usId());
        map.put("name", skill.getName());
        map.put("description", skill.getDescription());
        map.put("slot", skill.getSlot());
        map.put("cooltime", skill.getCooltime());
        map.put("hits", skill.getHits());
        map.put("passive", skill.getPassive());
        map.put("aoe", skill.getAoe());
        map.put("random", skill.getRandom());
        map.put("max_level", skill.getMaxLevel());
        map.put("multiplier_formula", skill.getMultiplierFormula());
        map.put("multiplier_formula_raw", skill.getMultiplierFormulaRaw());
        map.put("icon_filename", skill.getIconFilename());
        map.put("other_skill_id", skill.getOtherSkill());
        map.put("swarfarm_url", skill.getUrl());
        
        // JSON 배열을 문자열로 변환
        if (skill.getScalesWith() != null) {
            try {
                map.put("scales_with", objectMapper.writeValueAsString(skill.getScalesWith()));
            } catch (Exception e) {
                log.warn("scales_with 변환 실패", e);
            }
        }
        
        if (skill.getLevelProgressDescription() != null) {
            try {
                map.put("level_progress_description", objectMapper.writeValueAsString(skill.getLevelProgressDescription()));
            } catch (Exception e) {
                log.warn("level_progress_description 변환 실패", e);
            }
        }
        
        map.put("crt_user_id", "SYSTEM");
        map.put("upt_user_id", "SYSTEM");
        
        return map;
    }
    
    /**
     * 스킬 업그레이드 저장
     */
    private void saveSkillUpgrades(String skillId, List<SwarfarmSkillResponse.UpgradeData> upgrades) {
        try {
            // 기존 업그레이드 삭제
            swarfarmSkillMapper.deleteSkillUpgrades(skillId);

            List<Map<String, Object>> upgradeRows = new java.util.ArrayList<>();
            for (int i = 0; i < upgrades.size(); i++) {
                SwarfarmSkillResponse.UpgradeData upgrade = upgrades.get(i);
                Map<String, Object> upgradeData = new HashMap<>();
                upgradeData.put("skill_id", skillId);
                upgradeData.put("upgrade_level", i + 1);
                upgradeData.put("effect", upgrade.getEffect());
                upgradeData.put("amount", upgrade.getAmount());
                upgradeRows.add(upgradeData);
            }
            if (!upgradeRows.isEmpty()) {
                swarfarmSkillMapper.insertSkillUpgradesBatch(upgradeRows);
            }
        } catch (Exception e) {
            log.error("스킬 업그레이드 저장 중 오류 발생", e);
            throw new RuntimeException("스킬 업그레이드 저장 실패: skillId=" + skillId, e);
        }
    }
    
    /**
     * 스킬 효과 저장
     */
    private void saveSkillEffects(String skillId, List<SwarfarmSkillResponse.EffectData> effects) {
        try {
            // 기존 효과 삭제
            swarfarmSkillMapper.deleteSkillEffects(skillId);

            List<Map<String, Object>> effectRows = new java.util.ArrayList<>();
            for (int i = 0; i < effects.size(); i++) {
                SwarfarmSkillResponse.EffectData effect = effects.get(i);
                if (effect.getEffect() == null) {
                    continue;
                }
                
                Map<String, Object> effectData = new HashMap<>();
                effectData.put("skill_id", skillId);
                effectData.put("effect_id", effect.getEffect().getId());
                effectData.put("effect_name", effect.getEffect().getName());
                effectData.put("effect_type", effect.getEffect().getType());
                effectData.put("effect_description", effect.getEffect().getDescription());
                effectData.put("is_buff", effect.getEffect().getIsBuff());
                effectData.put("aoe", effect.getAoe());
                effectData.put("single_target", effect.getSingleTarget());
                effectData.put("self_effect", effect.getSelfEffect());
                effectData.put("chance", effect.getChance());
                effectData.put("on_crit", effect.getOnCrit());
                effectData.put("on_death", effect.getOnDeath());
                effectData.put("random", effect.getRandom());
                effectData.put("quantity", effect.getQuantity());
                effectData.put("all", effect.getAll());
                effectData.put("self_hp", effect.getSelfHp());
                effectData.put("target_hp", effect.getTargetHp());
                effectData.put("damage", effect.getDamage());
                effectData.put("note", effect.getNote());
                effectData.put("effect_order", i + 1);
                effectRows.add(effectData);
            }
            if (!effectRows.isEmpty()) {
                swarfarmSkillMapper.insertSkillEffectsBatch(effectRows);
            }
        } catch (Exception e) {
            log.error("스킬 효과 저장 중 오류 발생", e);
            throw new RuntimeException("스킬 효과 저장 실패: skillId=" + skillId, e);
        }
    }
    
    /**
     * 스킬 사용 몬스터 저장
     */
    private void saveSkillUsedOn(String skillId, List<Integer> usedOn) {
        try {
            // 기존 매핑 삭제
            swarfarmSkillMapper.deleteSkillUsedOn(skillId);

            List<Map<String, Object>> usedOnRows = new java.util.ArrayList<>();
            for (Integer monsterSwarfarmId : usedOn) {
                Map<String, Object> usedOnData = new HashMap<>();
                usedOnData.put("skill_id", skillId);
                usedOnData.put("monster_swarfarm_id", monsterSwarfarmId);
                usedOnRows.add(usedOnData);
            }
            if (!usedOnRows.isEmpty()) {
                swarfarmSkillMapper.insertSkillUsedOnBatch(usedOnRows);
            }
        } catch (Exception e) {
            log.error("스킬 사용 몬스터 저장 중 오류 발생", e);
            throw new RuntimeException("스킬 사용 몬스터 저장 실패: skillId=" + skillId, e);
        }
    }
}

