package com.smw.monster.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.SwarfarmMonsterResponse;
import com.smw.monster.mapper.SwarfarmMonsterMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmMonsterServiceImpl implements SwarfarmMonsterService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/monsters/";
    private static final String SWARFARM_IMAGE_BASE_URL = "https://swarfarm.com/static/herders/images/monsters/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    private static final int MAX_PARALLEL_PAGES = 5; // 동시 처리할 최대 페이지 수
    private static final int IMAGE_DOWNLOAD_THREADS = 10; // 이미지 다운로드 스레드 풀 크기
    
    @Autowired
    private SwarfarmMonsterMapper swarfarmMonsterMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${server.servlet.context-path:}")
    private String contextPath;
    
    // 이미지 다운로드용 스레드 풀
    private final ExecutorService imageDownloadExecutor = Executors.newFixedThreadPool(IMAGE_DOWNLOAD_THREADS);
    
    // 페이지 처리용 스레드 풀
    private final ExecutorService pageProcessExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_PAGES);
    
    // 로그 콜백 (배치 실행 시 상세 로그 수집용)
    private Consumer<String> logCallback = null;
    
    @Override
    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
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
    
    private String getImageBasePath() {
        // 프로젝트 루트의 static/images 경로
        // 실제 파일 시스템 경로 사용 (JAR 파일 내부가 아닌)
        String projectRoot = System.getProperty("user.dir");
        String imagePath = projectRoot + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator + "static" + File.separator + "images";
        
        // 경로가 존재하지 않으면 생성
        try {
            Path path = Paths.get(imagePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("이미지 디렉토리 생성: {}", imagePath);
            }
        } catch (Exception e) {
            log.warn("이미지 디렉토리 생성 실패: {}", imagePath, e);
        }
        
        return imagePath;
    }
    
    @Override
    public int syncAllMonsters() {
        addBatchLog("===== Swarfarm 몬스터 동기화 시작 =====");
        long startTime = System.currentTimeMillis();
        int totalSynced = 0;
        
        try {
            // 첫 페이지로 전체 개수 확인
            addBatchLog("API 조회 시작: 첫 페이지 데이터 가져오기");
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmMonsterResponse firstResponse = fetchMonsterData(firstPageUrl);
            
            if (firstResponse == null) {
                addBatchLog("오류: 첫 페이지 데이터를 가져올 수 없습니다.");
                return 0;
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            addBatchLog("전체 몬스터 수: %d개, 예상 페이지 수: %d페이지", totalCount, totalPages);
            
            // 이미 존재하는 몬스터 ID 목록을 한번에 조회 (성능 최적화)
            addBatchLog("기존 몬스터 데이터 조회 시작...");
            Set<Integer> existingSwarfarmIds = loadExistingSwarfarmIds();
            addBatchLog("기존 몬스터 수: %d개 (건너뛸 몬스터)", existingSwarfarmIds.size());
            
            // 첫 페이지 처리
            addBatchLog("페이지 1 처리 시작...");
            int synced = syncMonstersByPage(1, existingSwarfarmIds);
            totalSynced += synced;
            addBatchLog("페이지 1 처리 완료: %d개 저장", synced);
            
            // 나머지 페이지 병렬 처리
            addBatchLog("나머지 페이지 병렬 처리 시작 (최대 %d개 동시 처리)...", MAX_PARALLEL_PAGES);
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            for (int page = 2; page <= totalPages; page++) {
                final int pageNum = page;
                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    addBatchLog("페이지 %d 동기화 시작", pageNum);
                    int pageSynced = syncMonstersByPage(pageNum, existingSwarfarmIds);
                    addBatchLog("페이지 %d 동기화 완료: %d개 저장", pageNum, pageSynced);
                    return pageSynced;
                }, pageProcessExecutor);
                futures.add(future);
                
                // 동시 처리 수 제한
                if (futures.size() >= MAX_PARALLEL_PAGES) {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    for (CompletableFuture<Integer> f : futures) {
                        try {
                            totalSynced += f.get();
                        } catch (Exception e) {
                            addBatchLog("페이지 처리 중 오류: %s", e.getMessage());
                            log.error("페이지 처리 중 오류", e);
                        }
                    }
                    futures.clear();
                }
            }
            
            // 남은 작업 완료 대기
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<Integer> f : futures) {
                    try {
                        totalSynced += f.get();
                    } catch (Exception e) {
                        addBatchLog("페이지 처리 중 오류: %s", e.getMessage());
                        log.error("페이지 처리 중 오류", e);
                    }
                }
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            addBatchLog("===== Swarfarm 몬스터 동기화 완료 =====");
            addBatchLog("총 동기화된 몬스터 수: %d개", totalSynced);
            addBatchLog("소요 시간: %.2f초", elapsedTime / 1000.0);
        } catch (Exception e) {
            addBatchLog("오류 발생: %s", e.getMessage());
            log.error("몬스터 동기화 중 오류 발생", e);
            throw e; // 예외를 다시 던져서 트랜잭션 롤백 유도
        }
        
        return totalSynced;
    }
    
    /**
     * 이미 존재하는 Swarfarm ID 목록을 한번에 조회 (성능 최적화)
     */
    private Set<Integer> loadExistingSwarfarmIds() {
        try {
            // 모든 swarfarm_id를 한번에 조회
            List<Integer> existingIds = swarfarmMonsterMapper.selectAllSwarfarmIds();
            return new HashSet<>(existingIds);
        } catch (Exception e) {
            log.warn("기존 몬스터 ID 조회 실패, 빈 Set 반환", e);
            return new HashSet<>();
        }
    }
    
    @Override
    public int syncMonstersByPage(int page) {
        return syncMonstersByPage(page, new HashSet<>());
    }
    
    private int syncMonstersByPage(int page, Set<Integer> existingSwarfarmIds) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmMonsterResponse response = fetchMonsterData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            // 새로 추가할 몬스터만 필터링
            List<SwarfarmMonsterResponse.MonsterData> newMonsters = response.getResults().stream()
                    .filter(monster -> !existingSwarfarmIds.contains(monster.getId()))
                    .collect(Collectors.toList());
            
            if (newMonsters.isEmpty()) {
                log.debug("페이지 {}: 모든 몬스터가 이미 존재합니다.", page);
                return 0;
            }
            
            log.info("페이지 {}: {}개 중 {}개 새 몬스터 발견", page, response.getResults().size(), newMonsters.size());
            
            // 몬스터 데이터 변환 및 이미지 다운로드 (병렬 처리)
            List<CompletableFuture<Map<String, Object>>> monsterDataFutures = newMonsters.stream()
                    .map(monster -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Map<String, Object> monsterData = convertToMap(monster);
                            
                            // 이미지 다운로드 (비동기)
                            if (monster.getImageFilename() != null && monster.getElement() != null) {
                                String imageUrl = downloadMonsterImage(monster.getImageFilename(), monster.getElement());
                                // 이미지 다운로드 실패 시에도 기본 경로 설정 (NOT NULL 제약조건 대응)
                                if (imageUrl == null) {
                                    String elementFolder = monster.getElement().substring(0, 1).toUpperCase() 
                                            + monster.getElement().substring(1).toLowerCase();
                                    imageUrl = "/images/" + elementFolder + "/" + monster.getImageFilename();
                                    log.debug("이미지 다운로드 실패, 기본 경로 사용: {}", imageUrl);
                                }
                                monsterData.put("image_url", imageUrl);
                            } else {
                                // image_filename이나 element가 없는 경우 기본값 설정
                                String defaultImageUrl = "/images/default/monster_default.png";
                                monsterData.put("image_url", defaultImageUrl);
                                log.debug("이미지 정보 없음, 기본 이미지 경로 사용: {}", defaultImageUrl);
                            }
                            
                            // Skills와 Sources 정보도 함께 저장
                            monsterData.put("_skills", monster.getSkills());
                            monsterData.put("_sources", monster.getSource());
                            monsterData.put("_swarfarm_id", monster.getId());
                            
                            return monsterData;
                        } catch (Exception e) {
                            log.error("몬스터 데이터 변환 중 오류: {}", monster.getId(), e);
                            return null;
                        }
                    }, imageDownloadExecutor))
                    .collect(Collectors.toList());
            
            // 모든 데이터 변환 완료 대기
            List<Map<String, Object>> monsterDataList = monsterDataFutures.stream()
                    .map(future -> {
                        try {
                            return future.get(60, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.error("몬스터 데이터 변환 대기 중 오류", e);
                            return null;
                        }
                    })
                    .filter(data -> data != null)
                    .collect(Collectors.toList());
            
            // 배치로 DB 저장
            int syncedCount = saveMonstersBatch(monsterDataList);
            
            log.info("페이지 {} 동기화 완료. {}개 저장", page, syncedCount);
            return syncedCount;
            
        } catch (Exception e) {
            log.error("페이지 {} 동기화 중 오류 발생", page, e);
            return 0;
        }
    }
    
    /**
     * 여러 몬스터를 배치로 저장 (성능 최적화)
     */
    private int saveMonstersBatch(List<Map<String, Object>> monsterDataList) {
        int savedCount = 0;
        for (Map<String, Object> monsterData : monsterDataList) {
            try {
                if (saveMonster(monsterData)) {
                    savedCount++;
                    
                    // Skills 저장
                    @SuppressWarnings("unchecked")
                    List<Integer> skills = (List<Integer>) monsterData.get("_skills");
                    if (skills != null && !skills.isEmpty()) {
                        Integer swarfarmId = (Integer) monsterData.get("_swarfarm_id");
                        saveMonsterSkills(swarfarmId, skills);
                    }
                    
                    // Sources 저장
                    @SuppressWarnings("unchecked")
                    List<SwarfarmMonsterResponse.SourceData> sources = 
                            (List<SwarfarmMonsterResponse.SourceData>) monsterData.get("_sources");
                    if (sources != null && !sources.isEmpty()) {
                        Integer swarfarmId = (Integer) monsterData.get("_swarfarm_id");
                        saveMonsterSources(swarfarmId, sources);
                    }
                }
            } catch (Exception e) {
                log.error("몬스터 배치 저장 중 오류 발생", e);
            }
        }
        return savedCount;
    }
    
    @Override
    public String downloadMonsterImage(String imageFilename, String element) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        
        try {
            String imageUrl = SWARFARM_IMAGE_BASE_URL + imageFilename;
            String basePath = getImageBasePath();
            
            // 속성별 폴더 경로 생성
            String elementFolder = element.substring(0, 1).toUpperCase() + element.substring(1).toLowerCase();
            Path imageDir = Paths.get(basePath, elementFolder);
            
            // 폴더가 없으면 생성
            if (!Files.exists(imageDir)) {
                Files.createDirectories(imageDir);
                log.debug("이미지 디렉토리 생성: {}", imageDir);
            }
            
            // 이미지 파일 경로
            Path imagePath = imageDir.resolve(imageFilename);
            
            // 이미 존재하는 파일이면 건너뜀
            if (Files.exists(imagePath)) {
                log.debug("이미지가 이미 존재합니다: {}", imagePath);
                return "/images/" + elementFolder + "/" + imageFilename;
            }
            
            // 이미지 다운로드
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000); // 타임아웃 증가
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "image/*");
            
            int responseCode = connection.getResponseCode();
            log.debug("이미지 다운로드 시도: {} - 응답 코드: {}", imageUrl, responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(imagePath.toFile());
                
                byte[] buffer = new byte[8192]; // 버퍼 크기 증가
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                
                outputStream.flush();
                log.info("이미지 다운로드 완료: {} ({} bytes)", imagePath, totalBytes);
                return "/images/" + elementFolder + "/" + imageFilename;
            } else {
                log.warn("이미지 다운로드 실패. HTTP 응답 코드: {} - URL: {}", responseCode, imageUrl);
                return null;
            }
            
        } catch (java.net.SocketTimeoutException e) {
            log.warn("이미지 다운로드 타임아웃: {}", imageFilename, e);
            return null;
        } catch (java.io.FileNotFoundException e) {
            log.warn("이미지 파일을 찾을 수 없음: {} - URL: {}", imageFilename, SWARFARM_IMAGE_BASE_URL + imageFilename, e);
            return null;
        } catch (Exception e) {
            log.error("이미지 다운로드 중 오류 발생: {} - URL: {}", imageFilename, SWARFARM_IMAGE_BASE_URL + imageFilename, e);
            return null; // 예외를 던지지 않고 null 반환하여 배치가 계속 진행되도록 함
        } finally {
            // 리소스 정리
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                    log.warn("InputStream 닫기 실패", e);
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (Exception e) {
                    log.warn("FileOutputStream 닫기 실패", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    @Override
    public boolean saveMonster(Map<String, Object> monsterData) {
        try {
            // monster_id 생성 (com2us_id를 문자열로 변환)
            Integer com2usId = (Integer) monsterData.get("com2us_id");
            if (com2usId == null) {
                log.warn("com2us_id가 없어서 저장할 수 없습니다.");
                return false;
            }
            
            String monsterId = String.valueOf(com2usId);
            monsterData.put("monster_id", monsterId);
            
            // image_url이 null인 경우 기본값 설정 (NOT NULL 제약조건 대응)
            if (monsterData.get("image_url") == null) {
                String imageFilename = (String) monsterData.get("image_filename");
                String element = (String) monsterData.get("monster_elemental");
                
                if (imageFilename != null && element != null) {
                    String elementFolder = element.substring(0, 1).toUpperCase() 
                            + element.substring(1).toLowerCase();
                    String defaultImageUrl = "/images/" + elementFolder + "/" + imageFilename;
                    monsterData.put("image_url", defaultImageUrl);
                    log.debug("image_url이 null이어서 기본 경로 설정: {}", defaultImageUrl);
                } else {
                    // 최종 기본값
                    monsterData.put("image_url", "/images/default/monster_default.png");
                    log.debug("image_url이 null이고 이미지 정보도 없어서 최종 기본값 설정");
                }
            }
            
            // 기존 데이터 업데이트 또는 신규 삽입
            int result = swarfarmMonsterMapper.upsertMonster(monsterData);
            
            return result > 0;
        } catch (Exception e) {
            log.error("몬스터 저장 중 오류 발생", e);
            return false;
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
     * 스레드 풀 종료 (애플리케이션 종료 시 호출)
     */
    public void shutdown() {
        imageDownloadExecutor.shutdown();
        pageProcessExecutor.shutdown();
        try {
            if (!imageDownloadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                imageDownloadExecutor.shutdownNow();
            }
            if (!pageProcessExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                pageProcessExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            imageDownloadExecutor.shutdownNow();
            pageProcessExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Swarfarm API에서 데이터 가져오기
     */
    private SwarfarmMonsterResponse fetchMonsterData(String apiUrl) {
        try {
            log.debug("API 호출: {}", apiUrl);
            String response = restTemplate.getForObject(apiUrl, String.class);
            return objectMapper.readValue(response, SwarfarmMonsterResponse.class);
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            return null;
        }
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
    private void saveMonsterSkills(Integer swarfarmId, List<Integer> skills) {
        try {
            // 먼저 monster_id를 찾아야 함
            String monsterId = swarfarmMonsterMapper.findMonsterIdBySwarfarmId(swarfarmId);
            if (monsterId == null) {
                log.warn("Swarfarm ID {}에 해당하는 몬스터를 찾을 수 없습니다.", swarfarmId);
                return;
            }
            
            // 기존 스킬 삭제
            swarfarmMonsterMapper.deleteMonsterSkillsByMonsterId(monsterId);
            
            // 새 스킬 저장
            for (int i = 0; i < skills.size(); i++) {
                Map<String, Object> skillData = new HashMap<>();
                skillData.put("monster_id", monsterId);
                skillData.put("skill_id", skills.get(i));
                skillData.put("skill_order", i + 1);
                swarfarmMonsterMapper.insertMonsterSkill(skillData);
            }
        } catch (Exception e) {
            log.error("스킬 저장 중 오류 발생", e);
        }
    }
    
    /**
     * 몬스터 획득 경로 저장
     */
    private void saveMonsterSources(Integer swarfarmId, List<SwarfarmMonsterResponse.SourceData> sources) {
        try {
            // 먼저 monster_id를 찾아야 함
            String monsterId = swarfarmMonsterMapper.findMonsterIdBySwarfarmId(swarfarmId);
            if (monsterId == null) {
                log.warn("Swarfarm ID {}에 해당하는 몬스터를 찾을 수 없습니다.", swarfarmId);
                return;
            }
            
            // 기존 획득 경로 삭제
            swarfarmMonsterMapper.deleteMonsterSourcesByMonsterId(monsterId);
            
            // 새 획득 경로 저장
            for (int i = 0; i < sources.size(); i++) {
                SwarfarmMonsterResponse.SourceData source = sources.get(i);
                Map<String, Object> sourceData = new HashMap<>();
                sourceData.put("monster_id", monsterId);
                sourceData.put("source_id", source.getId());
                sourceData.put("source_name", source.getName());
                sourceData.put("source_description", source.getDescription());
                sourceData.put("farmable_source", source.getFarmableSource());
                sourceData.put("source_order", i + 1);
                swarfarmMonsterMapper.insertMonsterSource(sourceData);
            }
        } catch (Exception e) {
            log.error("획득 경로 저장 중 오류 발생", e);
        }
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

