package com.admin.log.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.admin.batch.mapper.BatchMapper;
import com.admin.log.mapper.LogMapper;
import com.sysconf.util.DateUtil;

@Service
@Primary
public class LogServiceImpl implements LogService {
	
	private static final String API_ID_NONE = "__NONE__";
	private final ConcurrentHashMap<String, String> apiIdCache = new ConcurrentHashMap<>();
	private final ExecutorService apiLogExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
		private final AtomicInteger seq = new AtomicInteger(1);
		@Override
		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, "api-log-" + seq.getAndIncrement());
			t.setDaemon(true);
			return t;
		}
	});

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
		if (param == null) {
			return;
		}
		// URL이 없으면 스킵
		Object urlObj = param != null ? param.get("url") : null;
		String url = urlObj != null ? urlObj.toString() : null;
		if (url == null || url.isEmpty()) {
			return;
		}

		// API URL -> api_id 캐시 (DB select 최소화)
		String cached = apiIdCache.get(url);
		if (API_ID_NONE.equals(cached)) {
			return; // 등록되지 않은 API
		}
		if (cached == null) {
			Map<String, Object> api = mapper.selectApiByUrl(param);
			if (api == null || api.get("api_id") == null) {
				apiIdCache.put(url, API_ID_NONE);
				return;
			}
			cached = api.get("api_id").toString();
			apiIdCache.put(url, cached);
		}

		param.put("api_id", cached);
		param.put("exe_dtm", dateUtil.now());
		mapper.insertApiExecutionLog(param);
	}
	
	@Override
	public void insertApiLogAsync(Map<String, Object> param) {
		// 요청 thread에서 분리 (copy해서 동시성 이슈 방지)
		final Map<String, Object> copy = param != null ? new HashMap<>(param) : new HashMap<>();
		final java.util.Map<String, String> mdc = ThreadContext.getImmutableContext();
		apiLogExecutor.submit(() -> {
			if (mdc != null && !mdc.isEmpty()) {
				ThreadContext.putAll(mdc);
			}
			try {
				insertApiLog(copy);
			} catch (Exception ignore) {
				// 로깅 실패는 업무 흐름에 영향 주지 않음
			} finally {
				if (mdc != null && !mdc.isEmpty()) {
					for (String k : mdc.keySet()) ThreadContext.remove(k);
				}
			}
		});
	}
	
	@PreDestroy
	public void shutdown() {
		try {
			apiLogExecutor.shutdown();
		} catch (Exception ignore) {
			// no-op
		}
	}

	@Override
	public List<Map<String, String>> selectBatchConfig(Map<String, Object> param) {
		Map<String, Object> queryParam = param != null ? new HashMap<>(param) : new HashMap<>();
		return batchMapper.selectBatchConfig(queryParam);
	}
}
