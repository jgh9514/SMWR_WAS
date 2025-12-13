package com.smw.monster.service;

import java.util.Map;

public interface SwarfarmLevelService {
    
    /**
     * Swarfarm API에서 모든 레벨 데이터를 동기화
     * @return 동기화된 레벨 수
     */
    int syncAllLevels();
    
    /**
     * 특정 페이지의 레벨 데이터를 가져와서 저장
     * @param page 페이지 번호 (1부터 시작)
     * @return 저장된 레벨 수
     */
    int syncLevelsByPage(int page);
    
    /**
     * 레벨 데이터를 DB에 저장
     * @param levelData 레벨 데이터
     * @return 저장 성공 여부
     */
    boolean saveLevel(Map<String, Object> levelData);
    
    /**
     * 이미 존재하는 레벨인지 확인
     * @param levelId Swarfarm API ID
     * @return 존재 여부
     */
    boolean existsLevel(Integer levelId);
    
    /**
     * 총 페이지 수 계산
     * @param totalCount 전체 개수
     * @param pageSize 페이지 크기
     * @return 총 페이지 수
     */
    int calculateTotalPages(int totalCount, int pageSize);
}

