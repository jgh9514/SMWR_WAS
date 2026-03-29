package com.smw.monster.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 몬스터 상세 전용: 노말/1각/2각 + 다른 속성 패밀리 (monster-list와 무관)
 */
public final class MonsterDetailContextBuilder {

	private MonsterDetailContextBuilder() {
	}

	private static String elementKey(Object raw) {
		if (raw == null) {
			return "";
		}
		String s = raw.toString().trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) {
			return "";
		}
		if ("1".equals(s) || "fire".equals(s) || "불".equals(s)) {
			return "fire";
		}
		if ("2".equals(s) || "water".equals(s) || "물".equals(s)) {
			return "water";
		}
		if ("3".equals(s) || "wind".equals(s) || "바람".equals(s)) {
			return "wind";
		}
		if ("4".equals(s) || "light".equals(s) || "빛".equals(s)) {
			return "light";
		}
		if ("5".equals(s) || "dark".equals(s) || "어둠".equals(s)) {
			return "dark";
		}
		return "";
	}

	private static int awakenScore(Map<String, ?> row) {
		Object idObj = row.get("monster_id");
		if (idObj == null) {
			return 0;
		}
		int d = MonsterIdEvolutionUtil.awakenStepDigit(idObj.toString());
		if (d == 1) {
			return 3;
		}
		if (d == 2) {
			return 2;
		}
		if (d == 0) {
			return 1;
		}
		return 0;
	}

	private static Map<String, Object> slimRow(Map<String, ?> row) {
		Map<String, Object> m = new LinkedHashMap<>();
		putIfPresent(m, row, "monster_id");
		putIfPresent(m, row, "kr_name");
		putIfPresent(m, row, "un_name");
		putIfPresent(m, row, "monster_elemental");
		putIfPresent(m, row, "image_url");
		putIfPresent(m, row, "star");
		putIfPresent(m, row, "archetype");
		putIfPresent(m, row, "arousal_type");
		putIfPresent(m, row, "family_id");
		return m;
	}

	private static void putIfPresent(Map<String, Object> dest, Map<String, ?> src, String key) {
		if (src.containsKey(key) && src.get(key) != null) {
			dest.put(key, src.get(key));
		}
	}

	public static Map<String, Object> build(String monsterId, String currentElemental, List<Map<String, ?>> familyRows) {
		Map<String, Object> ctx = new LinkedHashMap<>();
		if (monsterId == null || familyRows == null || familyRows.isEmpty()) {
			ctx.put("evolution", evolutionEmpty());
			ctx.put("siblings", new ArrayList<>());
			return ctx;
		}
		String egk = MonsterIdEvolutionUtil.evolutionGroupKey(monsterId);
		List<Map<String, ?>> evoLine = new ArrayList<>();
		for (Map<String, ?> r : familyRows) {
			Object mid = r.get("monster_id");
			if (mid != null && egk.equals(MonsterIdEvolutionUtil.evolutionGroupKey(mid.toString()))) {
				evoLine.add(r);
			}
		}
		evoLine.sort(Comparator
				.comparingInt((Map<String, ?> r) -> {
					int d = MonsterIdEvolutionUtil.awakenStepDigit(Objects.toString(r.get("monster_id"), ""));
					return d >= 0 ? d : 999;
				})
				.thenComparing(r -> Objects.toString(r.get("monster_id"), "")));

		Map<String, Object> evolution = new LinkedHashMap<>();
		evolution.put("normal", evoLine.size() > 0 ? slimRow(evoLine.get(0)) : null);
		evolution.put("awakened", evoLine.size() > 1 ? slimRow(evoLine.get(1)) : null);
		evolution.put("second_awakening", evoLine.size() > 2 ? slimRow(evoLine.get(2)) : null);
		ctx.put("evolution", evolution);

		String curEl = elementKey(currentElemental);
		Map<String, List<Map<String, ?>>> byEl = new HashMap<>();
		for (Map<String, ?> r : familyRows) {
			String ek = elementKey(r.get("monster_elemental"));
			if (ek.isEmpty()) {
				continue;
			}
			if (!curEl.isEmpty() && ek.equals(curEl)) {
				continue;
			}
			byEl.computeIfAbsent(ek, k -> new ArrayList<>()).add(r);
		}
		String[] order = { "fire", "water", "wind", "light", "dark" };
		List<Map<String, Object>> siblings = new ArrayList<>();
		for (String el : order) {
			List<Map<String, ?>> list = byEl.get(el);
			if (list == null || list.isEmpty()) {
				continue;
			}
			Map<String, ?> best = list.stream().max(Comparator.comparingInt(MonsterDetailContextBuilder::awakenScore)).orElse(list.get(0));
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("element", el);
			item.put("monster", slimRow(best));
			siblings.add(item);
		}
		ctx.put("siblings", siblings);
		return ctx;
	}

	private static Map<String, Object> evolutionEmpty() {
		Map<String, Object> evolution = new LinkedHashMap<>();
		evolution.put("normal", null);
		evolution.put("awakened", null);
		evolution.put("second_awakening", null);
		return evolution;
	}
}
