package com.admin.log.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.admin.batch.mapper.BatchMapper;
import com.admin.log.mapper.LogMapper;
import com.sysconf.util.DateUtil;

@Service
public class LogServiceImpl implements LogService {

	@Autowired
	DateUtil dateUtil;

	@Autowired
	LogMapper mapper;

	@Autowired
	BatchMapper batchMapper;
	
	@Override
	public List<Map<String, ?>> selectLoginHisList(Map<String, Object> param) {
		return mapper.selectLoginHisList(param);
	}
	
	@Override
	public List<Map<String, ?>> selectApiHisList(Map<String, Object> param) {
		return mapper.selectApiHisList(param);
	}
	
	@Override
	public List<Map<String, ?>> selectBatHisList(Map<String, Object> param) {
		return batchMapper.selectBatHisList(param);
	}
	
	@Override
	public String selectDetailBatHis(String id) {
		return mapper.selectDetailBatHis(id);
	}

	@Override
	public List<Map<String, ?>> selectBatchList(Map<String, Object> param) {
		return batchMapper.selectBatchList(param);
	}

	@Override
	public void insertApiLog(Map<String, Object> param) {
		Map<String, Object> api = mapper.selectApiByUrl(param);
		
		// 등록되지 않은 API URL인 경우 로그만 남기지 않고 종료
		if (api == null) {
			return;
		}

		param.put("api_id", api.get("api_id"));
		param.put("exe_dtm", dateUtil.now());
		mapper.insertApiExecutionLog(param);
	}

	@Override
	public List<Map<String, String>> selectBatchConfig(Map<String, Object> param) {
		Map<String, Object> queryParam = param != null ? new HashMap<>(param) : new HashMap<>();
		return batchMapper.selectBatchConfig(queryParam);
	}
}
