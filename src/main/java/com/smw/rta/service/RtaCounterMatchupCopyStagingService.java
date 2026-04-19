package com.smw.rta.service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import javax.sql.DataSource;

import org.postgresql.copy.CopyManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smw.rta.config.RtaStagingMergeProperties;
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

	private static final String STAGING_QUALIFIED = "public.staging_matchup_agg";

	private final DataSource dataSource;
	private final RtaStagingMergeProperties stagingMergeProperties;

	/** merge 직후 {@code ANALYZE public.rta_agg_counter_matchup} — 대량 적재 후 통계 갱신(플랜 품질). */
	@Value("${smw.rta.counter-agg.analyze-target-after-merge:true}")
	private boolean analyzeTargetAfterMerge;

	/**
	 * 스테이징 비우기 → COPY(한 스트림) → 스테이징 집계 후 본 테이블 UPSERT → 스테이징 비우기.
	 */
	@Transactional(transactionManager = "rtaJdbcTransactionManager", propagation = Propagation.REQUIRES_NEW,
			rollbackFor = Exception.class)
	public void flushCounterMatchupViaCopyStaging(List<RtaCounterMatchupUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		Connection conn = DataSourceUtils.getConnection(dataSource);
		try {
			try (Statement st = conn.createStatement()) {
				// 집계 통계는 재계산 가능 → WAL fsync 대기 생략으로 커밋 속도 향상
				st.execute("SET LOCAL synchronous_commit = off");
				st.execute(TRUNCATE_STAGING);
			}
			CopyManager copyManager = RtaPgCopySupport.unwrapPg(conn).getCopyAPI();
			InputStream tsvStream = new RtaTsvRowInputStream<>(rows, r -> {
				String key = r.getOpponentComboKey();
				if (key == null || key.indexOf('\t') >= 0 || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
					throw new IllegalArgumentException("opponent_combo_key 에 TAB/개행이 있으면 COPY TSV 불가: " + key);
				}
				return RtaTsvRowInputStream.tsvLine(
						String.valueOf(r.getSeasonId()),
						String.valueOf(r.getRatingId()),
						String.valueOf(r.getSubjectUnitId()),
						key,
						String.valueOf(r.getOpponentComboSize()),
						String.valueOf(r.getWinDelta()),
						String.valueOf(r.getLoseDelta()));
			});
			long copied = copyManager.copyIn(COPY_SQL, tsvStream);
			RtaAggStagingMergeTune.prepareAfterCopyBeforeMerge(conn, stagingMergeProperties, STAGING_QUALIFIED);
			log.info("[rta-counter-staging] COPY 완료 rows={}, rta_agg_counter_matchup 로 merge 시작 (수십 초~수분 걸릴 수 있음)", copied);
			long tMerge = System.currentTimeMillis();
			long merged = RtaAggStagingJdbc.executeInsertMergeReturningRows(conn,
					RtaAggStagingMergeSql.MERGE_MATCHUP_STAGING_INTO_COUNTER);
			log.info("[rta-counter-staging] merge 완료 affected={}, {} ms", merged, System.currentTimeMillis() - tMerge);
			if (analyzeTargetAfterMerge) {
				try (Statement st = conn.createStatement()) {
					st.execute("ANALYZE public.rta_agg_counter_matchup");
				}
			}
			try (Statement st = conn.createStatement()) {
				st.execute(TRUNCATE_STAGING);
			}
		} catch (SQLException | IOException e) {
			// merge 실패 시에도 staging 정리 시도 — 새 커넥션으로 별도 실행(현 커넥션은 이미 깨진 상태일 수 있음)
			tryTruncateStagingOnNewConn();
			throw new IllegalStateException("COPY/merge staging_matchup_agg 실패: " + e.getMessage(), e);
		} finally {
			DataSourceUtils.releaseConnection(conn, dataSource);
		}
	}

	/** merge 실패 시 스테이징 테이블을 새 커넥션으로 정리 — 기존 커넥션이 이미 끊긴 상태일 수 있음 */
	private void tryTruncateStagingOnNewConn() {
		try {
			Connection c = dataSource.getConnection();
			try (Statement st = c.createStatement()) {
				st.execute(TRUNCATE_STAGING);
				c.commit();
			} catch (SQLException ex) {
				log.warn("[rta-counter-staging] 실패 후 staging 정리 오류: {}", ex.getMessage());
			} finally {
				try { c.close(); } catch (SQLException ignore) {}
			}
		} catch (SQLException ex) {
			log.warn("[rta-counter-staging] 실패 후 staging 정리용 커넥션 획득 실패: {}", ex.getMessage());
		}
	}

}
