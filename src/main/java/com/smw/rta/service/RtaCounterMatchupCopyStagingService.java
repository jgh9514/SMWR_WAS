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

import com.smw.rta.model.RtaCounterMatchupUpsertRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code rta_agg_counter_matchup} 대량 갱신: UNLOGGED 스테이징에 {@link CopyManager}(COPY FROM STDIN) 후
 * 집합 기반 {@code INSERT ... SELECT ... ON CONFLICT DO UPDATE}(누적).
 * <p>
 * TRUNCATE·COPY·MERGE·TRUNCATE 는 <strong>동일 {@link Connection}</strong>에서만 수행한다 (시너지 스테이징과 동일 이유).
 * <p>
 * 호출부는 동일 {@code rtaJdbcTransactionManager} 트랜잭션에 참여해야 하며, 스테이징 TRUNCATE·MERGE·후처리 TRUNCATE 가 한 커밋에 묶인다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RtaCounterMatchupCopyStagingService {

	private static final String COPY_SQL = """
			COPY public.staging_matchup_agg (
			    season_id, rating_id, subject_unit_id, opponent_combo_key, opponent_combo_size, win_cnt, lose_cnt
			) FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N', ENCODING 'UTF8')
			""";

	private static final String TRUNCATE_STAGING = "TRUNCATE public.staging_matchup_agg";

	private final DataSource dataSource;

	/**
	 * 스테이징 비우기 → COPY(한 스트림) → 스테이징 집계 후 본 테이블 UPSERT → 스테이징 비우기.
	 */
	@Transactional(transactionManager = "rtaJdbcTransactionManager", propagation = Propagation.MANDATORY,
			rollbackFor = Exception.class)
	public void flushCounterMatchupViaCopyStaging(List<RtaCounterMatchupUpsertRow> rows) {
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
			log.info("[rta-counter-staging] COPY 완료 rows={}, rta_agg_counter_matchup 로 merge 시작 (수십 초~수분 걸릴 수 있음)", copied);
			long tMerge = System.currentTimeMillis();
			long merged = RtaAggStagingJdbc.executeInsertMergeReturningRows(conn,
					RtaAggStagingMergeSql.MERGE_MATCHUP_STAGING_INTO_COUNTER);
			log.info("[rta-counter-staging] merge 완료 affected={}, {} ms", merged, System.currentTimeMillis() - tMerge);
			try (Statement st = conn.createStatement()) {
				st.execute(TRUNCATE_STAGING);
			}
		} catch (SQLException | IOException e) {
			throw new IllegalStateException("COPY/merge staging_matchup_agg 실패: " + e.getMessage(), e);
		} finally {
			DataSourceUtils.releaseConnection(conn, dataSource);
		}
	}

	/** TAB 구분, UTF-8. opponent_combo_key 는 현재 숫자·콤마만 사용한다고 가정(탭·개행 없음). */
	private static byte[] buildTsvChunkUtf8(List<RtaCounterMatchupUpsertRow> rows, int from, int to) {
		StringBuilder sb = new StringBuilder((to - from) * 48);
		for (int i = from; i < to; i++) {
			RtaCounterMatchupUpsertRow r = rows.get(i);
			appendNoTab(sb, r.getSeasonId());
			sb.append('\t');
			appendNoTab(sb, r.getRatingId());
			sb.append('\t');
			appendNoTab(sb, r.getSubjectUnitId());
			sb.append('\t');
			String key = r.getOpponentComboKey();
			if (key == null || key.indexOf('\t') >= 0 || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
				throw new IllegalArgumentException("opponent_combo_key 에 TAB/개행이 있으면 COPY TSV 불가: " + key);
			}
			sb.append(key);
			sb.append('\t');
			appendNoTab(sb, r.getOpponentComboSize());
			sb.append('\t');
			appendNoTab(sb, r.getWinDelta());
			sb.append('\t');
			appendNoTab(sb, r.getLoseDelta());
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
