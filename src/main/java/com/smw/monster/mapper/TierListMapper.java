package com.smw.monster.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TierListMapper {

    /** 사용자의 저장된 티어리스트 목록 조회 (최신순) */
    List<Map<String, Object>> selectTierListByUser(Map<String, Object> param);

    /** 단건 조회 (소유권 확인용) */
    Map<String, Object> selectTierListById(Map<String, Object> param);

    /** 티어리스트 저장 */
    int insertTierList(Map<String, Object> param);

    /** 제목 또는 데이터 수정 */
    int updateTierList(Map<String, Object> param);

    /** 티어리스트 삭제 */
    int deleteTierList(Map<String, Object> param);
}
