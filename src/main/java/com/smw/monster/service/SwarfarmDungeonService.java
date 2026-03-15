package com.smw.monster.service;

import java.util.Map;

public interface SwarfarmDungeonService {
    
    /**
     * Swarfarm API에서 모든 던전 데이터를 동기화
     * @return 동기화된 던전 수
     */
    int syncAllDungeons();
    
    /**
     * 특정 페이지의 던전 데이터를 가져와서 저장
     * @param page 페이지 번호 (1부터 시작)
     * @return 저장된 던전 수
     */
    int syncDungeonsByPage(int page);
    
    /**
     * Swarfarm API에서 던전 이미지를 다운로드
     * @param iconFilename 아이콘 파일명
     * @return 저장된 이미지 경로
     */
    String downloadDungeonImage(String iconFilename);
    
    /**
     * 던전 데이터를 DB에 저장
     * @param dungeonData 던전 데이터
     * @return 저장 성공 여부
     */
    boolean saveDungeon(Map<String, Object> dungeonData);
    
    /**
     * 이미 존재하는 던전인지 확인
     * @param dungeonId Swarfarm API ID
     * @return 존재 여부
     */
    boolean existsDungeon(Integer dungeonId);
    
    /**
     * 총 페이지 수 계산
     * @param totalCount 전체 개수
     * @param pageSize 페이지 크기
     * @return 총 페이지 수
     */
    int calculateTotalPages(int totalCount, int pageSize);
}

