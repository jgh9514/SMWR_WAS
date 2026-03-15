package com.smw.monster.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.common.util.S3Service;
import com.smw.monster.dto.SwarfarmLeaderSkillResponse;
import com.smw.monster.mapper.SwarfarmLeaderSkillMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmLeaderSkillServiceImpl implements SwarfarmLeaderSkillService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/leader-skills/";
    private static final String SWARFARM_LEADER_SKILL_IMAGE_BASE = "https://swarfarm.com/static/herders/images/skills/leader/";
    /** Swarfarm 500/404 시 GitHub raw URL 폴백 (swarfarm/swarfarm 리포지토리) */
    private static final String GITHUB_LEADER_SKILL_IMAGE_BASE = "https://raw.githubusercontent.com/swarfarm/swarfarm/master/herders/static/herders/images/skills/leader/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    private static final String SOURCE_SWARFARM_HTTP = "swarfarm-http";
    private static final String SOURCE_SWARFARM_REST = "swarfarm-rest";
    private static final String SOURCE_GITHUB_FALLBACK = "github-fallback";
    
    @Autowired
    private SwarfarmLeaderSkillMapper swarfarmLeaderSkillMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private S3Service s3Service;
    
    private Consumer<String> logCallback = null;
    
    @Override
    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }
    
    private void addBatchLog(String message, Object... args) {
        String logMessage = args.length > 0 ? String.format(message, args) : message;
        if (logCallback != null) {
            logCallback.accept(logMessage);
        } else {
            log.info(logMessage);
        }
    }
    
    @Override
    public int syncAllLeaderSkills() {
        addBatchLog("===== Swarfarm 리더 스킬 동기화 시작 =====");
        long startTime = System.currentTimeMillis();
        int totalSynced = 0;
        SyncStats stats = new SyncStats();
        
        try {
            addBatchLog("API 조회 시작: 첫 페이지 데이터 가져오기");
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmLeaderSkillResponse firstResponse = fetchLeaderSkillData(firstPageUrl);
            
            if (firstResponse == null) {
                addBatchLog("오류: 첫 페이지 데이터를 가져올 수 없습니다.");
                throw new RuntimeException("첫 페이지 데이터를 가져올 수 없습니다.");
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            addBatchLog("전체 리더 스킬 수: %d개, 예상 페이지 수: %d페이지", totalCount, totalPages);
            
            for (int page = 1; page <= totalPages; page++) {
                addBatchLog("페이지 %d 처리 시작...", page);
                int synced = syncLeaderSkillsByPage(page, stats);
                totalSynced += synced;
                addBatchLog("페이지 %d 처리 완료: %d개 저장", page, synced);
                
                if (page < totalPages) {
                    Thread.sleep(500);
                }
            }
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            addBatchLog("===== Swarfarm 리더 스킬 동기화 완료 =====");
            addBatchLog("총 동기화된 리더 스킬 수: %d개", totalSynced);
            addBatchLog("아이콘 업로드 성공: %d개", stats.iconUploadedCount);
            addBatchLog("아이콘 업로드 실패: %d개", stats.iconFailedCount);
            addBatchLog("아이콘 소스별 성공 - Swarfarm(HttpURLConnection): %d개", stats.swarfarmHttpSuccessCount);
            addBatchLog("아이콘 소스별 성공 - Swarfarm(RestTemplate): %d개", stats.swarfarmRestSuccessCount);
            addBatchLog("아이콘 소스별 성공 - GitHub 폴백: %d개", stats.githubFallbackSuccessCount);
            addBatchLog("DB 저장 실패: %d개", stats.dbSaveFailedCount);
            addBatchLog("monster_leaders 기본 이미지 대체: %d개", stats.defaultIconFallbackCount);
            addBatchLog("처리 중 예외 발생: %d개", stats.processingErrorCount);
            addBatchLog("소요 시간: %.2f초", elapsedTime / 1000.0);
        } catch (Exception e) {
            addBatchLog("오류 발생: %s", e.getMessage());
            log.error("리더 스킬 동기화 중 오류 발생", e);
            throw new RuntimeException("리더 스킬 동기화 실패", e);
        }
        
        return totalSynced;
    }
    
    @Override
    public int syncLeaderSkillsByPage(int page) {
        return syncLeaderSkillsByPage(page, new SyncStats());
    }
    
    private int syncLeaderSkillsByPage(int page, SyncStats stats) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmLeaderSkillResponse response = fetchLeaderSkillData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                addBatchLog("페이지 %d 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            for (SwarfarmLeaderSkillResponse.LeaderSkillData leaderSkill : response.getResults()) {
                try {
                    Map<String, Object> leaderSkillData = convertToMap(leaderSkill);
                    IconUploadResult iconResult = downloadAndUploadLeaderSkillIcon(leaderSkill);
                    
                    if (iconResult.isSuccess()) {
                        leaderSkillData.put("icon_path", iconResult.iconPath);
                        stats.recordIconSuccess(iconResult.source);
                        addBatchLog("아이콘 업로드 성공: leader_skill_id=%d, 파일=%s, source=%s",
                                leaderSkill.getId(), iconResult.iconFilename, iconResult.source);
                    } else {
                        stats.iconFailedCount++;
                        stats.defaultIconFallbackCount++;
                        addBatchLog("아이콘 업로드 실패: leader_skill_id=%d, attribute=%s, area=%s, element=%s, 시도=%s",
                                leaderSkill.getId(),
                                leaderSkill.getAttribute(),
                                leaderSkill.getArea(),
                                leaderSkill.getElement(),
                                iconResult.candidates);
                    }
                    
                    if (saveLeaderSkill(leaderSkillData)) {
                        syncedCount++;
                        upsertMonsterLeader(leaderSkillData);
                    } else {
                        stats.dbSaveFailedCount++;
                        addBatchLog("DB 저장 실패: leader_skill_id=%d", leaderSkill.getId());
                    }
                    
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } catch (Exception e) {
                    stats.processingErrorCount++;
                    addBatchLog("리더 스킬 처리 중 오류: leader_skill_id=%d, 오류=%s",
                            leaderSkill.getId(), e.getMessage());
                    log.error("리더 스킬 저장 중 오류 발생: {}", leaderSkill.getId(), e);
                }
            }
            
            return syncedCount;
        } catch (Exception e) {
            addBatchLog("페이지 %d 동기화 중 오류 발생: %s", page, e.getMessage());
            log.error("페이지 {} 동기화 중 오류 발생", page, e);
            return 0;
        }
    }
    
    /**
     * Swarfarm에서 리더 스킬 아이콘 다운로드 후 S3 업로드
     * 실제 파일명 패턴 (Swarfarm):
     *   - Arena/Guild/Dungeon: leader_skill_{Attribute}_{Area}.png
     *   - Element: leader_skill_{Attribute}_{Element}.png
     *   - General: leader_skill_{Attribute}.png
     */
    private IconUploadResult downloadAndUploadLeaderSkillIcon(SwarfarmLeaderSkillResponse.LeaderSkillData leaderSkill) {
        if (leaderSkill.getAttribute() == null || leaderSkill.getAttribute().isEmpty()) {
            return IconUploadResult.failure(new ArrayList<String>());
        }
        
        String attrPart = leaderSkill.getAttribute().replace(" ", "_");
        String area = leaderSkill.getArea();
        String element = leaderSkill.getElement();
        List<String> candidates = buildIconCandidates(attrPart, area, element);
        
        for (String iconFilename : candidates) {
            IconUploadResult result = tryDownloadAndUpload(iconFilename);
            if (result != null) return result;
            
            result = tryDownloadWithRestTemplate(iconFilename);
            if (result != null) return result;
            
            result = tryDownloadFromGitHub(iconFilename);
            if (result != null) return result;
        }
        
        return IconUploadResult.failure(candidates);
    }
    
    private List<String> buildIconCandidates(String attrPart, String area, String element) {
        Set<String> candidates = new LinkedHashSet<String>();
        
        if (element != null && !element.isEmpty()) {
            candidates.add("leader_skill_" + attrPart + "_" + element + ".png");
        }
        
        if (area != null && !area.isEmpty() && !"General".equals(area) && !"Element".equals(area)) {
            candidates.add("leader_skill_" + attrPart + "_" + area + ".png");
        }
        
        candidates.add("leader_skill_" + attrPart + ".png");
        return new ArrayList<String>(candidates);
    }
    
    private IconUploadResult tryDownloadWithRestTemplate(String iconFilename) {
        try {
            String imageUrl = SWARFARM_LEADER_SKILL_IMAGE_BASE + iconFilename;
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://swarfarm.com/");
            headers.setAccept(Arrays.asList(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG));
            HttpEntity<Void> entity = new HttpEntity<Void>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(imageUrl, HttpMethod.GET, entity, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().length > 0) {
                String s3FileName = "leader/" + iconFilename;
                String cloudFrontUrl = s3Service.uploadImage(new ByteArrayInputStream(response.getBody()), s3FileName, "image/png");
                return IconUploadResult.success(cloudFrontUrl, iconFilename, SOURCE_SWARFARM_REST);
            }
        } catch (Exception e) {
            log.debug("RestTemplate 다운로드 실패: {} - {}", iconFilename, e.getMessage());
        }
        
        return null;
    }
    
    /** Swarfarm 500/404 시 GitHub raw URL에서 다운로드 (폴백) */
    private IconUploadResult tryDownloadFromGitHub(String iconFilename) {
        try {
            String imageUrl = GITHUB_LEADER_SKILL_IMAGE_BASE + iconFilename;
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            HttpEntity<Void> entity = new HttpEntity<Void>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(imageUrl, HttpMethod.GET, entity, byte[].class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null && response.getBody().length > 0) {
                String s3FileName = "leader/" + iconFilename;
                String cloudFrontUrl = s3Service.uploadImage(new ByteArrayInputStream(response.getBody()), s3FileName, "image/png");
                return IconUploadResult.success(cloudFrontUrl, iconFilename, SOURCE_GITHUB_FALLBACK);
            }
        } catch (Exception e) {
            log.debug("GitHub 다운로드 실패: {} - {}", iconFilename, e.getMessage());
        }
        
        return null;
    }
    
    private IconUploadResult tryDownloadAndUpload(String iconFilename) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            String imageUrl = SWARFARM_LEADER_SKILL_IMAGE_BASE + iconFilename;
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("Referer", "https://swarfarm.com/");
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                String s3FileName = "leader/" + iconFilename;
                String cloudFrontUrl = s3Service.uploadImage(inputStream, s3FileName, "image/png");
                return IconUploadResult.success(cloudFrontUrl, iconFilename, SOURCE_SWARFARM_HTTP);
            }
            
            log.debug("리더 스킬 아이콘 HTTP {}: {}", responseCode, imageUrl);
        } catch (Exception e) {
            log.debug("리더 스킬 아이콘 시도 실패: {} - {}", iconFilename, e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) {
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
        
        return null;
    }
    
    /** monster_leaders.icon_path NOT NULL 대비 기본값 (CloudFront) */
    private static final String DEFAULT_LEADER_ICON = "https://dyjduzi8vf2k4.cloudfront.net/images/default-monster.png";
    
    /**
     * monster_leaders 테이블 동기화 (표시용 icon_path)
     */
    private void upsertMonsterLeader(Map<String, Object> leaderSkillData) {
        try {
            Map<String, Object> param = new HashMap<String, Object>();
            param.put("leader_id", String.valueOf(leaderSkillData.get("leader_skill_id")));
            param.put("type", leaderSkillData.get("attribute"));
            param.put("stat", leaderSkillData.get("area") != null ? leaderSkillData.get("area") : "General");
            param.put("increase_by", leaderSkillData.get("amount"));
            Object iconPath = leaderSkillData.get("icon_path");
            param.put("icon_path", iconPath != null && !iconPath.toString().isEmpty() ? iconPath : DEFAULT_LEADER_ICON);
            swarfarmLeaderSkillMapper.upsertMonsterLeader(param);
        } catch (Exception e) {
            log.warn("monster_leaders 동기화 실패: leader_id={}", leaderSkillData.get("leader_skill_id"), e);
        }
    }
    
    @Override
    public boolean saveLeaderSkill(Map<String, Object> leaderSkillData) {
        try {
            Integer leaderSkillId = (Integer) leaderSkillData.get("leader_skill_id");
            if (leaderSkillId == null) {
                log.warn("leader_skill_id가 없어서 저장할 수 없습니다.");
                return false;
            }
            
            int result = swarfarmLeaderSkillMapper.upsertLeaderSkill(leaderSkillData);
            return result > 0;
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
            String response = restTemplate.getForObject(apiUrl, String.class);
            return objectMapper.readValue(response, SwarfarmLeaderSkillResponse.class);
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            return null;
        }
    }
    
    /**
     * LeaderSkillData를 Map으로 변환
     */
    private Map<String, Object> convertToMap(SwarfarmLeaderSkillResponse.LeaderSkillData leaderSkill) {
        Map<String, Object> map = new HashMap<String, Object>();
        
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
    
    private static class SyncStats {
        private int iconUploadedCount;
        private int iconFailedCount;
        private int swarfarmHttpSuccessCount;
        private int swarfarmRestSuccessCount;
        private int githubFallbackSuccessCount;
        private int dbSaveFailedCount;
        private int defaultIconFallbackCount;
        private int processingErrorCount;
        
        private void recordIconSuccess(String source) {
            iconUploadedCount++;
            if (SOURCE_SWARFARM_HTTP.equals(source)) {
                swarfarmHttpSuccessCount++;
            } else if (SOURCE_SWARFARM_REST.equals(source)) {
                swarfarmRestSuccessCount++;
            } else if (SOURCE_GITHUB_FALLBACK.equals(source)) {
                githubFallbackSuccessCount++;
            }
        }
    }
    
    private static class IconUploadResult {
        private final String iconPath;
        private final String iconFilename;
        private final String source;
        private final List<String> candidates;
        
        private IconUploadResult(String iconPath, String iconFilename, String source, List<String> candidates) {
            this.iconPath = iconPath;
            this.iconFilename = iconFilename;
            this.source = source;
            this.candidates = candidates;
        }
        
        private static IconUploadResult success(String iconPath, String iconFilename, String source) {
            return new IconUploadResult(iconPath, iconFilename, source, null);
        }
        
        private static IconUploadResult failure(List<String> candidates) {
            return new IconUploadResult(null, null, null, candidates);
        }
        
        private boolean isSuccess() {
            return iconPath != null && !iconPath.isEmpty();
        }
    }
}
