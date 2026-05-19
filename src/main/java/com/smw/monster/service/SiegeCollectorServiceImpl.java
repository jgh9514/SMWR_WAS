package com.smw.monster.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.dto.response.SiegeApiArchiveResponse;
import com.smw.monster.dto.response.SiegeBattleLogItemResponse;
import com.smw.monster.dto.response.SiegeBattleLogListResponse;
import com.smw.monster.dto.response.SiegeBattleReplayResponse;
import com.smw.monster.mapper.SiegeCollectorMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiegeCollectorServiceImpl implements SiegeCollectorService {

	private final SiegeCollectorMapper siegeCollectorMapper;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional(readOnly = true)
	public SiegeBattleLogListResponse getMatchBattleLogs(Map<String, Object> param) {
		String matchId = stringVal(param.get("match_id"));
		if (matchId == null || matchId.isBlank()) {
			throw new IllegalArgumentException("match_id가 필요합니다.");
		}
		int paging = intVal(param.get("paging"), 20);
		int offset = parseOffset(param, paging);
		Map<String, Object> q = new HashMap<>(param);
		q.put("match_id", matchId);
		q.put("limit", paging);
		q.put("offset", offset);

		List<Map<String, ?>> rows = siegeCollectorMapper.selectMatchBattleLogList(q);
		Map<String, Object> countParam = new HashMap<>(q);
		countParam.remove("limit");
		countParam.remove("offset");
		int total = siegeCollectorMapper.selectMatchBattleLogCount(countParam);
		int totalPage = paging > 0 ? (int) Math.ceil(total / (double) paging) : 0;

		List<SiegeBattleLogItemResponse> list = new ArrayList<>(rows.size());
		for (Map<String, ?> row : rows) {
			list.add(toBattleLogItem(row));
		}
		return SiegeBattleLogListResponse.builder()
				.matchId(matchId)
				.list(list)
				.totalCount(total)
				.totalPage(totalPage)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public SiegeBattleReplayResponse getBattleReplay(long rid, Map<String, Object> param) {
		if (rid <= 0) {
			throw new IllegalArgumentException("rid가 필요합니다.");
		}
		Map<String, ?> raw = siegeCollectorMapper.selectBattleReplayRaw(rid);
		if (raw == null) {
			return null;
		}
		String matchId = stringVal(raw.get("match_id"));
		assertMatchAccess(matchId, param);

		Map<String, ?> payloadRow = siegeCollectorMapper.selectBattleReplayPayload(rid);
		Map<String, Object> payload = parseJsonMap(payloadRow != null ? payloadRow.get("payload_json") : null);

		return SiegeBattleReplayResponse.builder()
				.rid(rid)
				.matchId(matchId)
				.battleDesc(stringVal(raw.get("battle_desc")))
				.source(stringVal(raw.get("source")))
				.payload(payload)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public SiegeApiArchiveResponse getLatestApiArchive(Map<String, Object> param) {
		String command = stringVal(param.get("command"));
		if (command == null || command.isBlank()) {
			throw new IllegalArgumentException("command가 필요합니다.");
		}
		String matchId = stringVal(param.get("match_id"));
		if (matchId != null && !matchId.isBlank()) {
			assertMatchAccess(matchId, param);
		}
		Map<String, Object> q = new HashMap<>(param);
		q.put("command", command);
		Map<String, ?> row = siegeCollectorMapper.selectLatestApiArchive(q);
		if (row == null) {
			return null;
		}
		return SiegeApiArchiveResponse.builder()
				.id(longVal(row.get("id")))
				.command(stringVal(row.get("command")))
				.siegeId(longVal(row.get("siege_id")) > 0 ? longVal(row.get("siege_id")) : null)
				.matchId(stringVal(row.get("match_id")))
				.capturedAt(longVal(row.get("captured_at")))
				.logType(shortVal(row.get("log_type")))
				.baseNumber(shortVal(row.get("base_number")))
				.replayRid(longVal(row.get("replay_rid")) > 0 ? longVal(row.get("replay_rid")) : null)
				.source(stringVal(row.get("source")))
				.payload(parseJsonMap(row.get("payload_json")))
				.build();
	}

	private void assertMatchAccess(String matchId, Map<String, Object> param) {
		if (matchId == null || matchId.isBlank()) {
			return;
		}
		boolean viewAll = isTruthy(param.get("view_all_guilds"));
		if (viewAll) {
			return;
		}
		String guildId = param.get("view_guild_id") != null
				? stringVal(param.get("view_guild_id"))
				: stringVal(param.get("sess_guild_id"));
		if (guildId == null || guildId.isBlank()) {
			throw new IllegalArgumentException("길드 정보가 없습니다.");
		}
		int n = siegeCollectorMapper.countGuildAccessToMatch(matchId, guildId);
		if (n <= 0) {
			throw new IllegalArgumentException("해당 매치에 대한 조회 권한이 없습니다.");
		}
	}

	private SiegeBattleLogItemResponse toBattleLogItem(Map<String, ?> row) {
		Object fromCollector = row.get("from_collector");
		boolean collector = fromCollector instanceof Boolean b && b
				|| "true".equalsIgnoreCase(String.valueOf(fromCollector));
		return SiegeBattleLogItemResponse.builder()
				.logId(stringVal(row.get("log_id")))
				.logTimestamp(stringVal(row.get("log_timestamp")))
				.matchId(stringVal(row.get("match_id")))
				.baseNumber(intVal(row.get("base_number")) > 0 ? intVal(row.get("base_number")) : null)
				.guildId(stringVal(row.get("guild_id")))
				.wizardId(stringVal(row.get("wizard_id")))
				.wizardName(stringVal(row.get("wizard_name")))
				.oppGuildId(stringVal(row.get("opp_guild_id")))
				.oppWizardId(stringVal(row.get("opp_wizard_id")))
				.oppWizardName(stringVal(row.get("opp_wizard_name")))
				.winLose(stringVal(row.get("win_lose")))
				.replayRidRef(longVal(row.get("replay_rid_ref")) > 0 ? longVal(row.get("replay_rid_ref")) : null)
				.battleDesc(stringVal(row.get("battle_desc")))
				.matchScoreVar(intVal(row.get("match_score_var")) != 0 ? intVal(row.get("match_score_var")) : null)
				.wizardLevel(intVal(row.get("wizard_level")) > 0 ? intVal(row.get("wizard_level")) : null)
				.oppWizardLevel(intVal(row.get("opp_wizard_level")) > 0 ? intVal(row.get("opp_wizard_level")) : null)
				.logTypeApi(shortVal(row.get("log_type_api")) != 0 ? shortVal(row.get("log_type_api")) : null)
				.guildName(stringVal(row.get("guild_name")))
				.oppGuildName(stringVal(row.get("opp_guild_name")))
				.fromCollector(collector)
				.build();
	}

	private Map<String, Object> parseJsonMap(Object json) {
		if (json == null) {
			return Map.of();
		}
		String s = String.valueOf(json).trim();
		if (s.isEmpty()) {
			return Map.of();
		}
		try {
			return objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
		} catch (Exception e) {
			log.warn("[siege-collector] JSON parse failed: {}", e.getMessage());
			return Map.of();
		}
	}

	private static boolean isTruthy(Object o) {
		if (o instanceof Boolean b) {
			return b;
		}
		if (o == null) {
			return false;
		}
		String s = String.valueOf(o);
		return "true".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s);
	}

	private static int parseOffset(Map<String, Object> param, int paging) {
		if (param.get("offset") != null) {
			return Math.max(0, intVal(param.get("offset"), 0));
		}
		if (param.get("page") != null) {
			int page = intVal(param.get("page"), 1);
			return Math.max(0, (page - 1) * paging);
		}
		return 0;
	}

	private static String stringVal(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static int intVal(Object o, int defaultVal) {
		if (o == null) {
			return defaultVal;
		}
		if (o instanceof Number n) {
			return n.intValue();
		}
		try {
			return Integer.parseInt(String.valueOf(o));
		} catch (NumberFormatException e) {
			return defaultVal;
		}
	}

	private static long longVal(Object o) {
		if (o == null) {
			return 0L;
		}
		if (o instanceof Number n) {
			return n.longValue();
		}
		try {
			return Long.parseLong(String.valueOf(o));
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static short shortVal(Object o) {
		if (o == null) {
			return 0;
		}
		if (o instanceof Number n) {
			return n.shortValue();
		}
		try {
			return Short.parseShort(String.valueOf(o));
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
