package com.smw.rta.service;

/**
 * COPY 직후 동일 JDBC {@link java.sql.Connection}에서 merge 를 실행하기 위한 SQL.
 * MyBatis XML({@code rta-queries-batch-meta.xml}) 과 문장을 동기화할 것.
 */
final class RtaAggStagingMergeSql {

	private RtaAggStagingMergeSql() {
	}

	static final String MERGE_SYNERGY_STAGING_INTO_COMBO = """
			INSERT INTO public.rta_agg_synergy_combo (
			    season_id, rating_id, combo_unit_key, combo_size, match_cnt, win_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.combo_unit_key,
			    MAX(s.combo_size)::smallint AS combo_size,
			    SUM(s.match_cnt)::bigint AS match_cnt,
			    SUM(s.win_cnt)::bigint AS win_cnt
			FROM public.staging_synergy_agg s
			GROUP BY s.season_id, s.rating_id, s.combo_unit_key
			ON CONFLICT (season_id, rating_id, combo_unit_key) DO UPDATE SET
			    match_cnt = public.rta_agg_synergy_combo.match_cnt + EXCLUDED.match_cnt,
			    win_cnt = public.rta_agg_synergy_combo.win_cnt + EXCLUDED.win_cnt
			""";

	static final String MERGE_MATCHUP_STAGING_INTO_COUNTER = """
			INSERT INTO public.rta_agg_counter_matchup (
			    season_id, rating_id, subject_unit_id, opponent_combo_key, opponent_combo_size, win_cnt, lose_cnt
			)
			SELECT
			    s.season_id,
			    s.rating_id,
			    s.subject_unit_id,
			    s.opponent_combo_key,
			    MAX(s.opponent_combo_size)::smallint AS opponent_combo_size,
			    SUM(s.win_cnt)::bigint AS win_cnt,
			    SUM(s.lose_cnt)::bigint AS lose_cnt
			FROM public.staging_matchup_agg s
			GROUP BY s.season_id, s.rating_id, s.subject_unit_id, s.opponent_combo_key
			ON CONFLICT (season_id, rating_id, subject_unit_id, opponent_combo_key) DO UPDATE SET
			    win_cnt = public.rta_agg_counter_matchup.win_cnt + EXCLUDED.win_cnt,
			    lose_cnt = public.rta_agg_counter_matchup.lose_cnt + EXCLUDED.lose_cnt,
			    opponent_combo_size = EXCLUDED.opponent_combo_size
			""";
}
