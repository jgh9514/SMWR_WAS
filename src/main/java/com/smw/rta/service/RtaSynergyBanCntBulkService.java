package com.smw.rta.service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

import com.smw.rta.model.RtaSynergyBanDeltaRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code rta_agg_synergy_solo.ban_cnt} 증분 반영.
 * VALUES 다중행 UPDATE 대신 TEMP + {@link CopyManager} + 집계 후 {@code UPDATE … FROM}를 사용한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtaSynergyBanCntBulkService {

	private static final String COPY_SQL = """
			COPY tmp_synergy_ban_delta (season_id, rating_id, combo_unit_key, delta) FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N', ENCODING 'UTF8')
			""";

	private final DataSource dataSource;

	/**
	 * 호출부 트랜잭션(있으면)에 참여하는 커넥션으로 실행한다.
	 */
	public void applyBanCntDeltas(List<RtaSynergyBanDeltaRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		Connection conn = DataSourceUtils.getConnection(dataSource);
		try {
			try (Statement st = conn.createStatement()) {
				st.execute("""
						CREATE TEMP TABLE tmp_synergy_ban_delta (
						    season_id bigint NOT NULL,
						    rating_id integer NOT NULL,
						    combo_unit_key text NOT NULL,
						    delta bigint NOT NULL
						) ON COMMIT DROP
						""");
			}
			CopyManager copyManager = RtaPgCopySupport.unwrapPg(conn).getCopyAPI();
			InputStream tsvStream = new RtaTsvRowInputStream<>(rows, r -> RtaTsvRowInputStream.tsvLine(
					String.valueOf(r.getSeasonId()),
					String.valueOf(r.getRatingId()),
					r.getComboUnitKey() != null ? r.getComboUnitKey() : "",
					String.valueOf(r.getDelta())));
			long copied = copyManager.copyIn(COPY_SQL, tsvStream);
			try (Statement st = conn.createStatement()) {
				int updated = st.executeUpdate("""
						UPDATE public.rta_agg_synergy_solo t
						SET ban_cnt  = t.ban_cnt  + s.delta_sum,
						    pick_cnt = t.pick_cnt + s.delta_sum
						FROM (
						    SELECT season_id, rating_id, combo_unit_key, SUM(delta)::bigint AS delta_sum
						    FROM tmp_synergy_ban_delta
						    GROUP BY season_id, rating_id, combo_unit_key
						) s
						WHERE t.season_id = s.season_id
						  AND t.rating_id = s.rating_id
						  AND t.combo_unit_key = s.combo_unit_key
						""");
				log.debug("[rta-synergy-ban] COPY {}행 → UPDATE 적용 {}행", copied, updated);
			}
		} catch (SQLException | IOException e) {
			throw new IllegalStateException("ban_cnt staging UPDATE 실패: " + e.getMessage(), e);
		}
		// 호출부 @Transactional 커넥션 — 여기서 release 하면 상위 rollback 이 "JDBC rollback failed" 로 실패할 수 있음
	}
}
