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
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.SwarfarmDungeonResponse;
import com.smw.monster.mapper.SwarfarmDungeonMapper;

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
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private String getImageBasePath() {
        try {
            // 클래스패스에서 resources 경로 찾기
            String resourcePath = getClass().getClassLoader().getResource("static/images/dungeons").getPath();
            // Windows 경로 처리
            if (resourcePath.startsWith("/") && System.getProperty("os.name").toLowerCase().contains("win")) {
                resourcePath = resourcePath.substring(1);
            }
            return resourcePath;
        } catch (Exception e) {
            // 대체 경로
            String projectRoot = System.getProperty("user.dir");
            return projectRoot + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "static" + File.separator + "images" + File.separator + "dungeons";
        }
    }
    
    @Override
    public int syncAllDungeons() {
        log.info("===== Swarfarm 던전 동기화 시작 =====");
        int totalSynced = 0;
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmDungeonResponse firstResponse = fetchDungeonData(firstPageUrl);
            
            if (firstResponse == null) {
                log.error("첫 페이지 데이터를 가져올 수 없습니다.");
                return 0;
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            log.info("전체 던전 수: {}, 예상 페이지 수: {}", totalCount, totalPages);
            
            // 모든 페이지 처리
            for (int page = 1; page <= totalPages; page++) {
                log.info("페이지 {} 동기화 시작", page);
                int synced = syncDungeonsByPage(page);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                if (page < totalPages) {
                    Thread.sleep(500);
                }
            }
            
            log.info("===== Swarfarm 던전 동기화 완료. 총 {}개 동기화 =====", totalSynced);
        } catch (Exception e) {
            log.error("던전 동기화 중 오류 발생", e);
        }
        
        return totalSynced;
    }
    
    @Override
    public int syncDungeonsByPage(int page) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmDungeonResponse response = fetchDungeonData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            for (SwarfarmDungeonResponse.DungeonData dungeon : response.getResults()) {
                try {
                    // 이미 존재하는 던전인지 확인
                    if (existsDungeon(dungeon.getId())) {
                        log.debug("던전 ID {}는 이미 존재합니다. 건너뜁니다.", dungeon.getId());
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
                            log.warn("이미지 다운로드 실패: {}", dungeon.getIcon(), e);
                        }
                    }
                    
                    // DB 저장
                    if (saveDungeon(dungeonData)) {
                        syncedCount++;
                        
                        // Levels 저장
                        if (dungeon.getLevels() != null && !dungeon.getLevels().isEmpty()) {
                            saveDungeonLevels(dungeon.getId(), dungeon.getLevels());
                        }
                    }
                } catch (Exception e) {
                    log.error("던전 저장 중 오류 발생: {}", dungeon.getId(), e);
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
    public String downloadDungeonImage(String iconFilename) {
        try {
            String imageUrl = SWARFARM_IMAGE_BASE_URL + iconFilename;
            String basePath = getImageBasePath();
            
            // dungeons 폴더 경로 생성
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
                return "/images/dungeons/" + iconFilename;
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
                return "/images/dungeons/" + iconFilename;
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
            String response = restTemplate.getForObject(apiUrl, String.class);
            return objectMapper.readValue(response, SwarfarmDungeonResponse.class);
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            return null;
        }
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
            
            // 새 레벨 저장
            for (Integer levelId : levels) {
                Map<String, Object> levelData = new HashMap<>();
                levelData.put("dungeon_id", dungeonId);
                levelData.put("level_id", levelId);
                swarfarmDungeonMapper.insertDungeonLevel(levelData);
            }
        } catch (Exception e) {
            log.error("던전 레벨 저장 중 오류 발생", e);
        }
    }
}

