package com.smw.account.service;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

public interface AccountSummaryService {

	/**
	 * SWEX JSON 업로드 & 저장 후 요약 반환
	 */
	Map<String, Object> uploadAndSave(MultipartFile jsonFile, String sessUserId) throws Exception;

	/**
	 * 최신 임포트 요약
	 */
	Map<String, Object> selectLatestImport(Map<String, Object> param);

	/**
	 * 임포트 목록
	 */
	List<Map<String, ?>> selectImportList(Map<String, Object> param);

	/**
	 * 임포트 상세(요약)
	 */
	Map<String, Object> selectImportDetail(Map<String, Object> param);

	/**
	 * 몬스터 목록 (페이징)
	 */
	Map<String, Object> selectMonsterList(Map<String, Object> param);

	/**
	 * 몬스터 도감(전체) + 보유 카운트 (페이징/검색/속성)
	 */
	Map<String, Object> selectMonsterCatalog(Map<String, Object> param);

	/**
	 * 룬 목록 (페이징)
	 */
	Map<String, Object> selectRuneList(Map<String, Object> param);

	/**
	 * 룬 속도 요약 (신속+잡룬 / 신속+의지 최고 속도)
	 */
	Map<String, Object> selectRuneScoreSummary(Map<String, Object> param);
}


