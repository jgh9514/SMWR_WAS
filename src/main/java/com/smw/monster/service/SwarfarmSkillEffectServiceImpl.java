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
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.SwarfarmSkillEffectResponse;
import com.smw.monster.mapper.SwarfarmSkillEffectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmSkillEffectServiceImpl implements SwarfarmSkillEffectService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/skill-effects/";
    private static final String SWARFARM_IMAGE_BASE_URL = "https://swarfarm.com/static/herders/images/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    
    @Autowired
    private SwarfarmSkillEffectMapper swarfarmSkillEffectMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String getImageBasePath() {
        try {
            // 클래스패스에서 resources 경로 찾기
            String resourcePath = getClass().getClassLoader().getResource("static/images/skill-effects").getPath();
            // Windows 경로 처리
            if (resourcePath.startsWith("/") && System.getProperty("os.name").toLowerCase().contains("win")) {
                resourcePath = resourcePath.substring(1);
            }
            return resourcePath;
        } catch (Exception e) {
            // 대체 경로
            String projectRoot = System.getProperty("user.dir");
            return projectRoot + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "static" + File.separator + "images" + File.separator + "skill-effects";
        }
    }
    
    @Override
    public int syncAllSkillEffects() {
        log.info("===== Swarfarm 스킬 이펙트 동기화 시작 =====");
        int totalSynced = 0;
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmSkillEffectResponse firstResponse = fetchSkillEffectData(firstPageUrl);
            
            if (firstResponse == null) {
                log.error("첫 페이지 데이터를 가져올 수 없습니다.");
                return 0;
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            log.info("전체 스킬 이펙트 수: {}, 예상 페이지 수: {}", totalCount, totalPages);
            
            // 모든 페이지 처리
            for (int page = 1; page <= totalPages; page++) {
                log.info("페이지 {} 동기화 시작", page);
                int synced = syncSkillEffectsByPage(page);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                if (page < totalPages) {
                    Thread.sleep(500);
                }
            }
            
            log.info("===== Swarfarm 스킬 이펙트 동기화 완료. 총 {}개 동기화 =====", totalSynced);
        } catch (Exception e) {
            log.error("스킬 이펙트 동기화 중 오류 발생", e);
        }
        
        return totalSynced;
    }
    
    @Override
    public int syncSkillEffectsByPage(int page) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmSkillEffectResponse response = fetchSkillEffectData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            for (SwarfarmSkillEffectResponse.SkillEffectData effect : response.getResults()) {
                try {
                    // 이미 존재하는 이펙트인지 확인
                    if (existsSkillEffect(effect.getId())) {
                        log.debug("스킬 이펙트 ID {}는 이미 존재합니다. 건너뜁니다.", effect.getId());
                        continue;
                    }
                    
                    // 이펙트 데이터 변환
                    Map<String, Object> effectData = convertToMap(effect);
                    
                    // 이미지 다운로드 (icon_filename이 있는 경우만)
                    if (effect.getIconFilename() != null && !effect.getIconFilename().isEmpty()) {
                        try {
                            String imagePath = downloadSkillEffectImage(effect.getIconFilename());
                            effectData.put("icon_path", imagePath);
                        } catch (Exception e) {
                            log.warn("이미지 다운로드 실패: {}", effect.getIconFilename(), e);
                        }
                    }
                    
                    // DB 저장
                    if (saveSkillEffect(effectData)) {
                        syncedCount++;
                    }
                } catch (Exception e) {
                    log.error("스킬 이펙트 저장 중 오류 발생: {}", effect.getId(), e);
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
    public String downloadSkillEffectImage(String iconFilename) {
        try {
            // icon_filename이 이미 전체 경로를 포함할 수 있으므로 확인
            String imageUrl;
            if (iconFilename.startsWith("http")) {
                imageUrl = iconFilename;
            } else {
                // buff 또는 debuff 폴더 확인
                String folder = iconFilename.startsWith("buff_") ? "buffs" : 
                               iconFilename.startsWith("debuff_") ? "debuffs" : "skill-effects";
                imageUrl = SWARFARM_IMAGE_BASE_URL + folder + "/" + iconFilename;
            }
            
            String basePath = getImageBasePath();
            
            // skill-effects 폴더 경로 생성
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
                return "/images/skill-effects/" + iconFilename;
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
                return "/images/skill-effects/" + iconFilename;
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
    public boolean saveSkillEffect(Map<String, Object> effectData) {
        try {
            // effect_id는 Swarfarm API ID를 그대로 사용
            Integer effectId = (Integer) effectData.get("effect_id");
            if (effectId == null) {
                log.warn("effect_id가 없어서 저장할 수 없습니다.");
                return false;
            }
            
            // 기존 데이터 업데이트 또는 신규 삽입
            int result = swarfarmSkillEffectMapper.upsertSkillEffect(effectData);
            
            return result > 0;
        } catch (Exception e) {
            log.error("스킬 이펙트 저장 중 오류 발생", e);
            return false;
        }
    }
    
    @Override
    public boolean existsSkillEffect(Integer effectId) {
        try {
            Integer count = swarfarmSkillEffectMapper.countByEffectId(effectId);
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("스킬 이펙트 존재 확인 중 오류 발생", e);
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
    private SwarfarmSkillEffectResponse fetchSkillEffectData(String apiUrl) {
        try {
            log.debug("API 호출: {}", apiUrl);
            String response = restTemplate.getForObject(apiUrl, String.class);
            return objectMapper.readValue(response, SwarfarmSkillEffectResponse.class);
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            return null;
        }
    }
    
    /**
     * SkillEffectData를 Map으로 변환
     */
    private Map<String, Object> convertToMap(SwarfarmSkillEffectResponse.SkillEffectData effect) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("effect_id", effect.getId());
        map.put("name", effect.getName());
        map.put("is_buff", effect.getIsBuff());
        map.put("type", effect.getType());
        map.put("description", effect.getDescription());
        map.put("icon_filename", effect.getIconFilename());
        map.put("swarfarm_url", effect.getUrl());
        map.put("crt_user_id", "SYSTEM");
        map.put("upt_user_id", "SYSTEM");
        
        return map;
    }
}

