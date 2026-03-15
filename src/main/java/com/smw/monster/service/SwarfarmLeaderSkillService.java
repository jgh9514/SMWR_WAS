package com.smw.monster.service;

import java.util.Map;

public interface SwarfarmLeaderSkillService {
    
    /**
     * Swarfarm API에서 모든 리더 스킬 데이터를 동기화
     * @return 동기화된 리더 스킬 수
     */
    int syncAllLeaderSkills();
    
    /**
     * 특정 페이지의 리더 스킬 데이터를 가져와서 저장
     * @param page 페이지 번호 (1부터 시작)
     * @return 저장된 리더 스킬 수
     */
    int syncLeaderSkillsByPage(int page);
    
    /**
     * 리더 스킬 데이터를 DB에 저장
     * @param leaderSkillData 리더 스킬 데이터
     * @return 저장 성공 여부
     */
    boolean saveLeaderSkill(Map<String, Object> leaderSkillData);
    
    /**
     * 이미 존재하는 리더 스킬인지 확인
     * @param leaderSkillId Swarfarm API ID
     * @return 존재 여부
     */
    boolean existsLeaderSkill(Integer leaderSkillId);
    
    /**
     * 총 페이지 수 계산
     * @param totalCount 전체 개수
     * @param pageSize 페이지 크기
     * @return 총 페이지 수
     */
    int calculateTotalPages(int totalCount, int pageSize);
}

