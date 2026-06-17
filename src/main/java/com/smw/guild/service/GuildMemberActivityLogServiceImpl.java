package com.smw.guild.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GuildMemberActivityLogServiceImpl implements GuildMemberActivityLogService {

	static final String ACTION_DECK_REGISTER = "DECK_REGISTER";
	static final String ACTION_DECK_UPDATE = "DECK_UPDATE";
	static final String ACTION_DECK_DELETE = "DECK_DELETE";
	static final String ACTION_DECK_VOTE_UP = "DECK_VOTE_UP";
	static final String ACTION_DECK_VOTE_DOWN = "DECK_VOTE_DOWN";
	static final String ACTION_DECK_VOTE_CLEAR = "DECK_VOTE_CLEAR";
	static final String ACTION_DEFENSE_DECK_REGISTER = "DEFENSE_DECK_REGISTER";

	@Autowired
	private GuildMemberActivityLogTxHelper txHelper;

	@Autowired
	private GuildMemberActivityLogSupport activityLogSupport;

	@Autowired
	private ObjectMapper objectMapper;

	@Override
	public void tryLogDeckRegister(Map<String, Object> param, String deckId) {
		appendDeckLog(param, ACTION_DECK_REGISTER, deckId, mergeParam(param, null));
	}

	@Override
	public void tryLogDeckUpdate(Map<String, Object> param, Map<String, ?> deck) {
		appendDeckLog(param, ACTION_DECK_UPDATE, str(deck != null ? deck.get("deck_id") : null), mergeParam(param, deck));
	}

	@Override
	public void tryLogDeckDelete(Map<String, Object> param, Map<String, ?> deck) {
		appendDeckLog(param, ACTION_DECK_DELETE, str(deck != null ? deck.get("deck_id") : null), mergeParam(param, deck));
	}

	@Override
	public void tryLogDeckVote(Map<String, Object> param, String deckId, String vote) {
		String normalized = vote != null ? vote.trim().toUpperCase() : "";
		String actionType;
		if ("UP".equals(normalized)) {
			actionType = ACTION_DECK_VOTE_UP;
		} else if ("DOWN".equals(normalized)) {
			actionType = ACTION_DECK_VOTE_DOWN;
		} else {
			actionType = ACTION_DECK_VOTE_CLEAR;
			normalized = "CLEAR";
		}
		Map<String, Object> context = mergeParam(param, null);
		Map<String, Object> detail = buildDeckDetail(context, deckId);
		detail.put("vote", normalized);
		appendLog(param, actionType, deckId, buildSummary(actionType, context, deckId), detail);
	}

	@Override
	public void tryLogDefenseDeckRegister(Map<String, Object> param) {
		Map<String, Object> context = mergeParam(param, null);
		Map<String, Object> detail = buildDefenseDetail(context);
		appendLog(param, ACTION_DEFENSE_DECK_REGISTER, null,
				buildSummary(ACTION_DEFENSE_DECK_REGISTER, context, null), detail);
	}

	private void appendDeckLog(Map<String, Object> param, String actionType, String deckId, Map<String, Object> context) {
		String resolvedDeckId = deckId != null && !deckId.isBlank() ? deckId : str(context.get("deck_id"));
		Map<String, Object> detail = buildDeckDetail(context, resolvedDeckId);
		appendLog(param, actionType, resolvedDeckId, buildSummary(actionType, context, resolvedDeckId), detail);
	}

	private String buildSummary(String actionType, Map<String, Object> context, String deckId) {
		Set<String> monsterIds = activityLogSupport.collectMonsterIds(context);
		Map<String, String> nameById = activityLogSupport.loadKrNames(monsterIds);
		return activityLogSupport.buildDisplaySummary(actionType, context, str(deckId), nameById);
	}

	private void appendLog(Map<String, Object> param, String actionType, String referenceId,
			String summary, Map<String, Object> detail) {
		if (param == null) {
			return;
		}
		String guildId = str(param.get("sess_guild_id"));
		String userId = str(param.get("sess_user_id"));
		if (guildId.isEmpty() || userId.isEmpty()) {
			return;
		}
		Map<String, Object> row = new HashMap<>();
		row.put("guild_id", guildId);
		row.put("user_id", userId);
		row.put("action_type", actionType);
		row.put("reference_id", referenceId);
		row.put("summary", summary != null ? summary : "");
		row.put("detail_json", toJson(detail));
		txHelper.insertGuildMemberActivityLog(row);
	}

	private Map<String, Object> mergeParam(Map<String, Object> param, Map<String, ?> deck) {
		Map<String, Object> merged = new LinkedHashMap<>();
		if (deck != null) {
			for (Map.Entry<String, ?> e : deck.entrySet()) {
				if (e.getKey() != null && e.getValue() != null) {
					merged.put(e.getKey(), e.getValue());
				}
			}
		}
		if (param != null) {
			copyIfPresent(param, merged, "deck_id");
			copyIfPresent(param, merged, "def_monster_1");
			copyIfPresent(param, merged, "def_monster_2");
			copyIfPresent(param, merged, "def_monster_3");
			copyIfPresent(param, merged, "atk_monster_1");
			copyIfPresent(param, merged, "atk_monster_2");
			copyIfPresent(param, merged, "atk_monster_3");
			copyIfPresent(param, merged, "deck_comment");
		}
		return merged;
	}

	private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
		Object v = from.get(key);
		if (v != null && !String.valueOf(v).trim().isEmpty()) {
			to.put(key, v);
		}
	}

	private Map<String, Object> buildDeckDetail(Map<String, Object> param, String deckId) {
		Map<String, Object> detail = new LinkedHashMap<>();
		if (deckId != null && !deckId.isBlank()) {
			detail.put("deck_id", deckId);
		}
		putMonsterTriplet(detail, param, "def_monster_");
		putMonsterTriplet(detail, param, "atk_monster_");
		Object comment = param != null ? param.get("deck_comment") : null;
		if (comment != null && !String.valueOf(comment).trim().isEmpty()) {
			detail.put("deck_comment", String.valueOf(comment).trim());
		}
		return detail;
	}

	private Map<String, Object> buildDefenseDetail(Map<String, Object> param) {
		Map<String, Object> detail = new LinkedHashMap<>();
		putMonsterTriplet(detail, param, "def_monster_");
		return detail;
	}

	private static void putMonsterTriplet(Map<String, Object> detail, Map<String, Object> param, String prefix) {
		if (param == null) {
			return;
		}
		for (int i = 1; i <= 3; i++) {
			String key = prefix + i;
			Object v = param.get(key);
			if (v != null && !String.valueOf(v).trim().isEmpty()) {
				detail.put(key, String.valueOf(v).trim());
			}
		}
	}

	private String toJson(Map<String, Object> detail) {
		if (detail == null || detail.isEmpty()) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(detail);
		} catch (JsonProcessingException e) {
			log.debug("활동 로그 detail_json 직렬화 실패", e);
			return null;
		}
	}

	private static String str(Object o) {
		if (o == null) {
			return "";
		}
		String s = String.valueOf(o).trim();
		return s.isEmpty() ? "" : s;
	}
}
