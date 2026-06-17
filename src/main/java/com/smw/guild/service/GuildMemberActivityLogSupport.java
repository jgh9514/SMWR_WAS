package com.smw.guild.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.smw.guild.mapper.GuildMemberActivityLogMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GuildMemberActivityLogSupport {

	private static final int SUMMARY_MAX = 500;

	@Autowired
	private GuildMemberActivityLogMapper guildMemberActivityLogMapper;

	@Autowired
	private ObjectMapper objectMapper;

	@Transactional(readOnly = true)
	public Map<String, String> loadKrNames(Collection<String> monsterIds) {
		if (monsterIds == null || monsterIds.isEmpty()) {
			return Map.of();
		}
		List<String> ids = new ArrayList<>();
		for (String id : monsterIds) {
			if (id != null && !id.trim().isEmpty()) {
				ids.add(id.trim());
			}
		}
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<String, Object> param = new HashMap<>();
		param.put("monster_ids", ids);
		List<Map<String, ?>> rows = guildMemberActivityLogMapper.selectMonsterKrNamesByIds(param);
		Map<String, String> nameById = new HashMap<>();
		if (rows == null) {
			return nameById;
		}
		for (Map<String, ?> row : rows) {
			if (row == null) {
				continue;
			}
			String monsterId = str(row.get("monster_id"));
			String krName = str(row.get("kr_name"));
			if (!monsterId.isEmpty() && !krName.isEmpty()) {
				nameById.put(monsterId, krName);
			}
		}
		return nameById;
	}

	public void enrichActivityLogRows(List<Map<String, ?>> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		Set<String> monsterIds = new LinkedHashSet<>();
		for (Map<String, ?> row : rows) {
			collectMonsterIds(parseDetail(row != null ? row.get("detail_json") : null), monsterIds);
		}
		Map<String, String> nameById = loadKrNames(monsterIds);
		for (Map<String, ?> row : rows) {
			if (!(row instanceof Map)) {
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> mutable = (Map<String, Object>) row;
			Map<String, Object> detail = parseDetail(mutable.get("detail_json"));
			String summary = buildDisplaySummary(
					str(mutable.get("action_type")),
					detail,
					str(mutable.get("reference_id")),
					nameById);
			if (!summary.isEmpty()) {
				mutable.put("summary", summary);
			}
		}
	}

	public String buildMatchupLabel(Map<String, Object> param, Map<String, String> nameById) {
		if (param == null) {
			return "";
		}
		String def = joinMonsters(param, "def_monster_", nameById);
		String atk = joinMonsters(param, "atk_monster_", nameById);
		if (def.isEmpty() && atk.isEmpty()) {
			return "";
		}
		if (def.isEmpty()) {
			return "공:" + atk;
		}
		if (atk.isEmpty()) {
			return "방:" + def;
		}
		return "방:" + def + " vs 공:" + atk;
	}

	public String buildDisplaySummary(String actionType, Map<String, Object> detail, String referenceId,
			Map<String, String> nameById) {
		String verb = actionVerb(actionType);
		if (verb.isEmpty()) {
			return "";
		}
		String matchup = buildMatchupLabel(detail, nameById);
		String deckSuffix = referenceId.isEmpty() ? "" : " (#" + referenceId + ")";
		if (GuildMemberActivityLogServiceImpl.ACTION_DEFENSE_DECK_REGISTER.equals(actionType)) {
			String def = joinMonsters(detail, "def_monster_", nameById);
			return truncate("방덱 수동 등록" + (def.isEmpty() ? "" : " · 방:" + def));
		}
		return truncate(verb + (matchup.isEmpty() ? "" : " · " + matchup) + deckSuffix);
	}

	public Set<String> collectMonsterIds(Map<String, Object> param) {
		Set<String> ids = new LinkedHashSet<>();
		collectMonsterIds(param, ids);
		return ids;
	}

	public static void collectMonsterIds(Map<String, Object> param, Set<String> ids) {
		if (param == null || ids == null) {
			return;
		}
		for (String prefix : new String[] { "def_monster_", "atk_monster_" }) {
			for (int i = 1; i <= 3; i++) {
				String id = str(param.get(prefix + i));
				if (!id.isEmpty()) {
					ids.add(id);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> parseDetail(Object raw) {
		if (raw == null) {
			return new LinkedHashMap<>();
		}
		if (raw instanceof Map) {
			return new LinkedHashMap<>((Map<String, Object>) raw);
		}
		String text = String.valueOf(raw).trim();
		if (text.isEmpty()) {
			return new LinkedHashMap<>();
		}
		try {
			return objectMapper.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {});
		} catch (Exception e) {
			log.debug("활동 로그 detail_json 파싱 실패", e);
			return new LinkedHashMap<>();
		}
	}

	private static String actionVerb(String actionType) {
		if (actionType == null) {
			return "";
		}
		return switch (actionType) {
			case GuildMemberActivityLogServiceImpl.ACTION_DECK_REGISTER -> "추천 공덱 등록";
			case GuildMemberActivityLogServiceImpl.ACTION_DECK_UPDATE -> "추천 공덱 수정";
			case GuildMemberActivityLogServiceImpl.ACTION_DECK_DELETE -> "추천 공덱 삭제";
			case GuildMemberActivityLogServiceImpl.ACTION_DECK_VOTE_UP -> "추천 공덱 추천";
			case GuildMemberActivityLogServiceImpl.ACTION_DECK_VOTE_DOWN -> "추천 공덱 비추천";
			case GuildMemberActivityLogServiceImpl.ACTION_DECK_VOTE_CLEAR -> "추천 공덱 투표 취소";
			default -> "";
		};
	}

	private static String joinMonsters(Map<String, Object> param, String prefix, Map<String, String> nameById) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 3; i++) {
			String id = str(param.get(prefix + i));
			if (id.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append('/');
			}
			String label = nameById != null ? nameById.get(id) : null;
			sb.append(label != null && !label.isEmpty() ? label : id);
		}
		return sb.toString();
	}

	private static String str(Object o) {
		if (o == null) {
			return "";
		}
		String s = String.valueOf(o).trim();
		return s.isEmpty() ? "" : s;
	}

	private static String truncate(String s) {
		if (s == null) {
			return "";
		}
		if (s.length() <= SUMMARY_MAX) {
			return s;
		}
		return s.substring(0, SUMMARY_MAX - 3) + "...";
	}
}
