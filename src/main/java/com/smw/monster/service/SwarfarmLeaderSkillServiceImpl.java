package com.smw.monster.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.SwarfarmLeaderSkillResponse;
import com.smw.monster.mapper.SwarfarmLeaderSkillMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Primary
public class SwarfarmLeaderSkillServiceImpl implements SwarfarmLeaderSkillService {
    
    private static final String SWARFARM_API_BASE_URL = "https://swarfarm.com/api/v2/leader-skills/";
    private static final int DEFAULT_PAGE_SIZE = 100; // Swarfarm API 기본 페이지 크기
    
    @Autowired
    private SwarfarmLeaderSkillMapper swarfarmLeaderSkillMapper;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public int syncAllLeaderSkills() {
        log.info("===== Swarfarm 리더 스킬 동기화 시작 =====");
        int totalSynced = 0;
        
        try {
            // 첫 페이지로 전체 개수 확인
            String firstPageUrl = SWARFARM_API_BASE_URL + "?format=json&page=1";
            SwarfarmLeaderSkillResponse firstResponse = fetchLeaderSkillData(firstPageUrl);
            
            if (firstResponse == null) {
                log.error("첫 페이지 데이터를 가져올 수 없습니다.");
                return 0;
            }
            
            int totalCount = firstResponse.getCount();
            int totalPages = calculateTotalPages(totalCount, DEFAULT_PAGE_SIZE);
            
            log.info("전체 리더 스킬 수: {}, 예상 페이지 수: {}", totalCount, totalPages);
            
            // 모든 페이지 처리
            for (int page = 1; page <= totalPages; page++) {
                log.info("페이지 {} 동기화 시작", page);
                int synced = syncLeaderSkillsByPage(page);
                totalSynced += synced;
                
                // API 부하 방지를 위한 짧은 대기
                if (page < totalPages) {
                    Thread.sleep(500);
                }
            }
            
            log.info("===== Swarfarm 리더 스킬 동기화 완료. 총 {}개 동기화 =====", totalSynced);
        } catch (Exception e) {
            log.error("리더 스킬 동기화 중 오류 발생", e);
        }
        
        return totalSynced;
    }
    
    @Override
    public int syncLeaderSkillsByPage(int page) {
        try {
            String apiUrl = SWARFARM_API_BASE_URL + "?format=json&page=" + page;
            SwarfarmLeaderSkillResponse response = fetchLeaderSkillData(apiUrl);
            
            if (response == null || response.getResults() == null) {
                log.warn("페이지 {} 데이터가 없습니다.", page);
                return 0;
            }
            
            int syncedCount = 0;
            for (SwarfarmLeaderSkillResponse.LeaderSkillData leaderSkill : response.getResults()) {
                try {
                    // 이미 존재하는 리더 스킬인지 확인
                    if (existsLeaderSkill(leaderSkill.getId())) {
                        log.debug("리더 스킬 ID {}는 이미 존재합니다. 건너뜁니다.", leaderSkill.getId());
                        continue;
                    }
                    
                    // 리더 스킬 데이터 변환
                    Map<String, Object> leaderSkillData = convertToMap(leaderSkill);
                    
                    // DB 저장
                    if (saveLeaderSkill(leaderSkillData)) {
                        syncedCount++;
                    }
                } catch (Exception e) {
                    log.error("리더 스킬 저장 중 오류 발생: {}", leaderSkill.getId(), e);
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
    public boolean saveLeaderSkill(Map<String, Object> leaderSkillData) {
        try {
            // leader_skill_id는 Swarfarm API ID를 그대로 사용
            Integer leaderSkillId = (Integer) leaderSkillData.get("leader_skill_id");
            if (leaderSkillId == null) {
                log.warn("leader_skill_id가 없어서 저장할 수 없습니다.");
                return false;
            }
            
            // 기존 데이터 업데이트 또는 신규 삽입
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
        Map<String, Object> map = new HashMap<>();
        
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
}

