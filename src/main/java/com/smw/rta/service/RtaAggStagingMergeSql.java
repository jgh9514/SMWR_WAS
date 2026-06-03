package com.smw.rta.service;

/**
 * COPY 직후 동일 JDBC {@link java.sql.Connection}에서 merge 를 실행하기 위한 SQL.
 * MyBatis XML({@code rta-queries-batch-meta.xml}) 과 문장을 동기화할 것.
 */
final class RtaAggStagingMergeSql {

	private RtaAggStagingMergeSql() {
	}

	/**
	 * staging → 솔로/듀오/트리오 집계 테이블 누적 merge.
	 * <p>
	 * GROUP BY 없음: Java {@code accumulateSynergyAgg()} HashMap 이 (season_id, rating_id, combo_unit_key) 를
	 * 이미 유니크하게 합산하므로 staging 내 중복이 없다.
	 */
	/**
	 * staging(combo_size=1) → rta_agg_synergy_solo 누적 merge.
	 * pick_cnt = match_cnt (COPY 시 ban_cnt=0), lose_cnt = match_cnt - win_cnt.
	 * ban_cnt 는 기존 값 유지.
	 */
	static final String MERGE_SYNERGY_STAGING_INTO_SOLO = """
			INSERT INTO public.rta_agg_synergy_solo (
			    season_id, rating_id, combo_unit_key,
			    pick_cnt, ban_cnt, match_cnt, win_cnt, lose_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.combo_unit_key,
			    s.match_cnt,
			    COALESCE(s.ban_cnt, 0),
			    s.match_cnt,
			    s.win_cnt,
			    (s.match_cnt - s.win_cnt)
			FROM public.staging_synergy_agg s
			WHERE s.combo_size = 1
			ON CONFLICT (season_id, rating_id, combo_unit_key) DO UPDATE SET
			    pick_cnt  = public.rta_agg_synergy_solo.pick_cnt  + EXCLUDED.pick_cnt,
			    match_cnt = public.rta_agg_synergy_solo.match_cnt + EXCLUDED.match_cnt,
			    win_cnt   = public.rta_agg_synergy_solo.win_cnt   + EXCLUDED.win_cnt,
			    lose_cnt  = public.rta_agg_synergy_solo.lose_cnt  + EXCLUDED.lose_cnt,
			    ban_cnt   = public.rta_agg_synergy_solo.ban_cnt
			""";

	static final String MERGE_SYNERGY_STAGING_INTO_DUO = """
			INSERT INTO public.rta_agg_synergy_duo (
			    season_id, rating_id, combo_unit_key, match_cnt, win_cnt, lose_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.combo_unit_key,
			    s.match_cnt,
			    s.win_cnt,
			    (s.match_cnt - s.win_cnt)
			FROM public.staging_synergy_agg s
			WHERE s.combo_size = 2
			ON CONFLICT (season_id, rating_id, combo_unit_key) DO UPDATE SET
			    match_cnt = public.rta_agg_synergy_duo.match_cnt + EXCLUDED.match_cnt,
			    win_cnt   = public.rta_agg_synergy_duo.win_cnt   + EXCLUDED.win_cnt,
			    lose_cnt  = public.rta_agg_synergy_duo.lose_cnt  + EXCLUDED.lose_cnt
			""";

	static final String MERGE_SYNERGY_STAGING_INTO_TRIO = """
			INSERT INTO public.rta_agg_synergy_trio (
			    season_id, rating_id, combo_unit_key, match_cnt, win_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.combo_unit_key,
			    s.match_cnt,
			    s.win_cnt
			FROM public.staging_synergy_agg s
			WHERE s.combo_size = 3
			ON CONFLICT (season_id, rating_id, combo_unit_key) DO UPDATE SET
			    match_cnt = public.rta_agg_synergy_trio.match_cnt + EXCLUDED.match_cnt,
			    win_cnt   = public.rta_agg_synergy_trio.win_cnt   + EXCLUDED.win_cnt
			""";

	/**
	 * staging(opponent_combo_size=1) → rta_agg_counter_solo 누적 merge.
	 * opponent_monster_id 는 bigint(단일 유닛 ID 문자열).
	 */
	static final String MERGE_MATCHUP_STAGING_INTO_COUNTER_SOLO = """
			INSERT INTO public.rta_agg_counter_solo (
			    season_id, rating_id, subject_monster_id, opponent_monster_id, win_cnt, lose_cnt, updated_at
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.subject_unit_id,
			    CAST(s.opponent_combo_key AS bigint),
			    s.win_cnt,
			    s.lose_cnt,
			    CURRENT_TIMESTAMP
		FROM public.staging_matchup_agg s
		WHERE s.opponent_combo_size = 1
		ORDER BY s.season_id, s.rating_id, s.subject_unit_id, CAST(s.opponent_combo_key AS bigint)
		ON CONFLICT (season_id, rating_id, subject_monster_id, opponent_monster_id) DO UPDATE SET
			    win_cnt    = public.rta_agg_counter_solo.win_cnt   + EXCLUDED.win_cnt,
			    lose_cnt   = public.rta_agg_counter_solo.lose_cnt  + EXCLUDED.lose_cnt,
			    updated_at = EXCLUDED.updated_at
			""";

	/**
	 * staging(opponent_combo_size=2) → rta_agg_counter_duo 누적 merge.
	 * opponent_monster_id 는 text 콤보 키.
	 */
	static final String MERGE_MATCHUP_STAGING_INTO_COUNTER_DUO = """
			INSERT INTO public.rta_agg_counter_duo (
			    season_id, rating_id, subject_monster_id, opponent_monster_id, win_cnt, lose_cnt, updated_at
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.subject_unit_id,
			    s.opponent_combo_key,
			    s.win_cnt,
			    s.lose_cnt,
			    CURRENT_TIMESTAMP
			FROM public.staging_matchup_agg s
			WHERE s.opponent_combo_size = 2
			ORDER BY s.season_id, s.rating_id, s.subject_unit_id, s.combo_key_hash, s.opponent_combo_key
			ON CONFLICT (season_id, rating_id, subject_monster_id, opponent_monster_id) DO UPDATE SET
			    win_cnt    = public.rta_agg_counter_duo.win_cnt   + EXCLUDED.win_cnt,
			    lose_cnt   = public.rta_agg_counter_duo.lose_cnt  + EXCLUDED.lose_cnt,
			    updated_at = EXCLUDED.updated_at
			""";

	/**
	 * staging(opponent_combo_size=3) → rta_agg_counter_trio 누적 merge.
	 * opponent_monster_id 는 text 콤보 키.
	 */
	static final String MERGE_MATCHUP_STAGING_INTO_COUNTER_TRIO = """
			INSERT INTO public.rta_agg_counter_trio (
			    season_id, rating_id, subject_monster_id, opponent_monster_id, win_cnt, lose_cnt, updated_at
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.subject_unit_id,
			    s.opponent_combo_key,
			    s.win_cnt,
			    s.lose_cnt,
			    CURRENT_TIMESTAMP
			FROM public.staging_matchup_agg s
			WHERE s.opponent_combo_size = 3
			ORDER BY s.season_id, s.rating_id, s.subject_unit_id, s.combo_key_hash, s.opponent_combo_key
			ON CONFLICT (season_id, rating_id, subject_monster_id, opponent_monster_id) DO UPDATE SET
			    win_cnt    = public.rta_agg_counter_trio.win_cnt   + EXCLUDED.win_cnt,
			    lose_cnt   = public.rta_agg_counter_trio.lose_cnt  + EXCLUDED.lose_cnt,
			    updated_at = EXCLUDED.updated_at
			""";
}
