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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smw.rta.config.RtaStagingMergeProperties;
import com.smw.rta.model.RtaSynergyAggUpsertRow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@code rta_agg_synergy_solo/duo/trio} 대량 갱신: UNLOGGED 스테이징 + {@link CopyManager} + 집합 {@code ON CONFLICT} 누적.
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
			    season_id, rating_id, combo_unit_key, combo_size, match_cnt, win_cnt, ban_cnt
			) FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N', ENCODING 'UTF8')
			""";

	private static final String TRUNCATE_STAGING = "TRUNCATE public.staging_synergy_agg";

	private static final String STAGING_QUALIFIED = "public.staging_synergy_agg";

	private final DataSource dataSource;
	private final RtaStagingMergeProperties stagingMergeProperties;

	@Transactional(transactionManager = "rtaJdbcTransactionManager", propagation = Propagation.REQUIRES_NEW,
			rollbackFor = Exception.class)
	public void flushSynergyAggViaCopyStaging(List<RtaSynergyAggUpsertRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return;
		}
		Connection conn = DataSourceUtils.getConnection(dataSource);
		try {
			try (Statement st = conn.createStatement()) {
				// 집계 통계는 재계산 가능 → WAL fsync 대기 생략으로 커밋 속도 향상
				st.execute("SET LOCAL synchronous_commit = off");
				RtaAggStagingMergeTune.applyLockTimeoutForMerge(st, stagingMergeProperties);
				st.execute(TRUNCATE_STAGING);
			}
			CopyManager copyManager = RtaPgCopySupport.unwrapPg(conn).getCopyAPI();
			// 스트리밍: 전체 byte[] 대신 행 단위 생성 → 힙 사용 O(1)
			InputStream tsvStream = new RtaTsvRowInputStream<>(rows, r -> {
				String key = r.getComboKey();
				if (key == null || key.indexOf('\t') >= 0 || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
					throw new IllegalArgumentException("combo_unit_key 에 TAB/개행이 있으면 COPY TSV 불가: " + key);
				}
				return RtaTsvRowInputStream.tsvLine(
						String.valueOf(r.getSeasonId()),
						String.valueOf(r.getRatingId()),
						key,
						String.valueOf(r.getComboSize()),
						String.valueOf(r.getMatchDelta()),
						String.valueOf(r.getWinDelta()),
						"0");
			});
			long copied = copyManager.copyIn(COPY_SQL, tsvStream);
			RtaAggStagingMergeTune.prepareAfterCopyBeforeMerge(conn, stagingMergeProperties, STAGING_QUALIFIED);
			log.info("[rta-synergy-staging] COPY 완료 rows={}, 분리 시너지 테이블로 merge 시작", copied);
			long tMerge = System.currentTimeMillis();
			long merged = 0L;
			merged += RtaAggStagingJdbc.executeInsertMergeReturningRows(conn,
					RtaAggStagingMergeSql.MERGE_SYNERGY_STAGING_INTO_SOLO);
			merged += RtaAggStagingJdbc.executeInsertMergeReturningRows(conn,
					RtaAggStagingMergeSql.MERGE_SYNERGY_STAGING_INTO_DUO);
			merged += RtaAggStagingJdbc.executeInsertMergeReturningRows(conn,
					RtaAggStagingMergeSql.MERGE_SYNERGY_STAGING_INTO_TRIO);
			log.info("[rta-synergy-staging] merge 완료 affected={}, {} ms", merged, System.currentTimeMillis() - tMerge);
			try (Statement st = conn.createStatement()) {
				st.execute(TRUNCATE_STAGING);
			}
		} catch (SQLException | IOException e) {
			tryTruncateStagingOnNewConn();
			throw new IllegalStateException("COPY/merge staging_synergy_agg 실패: " + e.getMessage(), e);
		} finally {
			DataSourceUtils.releaseConnection(conn, dataSource);
		}
	}

	private void tryTruncateStagingOnNewConn() {
		try {
			Connection c = dataSource.getConnection();
			try (Statement st = c.createStatement()) {
				st.execute(TRUNCATE_STAGING);
				c.commit();
			} catch (SQLException ex) {
				log.warn("[rta-synergy-staging] 실패 후 staging 정리 오류: {}", ex.getMessage());
			} finally {
				try { c.close(); } catch (SQLException ignore) {}
			}
		} catch (SQLException ex) {
			log.warn("[rta-synergy-staging] 실패 후 staging 정리용 커넥션 획득 실패: {}", ex.getMessage());
		}
	}

}
