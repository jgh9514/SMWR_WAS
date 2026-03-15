package com.smw.monster.service;

import java.util.Map;
import java.util.function.Consumer;

public interface SwarfarmMonsterService {
    
    /**
     * Swarfarm API에서 모든 몬스터 데이터를 동기화
     * @return 동기화된 몬스터 수
     */
    int syncAllMonsters();
    
    /**
     * 로그 콜백 설정 (배치 실행 시 상세 로그 수집용)
     * @param logCallback 로그 콜백 함수
     */
    void setLogCallback(Consumer<String> logCallback);
    
    /**
     * 특정 페이지의 몬스터 데이터를 가져와서 저장
     * @param page 페이지 번호 (1부터 시작)
     * @return 저장된 몬스터 수
     */
    int syncMonstersByPage(int page);
    
    /**
     * Swarfarm API에서 몬스터 이미지를 다운로드
     * @param imageFilename 이미지 파일명
     * @param element 속성 (Fire, Water, Wind, Light, Dark)
     * @return 저장된 이미지 경로
     */
    String downloadMonsterImage(String imageFilename, String element);
    
    /**
     * 몬스터 데이터를 DB에 저장
     * @param monsterData 몬스터 데이터
     * @return 저장 성공 여부
     */
    boolean saveMonster(Map<String, Object> monsterData);
    
    /**
     * 이미 존재하는 몬스터인지 확인
     * @param swarfarmId Swarfarm API ID
     * @return 존재 여부
     */
    boolean existsMonster(Integer swarfarmId);
    
    /**
     * 총 페이지 수 계산
     * @param totalCount 전체 개수
     * @param pageSize 페이지 크기
     * @return 총 페이지 수
     */
    int calculateTotalPages(int totalCount, int pageSize);
}

