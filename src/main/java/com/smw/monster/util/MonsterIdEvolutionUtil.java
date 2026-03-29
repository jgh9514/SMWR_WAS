package com.smw.monster.util;

/**
 * 프론트 monsterIdEvolution.ts 와 동일: monster_id 끝에서 속성·각성 단위 해석.
 */
public final class MonsterIdEvolutionUtil {

	private MonsterIdEvolutionUtil() {
	}

	public static String evolutionGroupKey(String monsterId) {
		if (monsterId == null) {
			return "";
		}
		String s = monsterId.trim();
		if (s.length() < 2) {
			return "solo:" + s;
		}
		return s.substring(0, s.length() - 2) + s.substring(s.length() - 1);
	}

	public static int awakenStepDigit(String monsterId) {
		if (monsterId == null || monsterId.length() < 2) {
			return -1;
		}
		char c = monsterId.charAt(monsterId.length() - 2);
		if (c >= '0' && c <= '9') {
			return c - '0';
		}
		return -1;
	}
}
