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
	static final String MERGE_SYNERGY_STAGING_INTO_SOLO = """
			INSERT INTO public.rta_agg_synergy_solo (
			    season_id, rating_id, combo_unit_key, match_cnt, win_cnt, ban_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.combo_unit_key,
			    s.match_cnt::bigint,
			    s.win_cnt::bigint,
			    COALESCE(s.ban_cnt, 0)::bigint
			FROM public.staging_synergy_agg s
			WHERE s.combo_size = 1
			ON CONFLICT (season_id, rating_id, combo_unit_key) DO UPDATE SET
			    match_cnt = public.rta_agg_synergy_solo.match_cnt + EXCLUDED.match_cnt,
			    win_cnt = public.rta_agg_synergy_solo.win_cnt + EXCLUDED.win_cnt,
			    ban_cnt = public.rta_agg_synergy_solo.ban_cnt
			""";

	static final String MERGE_SYNERGY_STAGING_INTO_DUO = """
			INSERT INTO public.rta_agg_synergy_duo (
			    season_id, rating_id, combo_unit_key, match_cnt, win_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.combo_unit_key,
			    s.match_cnt::bigint,
			    s.win_cnt::bigint
			FROM public.staging_synergy_agg s
			WHERE s.combo_size = 2
			ON CONFLICT (season_id, rating_id, combo_unit_key) DO UPDATE SET
			    match_cnt = public.rta_agg_synergy_duo.match_cnt + EXCLUDED.match_cnt,
			    win_cnt = public.rta_agg_synergy_duo.win_cnt + EXCLUDED.win_cnt
			""";

	static final String MERGE_SYNERGY_STAGING_INTO_TRIO = """
			INSERT INTO public.rta_agg_synergy_trio (
			    season_id, rating_id, combo_unit_key, match_cnt, win_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.combo_unit_key,
			    s.match_cnt::bigint,
			    s.win_cnt::bigint
			FROM public.staging_synergy_agg s
			WHERE s.combo_size = 3
			ON CONFLICT (season_id, rating_id, combo_unit_key) DO UPDATE SET
			    match_cnt = public.rta_agg_synergy_trio.match_cnt + EXCLUDED.match_cnt,
			    win_cnt = public.rta_agg_synergy_trio.win_cnt + EXCLUDED.win_cnt
			""";

	/**
	 * staging → rta_agg_counter_matchup 누적 merge.
	 * <p>
	 * GROUP BY 없음: Java {@code accumulateCounterMatchup()} HashMap 이
	 * (season_id, rating_id, subject_unit_id, opponent_combo_key) 를 이미 유니크하게 합산.
	 * <p>
	 * ORDER BY: 유니크 인덱스 컬럼 순서(season_id, rating_id, subject_unit_id, combo_key_hash, opponent_combo_key)
	 * 와 동일한 순서로 행을 밀어 넣어 B-tree 페이지를 순차적으로 접근 — 랜덤 I/O 최소화.
	 */
	static final String MERGE_MATCHUP_STAGING_INTO_COUNTER = """
			INSERT INTO public.rta_agg_counter_matchup (
			    season_id, rating_id, subject_unit_id, opponent_combo_key, opponent_combo_size, win_cnt, lose_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.subject_unit_id,
			    s.opponent_combo_key,
			    s.opponent_combo_size::smallint,
			    s.win_cnt::bigint,
			    s.lose_cnt::bigint
			FROM public.staging_matchup_agg s
			ORDER BY s.season_id, s.rating_id, s.subject_unit_id, s.combo_key_hash, s.opponent_combo_key
			ON CONFLICT (season_id, rating_id, subject_unit_id, combo_key_hash, opponent_combo_key) DO UPDATE SET
			    win_cnt = public.rta_agg_counter_matchup.win_cnt + EXCLUDED.win_cnt,
			    lose_cnt = public.rta_agg_counter_matchup.lose_cnt + EXCLUDED.lose_cnt,
			    opponent_combo_size = EXCLUDED.opponent_combo_size
			""";
}
