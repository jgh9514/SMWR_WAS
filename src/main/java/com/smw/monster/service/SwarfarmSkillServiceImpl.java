package com.smw.monster.service;

import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.SwarfarmSkillResponse;
import com.smw.monster.mapper.SwarfarmMonsterMapper;
import com.smw.monster.mapper.SwarfarmSkillMapper;
import com.sysconf.util.S3Service;

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
    private SwarfarmMonsterMapper swarfarmMonsterMapper;
    
    @Autowired
    private SwarfarmApiClient swarfarmApiClient;

    @Autowired
    private SwarfarmSyncMetrics swarfarmSyncMetrics;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${smw.swarfarm.request-delay-ms:250}")
    private long requestDelayMs;

    @Value("${smw.swarfarm.db-batch-size:250}")
    private int dbBatchSize;

    @Value("${smw.swarfarm.commit-chunk-size:100}")
    private int commitChunkSize;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
            
            stats.addProcessed(response.getResults().size());
            List<SwarfarmSkillResponse.SkillData> toProcess = new ArrayList<>();
            for (SwarfarmSkillResponse.SkillData skill : response.getResults()) {
                if (existingSwarfarmIds.contains(skill.getId())) {
                    log.debug("스킬 ID {}는 이미 존재합니다. 건너뜁니다.", skill.getId());
                    stats.addSkipped(1);
                    continue;
                }
                toProcess.add(skill);
            }
            if (toProcess.isEmpty()) {
                addBatchLog("페이지 %d 동기화 완료: 저장=0, 누적 처리=%d, 누적 스킵=%d, 누적 실패=%d",
                        page, stats.getProcessed(), stats.getSkipped(), stats.getFailed());
                return 0;
            }

            int chunkSize = Math.max(1, commitChunkSize);
            int totalSyncedPage = 0;
            for (int start = 0; start < toProcess.size(); start += chunkSize) {
                int end = Math.min(start + chunkSize, toProcess.size());
                List<SwarfarmSkillResponse.SkillData> chunk = new ArrayList<>(toProcess.subList(start, end));
                try {
                    totalSyncedPage += persistSkillChunkWithCommit(chunk, existingSwarfarmIds, stats);
                } catch (Exception e) {
                    stats.addFailed(1);
                    log.error("스킬 청크 저장 중 오류 (페이지 {}, 오프셋 {})", page, start, e);
                    throw new RuntimeException("스킬 저장 실패: 페이지 " + page + ", 청크 시작 " + start, e);
                }
            }

            addBatchLog("페이지 %d 동기화 완료: 저장=%d, 누적 처리=%d, 누적 스킵=%d, 누적 실패=%d",
                    page, totalSyncedPage, stats.getProcessed(), stats.getSkipped(), stats.getFailed());
            return totalSyncedPage;
            
        } catch (Exception e) {
            stats.addFailed(1);
            log.error("페이지 {} 동기화 중 오류 발생", page, e);
            throw new RuntimeException("페이지 " + page + " 동기화 실패", e);
        }
    }

    private TransactionTemplate newTxTemplate() {
        TransactionTemplate t = new TransactionTemplate(transactionManager);
        t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return t;
    }

    /**
     * 스킬을 {@code commitChunkSize}건 단위로 DB에 반영한다. S3 이미지 업로드는 트랜잭션 밖에서 수행하고,
     * skill_master·자식 테이블만 새 트랜잭션에서 커밋하여 한 번에 묶는 범위를 줄인다.
     */
    private int persistSkillChunkWithCommit(List<SwarfarmSkillResponse.SkillData> chunk,
            Set<Integer> existingSwarfarmIds, SwarfarmSyncMetrics.SyncStats stats) {
        List<Map<String, Object>> skillRows = new ArrayList<>(chunk.size());
        List<Integer> newSkillIds = new ArrayList<>(chunk.size());
        for (SwarfarmSkillResponse.SkillData skill : chunk) {
            Integer com2usId = skill.getCom2usId();
            if (com2usId == null) {
                throw new IllegalStateException("com2us_id가 없습니다. swarfarm_id=" + skill.getId());
            }
            Map<String, Object> skillData = convertToMap(skill);
            if (skill.getIconFilename() != null && !skill.getIconFilename().isEmpty()) {
                try {
                    String imagePath = downloadSkillImage(skill.getIconFilename());
                    skillData.put("icon_path", imagePath);
                    skillData.put("remark", null);
                } catch (Exception e) {
                    log.warn("스킬 아이콘 다운로드 실패 (저장은 계속): swarfarm_id={}, file={}",
                            skill.getId(), skill.getIconFilename(), e);
                    skillData.put("icon_path", null);
                    skillData.put("remark", buildIconDownloadFailureRemark(skill.getIconFilename(), e));
                }
            }
            skillData.put("skill_id", com2usId);
            skillRows.add(skillData);
            newSkillIds.add(com2usId);
        }

        Integer saved = newTxTemplate().execute(status -> {
            flushSkillMasterBatches(skillRows);
            if (!newSkillIds.isEmpty()) {
                swarfarmSkillMapper.deleteSkillUpgradesBySkillIds(newSkillIds);
                swarfarmSkillMapper.deleteSkillEffectsBySkillIds(newSkillIds);
                swarfarmMonsterMapper.deleteMonsterSkillLinksBySkillIds(newSkillIds);
            }
            Set<Integer> allUsedOnSwarfarmIds = new HashSet<>();
            for (SwarfarmSkillResponse.SkillData skill : chunk) {
                if (skill.getUsedOn() != null) {
                    allUsedOnSwarfarmIds.addAll(skill.getUsedOn());
                }
            }
            Map<Integer, String> monsterIdBySwarfarmId = loadMonsterIdCache(allUsedOnSwarfarmIds);

            List<Map<String, Object>> upgradeBuf = new ArrayList<>();
            List<Map<String, Object>> effectBuf = new ArrayList<>();
            List<Map<String, Object>> linkBuf = new ArrayList<>();
            for (SwarfarmSkillResponse.SkillData skill : chunk) {
                Integer skillId = skill.getCom2usId();
                collectUpgradeRows(skillId, skill.getUpgrades(), upgradeBuf);
                collectEffectRows(skillId, skill.getEffects(), effectBuf);
                collectUsedOnRows(skillId, skill.getUsedOn(), linkBuf, monsterIdBySwarfarmId);
            }

            flushInsertChunks(upgradeBuf, c -> swarfarmSkillMapper.insertSkillUpgradesBatch(c));
            flushInsertChunks(effectBuf, c -> swarfarmSkillMapper.insertSkillEffectsBatch(c));
            flushInsertChunks(linkBuf, c -> swarfarmMonsterMapper.insertMonsterSkillsBatch(c));

            int syncedCount = chunk.size();
            stats.addSaved(syncedCount);
            for (SwarfarmSkillResponse.SkillData skill : chunk) {
                existingSwarfarmIds.add(skill.getId());
            }
            return syncedCount;
        });
        return saved != null ? saved : 0;
    }

    private void flushSkillMasterBatches(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i += dbBatchSize) {
            int end = Math.min(i + dbBatchSize, rows.size());
            List<Map<String, Object>> chunk = new ArrayList<>(rows.subList(i, end));
            int n = swarfarmSkillMapper.upsertSkillsBatch(chunk);
            if (n <= 0) {
                throw new IllegalStateException("스킬 마스터 일괄 upsert 결과가 0입니다.");
            }
        }
    }

    private void flushInsertChunks(List<Map<String, Object>> buffer, Consumer<List<Map<String, Object>>> batchInsert) {
        if (buffer.isEmpty()) {
            return;
        }
        for (int i = 0; i < buffer.size(); i += dbBatchSize) {
            int end = Math.min(i + dbBatchSize, buffer.size());
            batchInsert.accept(new ArrayList<>(buffer.subList(i, end)));
        }
    }

    private Map<Integer, String> loadMonsterIdCache(Set<Integer> swarfarmIds) {
        if (swarfarmIds == null || swarfarmIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> rows = swarfarmMonsterMapper.selectMonsterIdsBySwarfarmIds(new ArrayList<>(swarfarmIds));
        Map<Integer, String> out = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object sid = row.get("swarfarm_id");
            Object mid = row.get("monster_id");
            if (sid != null && mid != null) {
                out.put(((Number) sid).intValue(), mid.toString());
            }
        }
        return out;
    }

    private void collectUpgradeRows(Integer skillId, List<SwarfarmSkillResponse.UpgradeData> upgrades,
            List<Map<String, Object>> buf) {
        if (skillId == null || upgrades == null || upgrades.isEmpty()) {
            return;
        }
        for (int i = 0; i < upgrades.size(); i++) {
            SwarfarmSkillResponse.UpgradeData upgrade = upgrades.get(i);
            Map<String, Object> upgradeData = new HashMap<>();
            upgradeData.put("skill_id", skillId);
            upgradeData.put("upgrade_level", i + 1);
            upgradeData.put("effect", upgrade.getEffect());
            upgradeData.put("amount", upgrade.getAmount());
            buf.add(upgradeData);
        }
    }

    private void collectEffectRows(Integer skillId, List<SwarfarmSkillResponse.EffectData> effects,
            List<Map<String, Object>> buf) {
        if (skillId == null || effects == null || effects.isEmpty()) {
            return;
        }
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
            buf.add(effectData);
        }
    }

    private void collectUsedOnRows(Integer skillId, List<Integer> usedOn, List<Map<String, Object>> buf,
            Map<Integer, String> monsterIdBySwarfarmId) {
        if (skillId == null || usedOn == null || usedOn.isEmpty()) {
            return;
        }
        int skillOrder = 1;
        for (Integer monsterSwarfarmId : usedOn) {
            String monsterId = monsterIdBySwarfarmId.get(monsterSwarfarmId);
            if (monsterId == null) {
                log.debug("used_on 몬스터 미등록(swarfarm_id={}), monster 동기화 후 재실행 시 반영 가능", monsterSwarfarmId);
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("monster_id", monsterId);
            row.put("skill_id", skillId);
            row.put("skill_order", skillOrder++);
            buf.add(row);
        }
    }

    private static final int REMARK_MAX_LEN = 1000;

    /** DB remark 컬럼(varchar 1000) 용 */
    private static String truncateRemark(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= REMARK_MAX_LEN) {
            return s;
        }
        return s.substring(0, REMARK_MAX_LEN - 1) + "…";
    }

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

    private Set<Integer> loadExistingSwarfarmIds() {
        return new HashSet<>(swarfarmSkillMapper.selectAllSwarfarmIds());
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
            String cloudFrontUrl = swarfarmApiClient.downloadImageToS3(imageUrl, iconFilename,
                    inferImageContentType(iconFilename), S3Service.SKILLS_FOLDER);
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

    private Integer saveSkillInternal(Map<String, Object> skillData) {
        Integer com2usId = (Integer) skillData.get("com2us_id");
        if (com2usId == null) {
            throw new IllegalStateException("com2us_id가 없어서 저장할 수 없습니다.");
        }
        skillData.put("skill_id", com2usId);
        int result = swarfarmSkillMapper.upsertSkill(skillData);
        if (result <= 0) {
            throw new IllegalStateException("스킬 upsert 결과가 0입니다. com2us_id=" + com2usId);
        }
        return com2usId;
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
            SwarfarmSkillResponse res = swarfarmApiClient.fetchJson(apiUrl, SwarfarmSkillResponse.class);
            if (res == null) {
                throw new IllegalStateException("API 응답이 null입니다: " + apiUrl);
            }
            return res;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("API 호출 중 오류 발생: {}", apiUrl, e);
            throw new RuntimeException("Swarfarm 스킬 API 호출 실패: " + apiUrl, e);
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
     * {@code skill_master.swarfarm_url} 값 결정.
     * <ol>
     *   <li>JSON 최상위 {@code url}이 있으면 그대로 사용 (상세/하이퍼링크 응답)</li>
     *   <li>없으면 Swarfarm 스킬 REST URL: {@code .../api/v2/skills/{id}/}</li>
     * </ol>
     * 이펙트 객체의 {@code effects[].effect.url}은 사용하지 않는다.
     */
    private String resolveSwarfarmSkillPageUrl(SwarfarmSkillResponse.SkillData skill) {
        String fromJson = skill.getUrl();
        if (fromJson != null && !fromJson.isBlank()) {
            return fromJson.trim();
        }
        Integer sid = skill.getId();
        if (sid == null) {
            return null;
        }
        return SWARFARM_API_BASE_URL + sid + "/";
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
        map.put("swarfarm_url", resolveSwarfarmSkillPageUrl(skill));
        
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
    
}

