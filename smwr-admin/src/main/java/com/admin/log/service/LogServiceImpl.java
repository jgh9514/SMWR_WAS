package com.admin.log.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.admin.batch.mapper.BatchMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class LogServiceImpl implements LogService {

	@Autowired
	private BatchMapper batchMapper;

	@Override
	public List<Map<String, String>> selectBatchConfig(Map<String, Object> param) {
		if (param == null) {
			return batchMapper.selectBatchConfig(Collections.<String, Object>emptyMap());
		}
		return batchMapper.selectBatchConfig(param);
	}

	@Override
	public void insertApiLogAsync(Map<String, Object> param) {
		// 기존 로그 저장 서비스 소스가 현재 저장소에 없어서, 우선 요청 흐름만 깨지지 않게 유지한다.
		if (log.isDebugEnabled()) {
			log.debug("API 로그 저장 스킵: {}", param);
		}
	}

	@Override
	public List<Map<String, ?>> selectLoginHisList(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public List<Map<String, ?>> selectApiHisList(Map<String, Object> param) {
		return Collections.emptyList();
	}

	@Override
	public List<Map<String, ?>> selectBatHisList(Map<String, Object> param) {
		if (param == null) {
			return batchMapper.selectBatHisList(Collections.<String, Object>emptyMap());
		}
		return batchMapper.selectBatHisList(param);
	}

	@Override
	public List<Map<String, ?>> selectBatchList(Map<String, Object> param) {
		if (param == null) {
			return batchMapper.selectBatchList(Collections.<String, Object>emptyMap());
		}
		return batchMapper.selectBatchList(param);
	}

	@Override
	public String selectDetailBatHis(String id) {
		if (id == null || id.trim().isEmpty()) {
			return "";
		}
		try {
			Map<String, ?> detail = batchMapper.selectBatchRunHisDetail(Long.valueOf(id));
			if (detail == null || detail.get("rslt_txt") == null) {
				return "";
			}
			return String.valueOf(detail.get("rslt_txt"));
		} catch (NumberFormatException e) {
			log.warn("배치 이력 상세 조회 실패: 잘못된 id={}", id);
			return "";
		}
	}
}
