package com.smw.rta.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

import com.smw.rta.config.RtaStagingMergeProperties;

/**
 * COPY 완료 후 {@code INSERT … ON CONFLICT} merge 직전에 스테이징·세션을 준비한다.
 */
final class RtaAggStagingMergeTune {

	private static final int WORK_MEM_MB_MAX = 2048;
	private static final int PARALLEL_WORKERS_MAX = 32;

	/** {@link #prepareAfterCopyBeforeMerge} 에 허용되는 스테이징 테이블 (SQL 주입 방지). */
	private static final Set<String> ALLOWED_STAGING_TABLES = Set.of(
			"public.staging_matchup_agg",
			"public.staging_synergy_agg");

	private RtaAggStagingMergeTune() {
	}

	/**
	 * Hikari 등에서 짧은 {@code lock_timeout} 이 걸려 있으면, 대용량 MERGE 가 다른 세션 락을
	 * 잠깐 잡는 동안 "canceling statement due to lock timeout" 이 난다. merge 트랜잭션 안에서
	 * {@code SET LOCAL} 으로만 덮어쓴다(커밋 후 풀 반환 시 소멸).
	 */
	static void applyLockTimeoutForMerge(Statement st, RtaStagingMergeProperties props) throws SQLException {
		if (props == null) {
			return;
		}
		int ms = props.getLockTimeoutMs();
		if (ms < 0) {
			return;
		}
		st.execute("SET LOCAL lock_timeout = " + ms);
	}

	/**
	 * @param stagingQualifiedTable {@code public.staging_matchup_agg} 또는 {@code public.staging_synergy_agg}
	 */
	static void prepareAfterCopyBeforeMerge(Connection conn, RtaStagingMergeProperties props,
			String stagingQualifiedTable) throws SQLException {
		if (props == null) {
			return;
		}
		if (!ALLOWED_STAGING_TABLES.contains(stagingQualifiedTable)) {
			throw new IllegalArgumentException("허용되지 않은 staging 테이블: " + stagingQualifiedTable);
		}
		try (Statement st = conn.createStatement()) {
			if (props.isAnalyzeStagingBeforeMerge()) {
				st.execute("ANALYZE " + stagingQualifiedTable);
			}
			if (props.isJitOffForMerge()) {
				st.execute("SET LOCAL jit = off");
			}
			int wm = props.getWorkMemMb();
			if (wm > 0) {
				int mb = Math.min(wm, WORK_MEM_MB_MAX);
				st.execute("SET LOCAL work_mem = '" + mb + "MB'");
			}
			int para = props.getMaxParallelWorkersPerGather();
			if (para > 0) {
				int p = Math.min(para, PARALLEL_WORKERS_MAX);
				st.execute("SET LOCAL max_parallel_workers_per_gather = " + p);
			}
		}
	}
}
