package com.smw.rta.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.ExecutorType;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.mapper.summonerswarMapper;
import com.sysconf.config.MybatisBatchConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Exporter full_log → raw 스테이징 전용 적재.
 * <p>
 * {@code ranker_rtpvp_replay_raw} + {@code ranker_rtpvp_replay_raw_payload} 두 테이블에만 쓴다.
 * 정규화 테이블({@code rta_match} 등) 조회·적재는 일절 하지 않는다.
 * 중복 rid는 ON CONFLICT(DB)로 처리하므로 사전 dedup 조회가 필요 없다.
 */
@Service
@Slf4j
public class RtaExporterRawIngestService {

	private static final int CHUNK_SIZE = 100;

	private final SqlSessionTemplate batchSqlSessionTemplate;
	private final ObjectMapper objectMapper;

	public RtaExporterRawIngestService(
			@Qualifier(MybatisBatchConfig.BATCH_SQL_SESSION_TEMPLATE) SqlSessionTemplate batchSqlSessionTemplate,
			ObjectMapper objectMapper) {
		this.batchSqlSessionTemplate = batchSqlSessionTemplate;
		this.objectMapper = objectMapper;
	}

	/**
	 * 파싱된 리플레이 아이템을 raw 스테이징 두 테이블에 적재한다.
	 *
	 * @param items {@link com.smw.monster.util.RankerRtpvpReplayLogParser#extractReplayItemsFromLogText} 결과
	 * @return {@code {success: N, fail: N}}
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Map<String, Integer> ingestRawItems(List<Map<String, Object>> items) {
		if (items == null || items.isEmpty()) {
			return resultMap(0, 0);
		}

		List<Map<String, Object>> rawRows = new ArrayList<>(items.size());
		int fail = 0;

		for (Map<String, Object> item : items) {
			Long rid = toLong(item.get("rid"));
			if (rid == null) {
				log.warn("[rta-exporter-raw] rid 없음 — 건너뜀");
				fail++;
				continue;
			}
			try {
				Map<String, Object> row = new HashMap<>(2);
				row.put("rid", rid);
				row.put("payload", objectMapper.writeValueAsString(item));
				rawRows.add(row);
			} catch (JsonProcessingException e) {
				log.warn("[rta-exporter-raw] JSON 직렬화 실패 rid={}: {}", rid, e.getMessage());
				fail++;
			}
		}

		if (rawRows.isEmpty()) {
			return resultMap(0, fail);
		}

		rawRows.sort(Comparator.comparingLong(m -> (Long) m.get("rid")));

		summonerswarMapper mapper = batchSqlSessionTemplate.getMapper(summonerswarMapper.class);
		try {
			for (int from = 0; from < rawRows.size(); from += CHUNK_SIZE) {
				List<Map<String, Object>> chunk = rawRows.subList(from, Math.min(from + CHUNK_SIZE, rawRows.size()));
				mapper.insertArenaReplayRawBulk(chunk);
				mapper.insertArenaReplayRawPayloadBulk(chunk);
			}
		} finally {
			batchSqlSessionTemplate.flushStatements();
		}

		log.info("[rta-exporter-raw] raw 적재 완료 success={} fail={}", rawRows.size(), fail);
		return resultMap(rawRows.size(), fail);
	}

	private static Long toLong(Object v) {
		if (v == null) return null;
		if (v instanceof Long l) return l;
		if (v instanceof Number n) return n.longValue();
		try { return Long.parseLong(String.valueOf(v).trim()); } catch (NumberFormatException e) { return null; }
	}

	private static Map<String, Integer> resultMap(int success, int fail) {
		Map<String, Integer> m = new HashMap<>(2);
		m.put("success", success);
		m.put("fail", fail);
		return m;
	}
}
