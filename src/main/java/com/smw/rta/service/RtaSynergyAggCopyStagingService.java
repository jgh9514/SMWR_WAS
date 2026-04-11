package com.smw.rta.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smw.rta.model.RtaSynergyAggUpsertRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code rta_agg_synergy_combo} 대량 갱신: UNLOGGED 스테이징 + {@link CopyManager} + 집합 {@code ON CONFLICT} 누적.
 * <p>
 * TRUNCATE·COPY·MERGE·TRUNCATE 는 <strong>동일 {@link Connection}</strong>에서만 수행한다.
 * (COPY 로 적재한 스테이징을 MyBatis 가 다른 커넥션에서 읽으면 세션 불일치로 merge 가 0행이 될 수 있음 — log4jdbc 등.)
 * <p>
 * {@code staging_synergy_agg} 는 DB 전역 1개이므로, 동일 시점에 시너지 집계 Job 을 병렬로 돌리면 TRUNCATE/COPY 가 서로 간섭한다.
 * 통합 배치는 {@link org.quartz.DisallowConcurrentExecution} 으로 겹침을 막는 것이 안전하다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtaSynergyAggCopyStagingService {

	private static final String COPY_SQL = """
			COPY public.staging_synergy_agg (
			    season_id, rating_id, combo_unit_key, combo_size, match_cnt, win_cnt
			) FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N', ENCODING 'UTF8')
			""";

	private static final String TRUNCATE_STAGING = "TRUNCATE public.staging_synergy_agg";

	private final DataSource dataSource;

	@Transactional(transactionManager = "rtaJdbcTransactionManager", propagation = Propagation.MANDATORY,
			rollbackFor = Exception.class)
	public void flushSynergyAggViaCopyStaging(List<RtaSynergyAggUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		byte[] bytes = buildTsvChunkUtf8(rows, 0, rows.size());

		Connection conn = DataSourceUtils.getConnection(dataSource);
		try {
			try (Statement st = conn.createStatement()) {
				st.execute(TRUNCATE_STAGING);
			}
			PGConnection pg = conn.unwrap(PGConnection.class);
			CopyManager copyManager = pg.getCopyAPI();
			long copied = copyManager.copyIn(COPY_SQL, new ByteArrayInputStream(bytes));
			log.info("[rta-synergy-staging] COPY 완료 rows={}, rta_agg_synergy_combo 로 merge 시작", copied);
			long tMerge = System.currentTimeMillis();
			long merged = RtaAggStagingJdbc.executeInsertMergeReturningRows(conn,
					RtaAggStagingMergeSql.MERGE_SYNERGY_STAGING_INTO_COMBO);
			log.info("[rta-synergy-staging] merge 완료 affected={}, {} ms", merged, System.currentTimeMillis() - tMerge);
			try (Statement st = conn.createStatement()) {
				st.execute(TRUNCATE_STAGING);
			}
		} catch (SQLException | IOException e) {
			throw new IllegalStateException("COPY/merge staging_synergy_agg 실패: " + e.getMessage(), e);
		} finally {
			DataSourceUtils.releaseConnection(conn, dataSource);
		}
	}

	private static byte[] buildTsvChunkUtf8(List<RtaSynergyAggUpsertRow> rows, int from, int to) {
		StringBuilder sb = new StringBuilder((to - from) * 40);
		for (int i = from; i < to; i++) {
			RtaSynergyAggUpsertRow r = rows.get(i);
			appendNoTab(sb, r.getSeasonId());
			sb.append('\t');
			appendNoTab(sb, r.getRatingId());
			sb.append('\t');
			String key = r.getComboKey();
			if (key == null || key.indexOf('\t') >= 0 || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
				throw new IllegalArgumentException("combo_unit_key 에 TAB/개행이 있으면 COPY TSV 불가: " + key);
			}
			sb.append(key);
			sb.append('\t');
			appendNoTab(sb, r.getComboSize());
			sb.append('\t');
			appendNoTab(sb, r.getMatchDelta());
			sb.append('\t');
			appendNoTab(sb, r.getWinDelta());
			sb.append('\n');
		}
		return sb.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static void appendNoTab(StringBuilder sb, long v) {
		sb.append(v);
	}

	private static void appendNoTab(StringBuilder sb, int v) {
		sb.append(v);
	}
}
