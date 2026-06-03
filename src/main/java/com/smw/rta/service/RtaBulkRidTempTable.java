package com.smw.rta.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import org.postgresql.copy.CopyManager;

/**
 * 대량 {@code replay_id} 집합을 {@code tmp_bulk_rids} 임시 테이블에 COPY 로 적재한다.
 * {@link RtaBulkRidLookupService}·시너지 prefetch·RTA 업로드 raw 마킹 등에서 공유한다.
 */
public final class RtaBulkRidTempTable {

	private RtaBulkRidTempTable() {
	}

	public static void loadRids(Connection conn, Collection<Long> rids) throws SQLException, IOException {
		loadRids(conn, rids, 5000);
	}

	/**
	 * @param tempIndexThreshold 이 건수 초과 시에만 {@code tmp_bulk_rids(rid)} 인덱스·ANALYZE 생성
	 */
	public static void loadRids(Connection conn, Collection<Long> rids, int tempIndexThreshold)
			throws SQLException, IOException {
		try (Statement st = conn.createStatement()) {
			st.execute("DROP TABLE IF EXISTS tmp_bulk_rids");
			st.execute("CREATE TEMP TABLE tmp_bulk_rids (rid bigint) ON COMMIT DROP");
		}
		CopyManager cm = RtaPgCopySupport.unwrapPg(conn).getCopyAPI();
		StringBuilder sb = new StringBuilder(Math.max(64, rids.size() * 12));
		for (Long rid : rids) {
			sb.append(rid).append('\n');
		}
		byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
		cm.copyIn("COPY tmp_bulk_rids (rid) FROM STDIN WITH (FORMAT text)", new ByteArrayInputStream(bytes));
		if (rids.size() > tempIndexThreshold) {
			try (Statement st = conn.createStatement()) {
				st.execute("CREATE INDEX ON tmp_bulk_rids (rid)");
				st.execute("ANALYZE tmp_bulk_rids");
			}
		}
	}

	/**
	 * {@code tmp_bulk_rids} 로 JOIN 해 시너지 적재 완료 표시.
	 * 호출부 트랜잭션 커넥션에서 실행할 것({@code DataSourceUtils.getConnection}).
	 */
	public static long markRtaMatchSynergyAppliedSuccess(Connection conn, Collection<Long> rids) throws SQLException, IOException {
		loadRids(conn, rids);
		try (Statement st = conn.createStatement()) {
			return RtaAggStagingJdbc.executeLargeUpdateCompatible(st, """
					UPDATE public.rta_match m
					SET synergy_applied_at = CURRENT_TIMESTAMP,
					    synergy_apply_result = 'S'
					FROM tmp_bulk_rids t
					WHERE m.replay_id = t.rid
					""");
		}
	}

	/**
	 * {@code ranker_rtpvp_replay_raw} 정규화 반영 완료 — 호출부 트랜잭션 {@link Connection}과 동일 세션에서 실행할 것.
	 */
	public static int updateRankerRtpvpReplayRawApplied(Connection conn, Collection<Long> rids) throws SQLException, IOException {
		if (rids == null || rids.isEmpty()) {
			return 0;
		}
		loadRids(conn, rids);
		try (Statement st = conn.createStatement()) {
			return (int) RtaAggStagingJdbc.executeLargeUpdateCompatible(st, """
					UPDATE ranker_rtpvp_replay_raw r SET
						  apply_status = 'applied'
						, applied_at = CURRENT_TIMESTAMP
						, last_error = NULL
					FROM tmp_bulk_rids t
					WHERE r.rid = t.rid
					""");
		}
	}

	/**
	 * {@code ranker_rtpvp_replay_raw} 정규화 실패 표기 — 동일 세션 트랜잭션 커넥션용.
	 */
	public static int updateRankerRtpvpReplayRawFailed(Connection conn, Collection<Long> rids, String message)
			throws SQLException, IOException {
		if (rids == null || rids.isEmpty()) {
			return 0;
		}
		loadRids(conn, rids);
		String sql = """
				UPDATE ranker_rtpvp_replay_raw r SET
					  apply_status = 'failed'
					, last_error = SUBSTRING(COALESCE(?, '') FROM 1 FOR 4000)
					, retry_count = retry_count + 1
				FROM tmp_bulk_rids t
				WHERE r.rid = t.rid
				""";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, message != null ? message : "");
			return ps.executeUpdate();
		}
	}

	/**
	 * {@code rta_match} 부모 없이 남은 unit / participant 행 삭제 — 동일 세션 또는 독립 커넥션.
	 */
	public static int deleteArenaOrphanChildrenByRids(Connection conn, Collection<Long> rids) throws SQLException, IOException {
		if (rids == null || rids.isEmpty()) {
			return 0;
		}
		loadRids(conn, rids);
		int total = 0;
		try (Statement st = conn.createStatement()) {
			total += (int) RtaAggStagingJdbc.executeLargeUpdateCompatible(st, """
					DELETE FROM public.rta_match_unit_pick u
					USING tmp_bulk_rids t
					WHERE u.replay_id = t.rid
					  AND NOT EXISTS (SELECT 1 FROM public.rta_match r WHERE r.replay_id = u.replay_id)
					""");
			total += (int) RtaAggStagingJdbc.executeLargeUpdateCompatible(st, """
					DELETE FROM public.rta_match_participant ul
					USING tmp_bulk_rids t
					WHERE ul.replay_id = t.rid
					  AND NOT EXISTS (SELECT 1 FROM public.rta_match r WHERE r.replay_id = ul.replay_id)
					""");
		}
		return total;
	}
}
