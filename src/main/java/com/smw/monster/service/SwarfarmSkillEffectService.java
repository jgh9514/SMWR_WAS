package com.smw.monster.service;

import java.util.Map;

public interface SwarfarmSkillEffectService {
    
    /**
     * Swarfarm API에서 모든 스킬 이펙트 데이터를 동기화
     * @return 동기화된 스킬 이펙트 수
     */
    int syncAllSkillEffects();
    
    /**
     * 특정 페이지의 스킬 이펙트 데이터를 가져와서 저장
     * @param page 페이지 번호 (1부터 시작)
     * @return 저장된 스킬 이펙트 수
     */
    int syncSkillEffectsByPage(int page);
    
    /**
     * Swarfarm API에서 스킬 이펙트 이미지를 다운로드
     * @param iconFilename 아이콘 파일명
     * @return 저장된 이미지 경로
     */
    String downloadSkillEffectImage(String iconFilename);
    
    /**
     * 스킬 이펙트 데이터를 DB에 저장
     * @param effectData 스킬 이펙트 데이터
     * @return 저장 성공 여부
     */
    boolean saveSkillEffect(Map<String, Object> effectData);
    
    /**
     * 이미 존재하는 스킬 이펙트인지 확인
     * @param effectId Swarfarm API ID
     * @return 존재 여부
     */
    boolean existsSkillEffect(Integer effectId);
    
    /**
     * 총 페이지 수 계산
     * @param totalCount 전체 개수
     * @param pageSize 페이지 크기
     * @return 총 페이지 수
     */
    int calculateTotalPages(int totalCount, int pageSize);
}

