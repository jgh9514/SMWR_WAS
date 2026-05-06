package com.smw.monster.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.monster.dto.SwarfarmSkillEffectResponse;
import com.smw.monster.mapper.SwarfarmSkillEffectMapper;
import com.sysconf.util.S3Service;

import io.micrometer.core.instrument.Timer;
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
    public int syncAllSkillEffects() {
        addBatchLog("===== Swarfarm 스킬 이펙트 동기화 시작 =====");
        int totalSynced = 0;
        SwarfarmSyncMetrics.SyncStats stats = swarfarmSyncMetrics.newStats();
        Timer.Sample syncTimerSample = swarfarmSyncMetrics.startTimer();
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmSkillEffectResponse firstResponse = fetchSkillEffectData(firstPageUrl);
            
            if (firstResponse == null) {
                log.error("첫 페이지 데이터를 가져올 수 없습니다.");
                throw new RuntimeException("첫 페이지 데이터를 가져올 수 없습니다.");
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            addBatchLog("전체 스킬 이펙트 수: %d, 예상 페이지 수: %d", totalCount, totalPages);
            Set<Integer> existingEffectIds = loadExistingEffectIds();
            
            // 모든 페이지 처리
            for (int page = 1; page <= totalPages; page++) {
                addBatchLog("페이지 %d 동기화 시작", page);
                int synced = syncSkillEffectsByPage(page, existingEffectIds, stats);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                if (page < totalPages) {
                    pauseBetweenRequests();
                }
            }
            
            addBatchLog("===== Swarfarm 스킬 이펙트 동기화 완료. 총 %d개 동기화 =====", totalSynced);
            addBatchLog("처리=%d, 저장=%d, 스킵=%d, 실패=%d",
                    stats.getProcessed(), stats.getSaved(), stats.getSkipped(), stats.getFailed());
            swarfarmSyncMetrics.recordSummary("skill_effect", "SUCCESS", stats, syncTimerSample);
            return totalSynced;
        } catch (Exception e) {
            log.error("스킬 이펙트 동기화 중 오류 발생", e);
            stats.addFailed(1);
            swarfarmSyncMetrics.recordSummary("skill_effect", "FAIL", stats, syncTimerSample);
            throw new RuntimeException("스킬 이펙트 동기화 실패", e);
        }
    }
    
    @Override
    public int syncSkillEffectsByPage(int page) {
        return syncSkillEffectsByPage(page, new HashSet<>(), swarfarmSyncMetrics.newStats());
    }

    private int syncSkillEffectsByPage(int page, Set<Integer> existingEffectIds, SwarfarmSyncMetrics.SyncStats stats) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmSkillEffectResponse response = fetchSkillEffectData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            stats.addProcessed(response.getResults().size());
            for (SwarfarmSkillEffectResponse.SkillEffectData effect : response.getResults()) {
                try {
                    // 이미 존재하는 이펙트인지 확인
                    if (existingEffectIds.contains(effect.getId())) {
                        log.debug("스킬 이펙트 ID {}는 이미 존재합니다. 건너뜁니다.", effect.getId());
                        stats.addSkipped(1);
                        continue;
                    }
                    
                    // 이펙트 데이터 변환
                    Map<String, Object> effectData = convertToMap(effect);
                    
                    // 이미지 다운로드 (icon_filename이 있는 경우만) — 실패 시 remark 기록 후 저장 계속
                    if (effect.getIconFilename() != null && !effect.getIconFilename().isEmpty()) {
                        try {
                            String imagePath = downloadSkillEffectImage(effect.getIconFilename());
                            effectData.put("icon_path", imagePath);
                            effectData.put("remark", null);
                        } catch (Exception e) {
                            log.warn("스킬 이펙트 아이콘 다운로드 실패 (저장은 계속): effect_id={}, file={}",
                                    effect.getId(), effect.getIconFilename(), e);
                            effectData.put("icon_path", null);
                            effectData.put("remark", buildIconDownloadFailureRemark(effect.getIconFilename(), e));
                        }
                    } else {
                        effectData.put("icon_path", null);
                        effectData.put("remark", null);
                    }
                    
                    if (!saveSkillEffect(effectData)) {
                        throw new IllegalStateException("스킬 이펙트 저장 실패 (DB 반환 false): " + effect.getId());
                    }
                    syncedCount++;
                    stats.addSaved(1);
                    existingEffectIds.add(effect.getId());
                } catch (Exception e) {
                    stats.addFailed(1);
                    log.error("스킬 이펙트 저장 중 오류 발생: {}", effect.getId(), e);
                    throw new RuntimeException("스킬 이펙트 저장 실패: " + effect.getId(), e);
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

    private Set<Integer> loadExistingEffectIds() {
        return new HashSet<>(swarfarmSkillEffectMapper.selectAllEffectIds());
    }

    private void pauseBetweenRequests() {
        if (requestDelayMs > 0) {
            try {
                Thread.sleep(requestDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("스킬 이펙트 동기화 대기 중 인터럽트가 발생했습니다.", e);
            }
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
                // Swarfarm: buff_·debuff_ 접두 파일 모두 /static/herders/images/buffs/ (debuffs/ 경로 아님)
                String folder = (iconFilename.startsWith("buff_") || iconFilename.startsWith("debuff_"))
                        ? "buffs"
                        : "skill-effects";
                imageUrl = SWARFARM_IMAGE_BASE_URL + folder + "/" + iconFilename;
            }
            String cloudFrontUrl = swarfarmApiClient.downloadImageToS3(imageUrl, iconFilename,
                    inferImageContentType(iconFilename), S3Service.SKILL_EFFECTS_FOLDER);
            log.info("스킬 이펙트 이미지 S3 업로드 완료: {} -> {}", iconFilename, cloudFrontUrl);
            return cloudFrontUrl;
        } catch (Exception e) {
            log.error("이미지 다운로드 및 S3 업로드 중 오류 발생: {}", iconFilename, e);
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
            
            // 기존 데이터 업데이트 또는 신규 삽입 (ON CONFLICT DO UPDATE는 변경 없으면 0 반환 가능)
            swarfarmSkillEffectMapper.upsertSkillEffect(effectData);
            return true;
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
            SwarfarmSkillEffectResponse res = swarfarmApiClient.fetchJson(apiUrl, SwarfarmSkillEffectResponse.class);
            if (res == null) {
                throw new IllegalStateException("API 응답이 null입니다: " + apiUrl);
            }
            return res;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            throw new RuntimeException("Swarfarm 스킬 이펙트 API 호출 실패: " + apiUrl, e);
        }
    }

    private static final int REMARK_MAX_LEN = 1000;

    private static String truncateRemark(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= REMARK_MAX_LEN) {
            return s;
        }
        return s.substring(0, REMARK_MAX_LEN - 1) + "…";
    }

    /** DB remark 컬럼(varchar 1000) 용 — 스킬 마스터와 동일 규칙 */
    private static String buildIconDownloadFailureRemark(String iconFilename, Throwable e) {
        String base = "이미지 다운로드 실패: " + iconFilename;
        if (e != null) {
            String msg = e.getMessage();
            if (msg != null && !msg.isBlank()) {
                String oneLine = msg.replace('\r', ' ').replace('\n', ' ').trim();
                if (oneLine.length() > 240) {
                    oneLine = oneLine.substring(0, 240) + "…";
                }
                base = base + " (" + oneLine + ")";
            }
        }
        return truncateRemark(base);
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

