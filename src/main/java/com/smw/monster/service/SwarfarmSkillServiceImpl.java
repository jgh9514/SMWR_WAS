package com.smw.monster.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.SwarfarmSkillResponse;
import com.smw.monster.mapper.SwarfarmSkillMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SwarfarmSkillServiceImpl implements SwarfarmSkillService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/skills/";
    private static final String SWARFARM_IMAGE_BASE_URL = "https://swarfarm.com/static/herders/images/skills/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    
    @Autowired
    private SwarfarmSkillMapper swarfarmSkillMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String getImageBasePath() {
        try {
            // 클래스패스에서 resources 경로 찾기
            String resourcePath = getClass().getClassLoader().getResource("static/images/skills").getPath();
            // Windows 경로 처리
            if (resourcePath.startsWith("/") && System.getProperty("os.name").toLowerCase().contains("win")) {
                resourcePath = resourcePath.substring(1);
            }
            return resourcePath;
        } catch (Exception e) {
            // 대체 경로
            String projectRoot = System.getProperty("user.dir");
            return projectRoot + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "static" + File.separator + "images" + File.separator + "skills";
        }
    }
    
    @Override
    public int syncAllSkills() {
        log.info("===== Swarfarm 스킬 동기화 시작 =====");
        int totalSynced = 0;
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmSkillResponse firstResponse = fetchSkillData(firstPageUrl);
            
            if (firstResponse == null) {
                log.error("첫 페이지 데이터를 가져올 수 없습니다.");
                return 0;
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            log.info("전체 스킬 수: {}, 예상 페이지 수: {}", totalCount, totalPages);
            
            // 첫 페이지 처리
            int synced = syncSkillsByPage(1);
            totalSynced += synced;
            
            // 나머지 페이지 처리
            for (int page = 2; page <= totalPages; page++) {
                log.info("페이지 {} 동기화 시작", page);
                synced = syncSkillsByPage(page);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                Thread.sleep(500);
            }
            
            log.info("===== Swarfarm 스킬 동기화 완료. 총 {}개 동기화 =====", totalSynced);
        } catch (Exception e) {
            log.error("스킬 동기화 중 오류 발생", e);
        }
        
        return totalSynced;
    }
    
    @Override
    public int syncSkillsByPage(int page) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmSkillResponse response = fetchSkillData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            for (SwarfarmSkillResponse.SkillData skill : response.getResults()) {
                try {
                    // 이미 존재하는 스킬인지 확인
                    if (existsSkill(skill.getId())) {
                        log.debug("스킬 ID {}는 이미 존재합니다. 건너뜁니다.", skill.getId());
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
                            log.warn("이미지 다운로드 실패: {}", skill.getIconFilename(), e);
                        }
                    }
                    
                    // DB 저장
                    if (saveSkill(skillData)) {
                        syncedCount++;
                        
                        // Upgrades 저장
                        if (skill.getUpgrades() != null && !skill.getUpgrades().isEmpty()) {
                            saveSkillUpgrades(skill.getId(), skill.getUpgrades());
                        }
                        
                        // Effects 저장
                        if (skill.getEffects() != null && !skill.getEffects().isEmpty()) {
                            saveSkillEffects(skill.getId(), skill.getEffects());
                        }
                        
                        // Used On 저장
                        if (skill.getUsedOn() != null && !skill.getUsedOn().isEmpty()) {
                            saveSkillUsedOn(skill.getId(), skill.getUsedOn());
                        }
                    }
                } catch (Exception e) {
                    log.error("스킬 저장 중 오류 발생: {}", skill.getId(), e);
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
    public String downloadSkillImage(String iconFilename) {
        try {
            String imageUrl = SWARFARM_IMAGE_BASE_URL + iconFilename;
            String basePath = getImageBasePath();
            
            // skills 폴더 경로 생성
            Path imageDir = Paths.get(basePath);
            
            // 폴더가 없으면 생성
            if (!Files.exists(imageDir)) {
                Files.createDirectories(imageDir);
            }
            
            // 이미지 파일 경로
            Path imagePath = imageDir.resolve(iconFilename);
            
            // 이미 존재하는 파일이면 건너뜀
            if (Files.exists(imagePath)) {
                log.debug("이미지가 이미 존재합니다: {}", imagePath);
                return "/images/skills/" + iconFilename;
            }
            
            // 이미지 다운로드
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = connection.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(imagePath.toFile())) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                
                log.info("이미지 다운로드 완료: {}", imagePath);
                return "/images/skills/" + iconFilename;
            } else {
                log.warn("이미지 다운로드 실패. HTTP 응답 코드: {}", responseCode);
                return null;
            }
            
        } catch (Exception e) {
            log.error("이미지 다운로드 중 오류 발생: {}", iconFilename, e);
            throw new RuntimeException("이미지 다운로드 실패", e);
        }
    }
    
    @Override
    public boolean saveSkill(Map<String, Object> skillData) {
        try {
            // skill_id 생성 (com2us_id를 문자열로 변환)
            Integer com2usId = (Integer) skillData.get("com2us_id");
            if (com2usId == null) {
                log.warn("com2us_id가 없어서 저장할 수 없습니다.");
                return false;
            }
            
            String skillId = String.valueOf(com2usId);
            skillData.put("skill_id", skillId);
            
            // 기존 데이터 업데이트 또는 신규 삽입
            int result = swarfarmSkillMapper.upsertSkill(skillData);
            
            return result > 0;
        } catch (Exception e) {
            log.error("스킬 저장 중 오류 발생", e);
            return false;
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
            String response = restTemplate.getForObject(apiUrl, String.class);
            return objectMapper.readValue(response, SwarfarmSkillResponse.class);
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            return null;
        }
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
    private void saveSkillUpgrades(Integer swarfarmId, List<SwarfarmSkillResponse.UpgradeData> upgrades) {
        try {
            String skillId = swarfarmSkillMapper.findSkillIdBySwarfarmId(swarfarmId);
            if (skillId == null) {
                log.warn("Swarfarm ID {}에 해당하는 스킬을 찾을 수 없습니다.", swarfarmId);
                return;
            }
            
            // 기존 업그레이드 삭제
            swarfarmSkillMapper.deleteSkillUpgrades(skillId);
            
            // 새 업그레이드 저장
            for (int i = 0; i < upgrades.size(); i++) {
                SwarfarmSkillResponse.UpgradeData upgrade = upgrades.get(i);
                Map<String, Object> upgradeData = new HashMap<>();
                upgradeData.put("skill_id", skillId);
                upgradeData.put("upgrade_level", i + 1);
                upgradeData.put("effect", upgrade.getEffect());
                upgradeData.put("amount", upgrade.getAmount());
                swarfarmSkillMapper.insertSkillUpgrade(upgradeData);
            }
        } catch (Exception e) {
            log.error("스킬 업그레이드 저장 중 오류 발생", e);
        }
    }
    
    /**
     * 스킬 효과 저장
     */
    private void saveSkillEffects(Integer swarfarmId, List<SwarfarmSkillResponse.EffectData> effects) {
        try {
            String skillId = swarfarmSkillMapper.findSkillIdBySwarfarmId(swarfarmId);
            if (skillId == null) {
                log.warn("Swarfarm ID {}에 해당하는 스킬을 찾을 수 없습니다.", swarfarmId);
                return;
            }
            
            // 기존 효과 삭제
            swarfarmSkillMapper.deleteSkillEffects(skillId);
            
            // 새 효과 저장
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
                swarfarmSkillMapper.insertSkillEffect(effectData);
            }
        } catch (Exception e) {
            log.error("스킬 효과 저장 중 오류 발생", e);
        }
    }
    
    /**
     * 스킬 사용 몬스터 저장
     */
    private void saveSkillUsedOn(Integer swarfarmId, List<Integer> usedOn) {
        try {
            String skillId = swarfarmSkillMapper.findSkillIdBySwarfarmId(swarfarmId);
            if (skillId == null) {
                log.warn("Swarfarm ID {}에 해당하는 스킬을 찾을 수 없습니다.", swarfarmId);
                return;
            }
            
            // 기존 매핑 삭제
            swarfarmSkillMapper.deleteSkillUsedOn(skillId);
            
            // 새 매핑 저장
            for (Integer monsterSwarfarmId : usedOn) {
                Map<String, Object> usedOnData = new HashMap<>();
                usedOnData.put("skill_id", skillId);
                usedOnData.put("monster_swarfarm_id", monsterSwarfarmId);
                swarfarmSkillMapper.insertSkillUsedOn(usedOnData);
            }
        } catch (Exception e) {
            log.error("스킬 사용 몬스터 저장 중 오류 발생", e);
        }
    }
}

