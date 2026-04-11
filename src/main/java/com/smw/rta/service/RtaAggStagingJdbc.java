package com.smw.rta.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.smw.rta.util.PgJdbcUpdateCount;

/**
 * 스테이징 merge(INSERT…SELECT) 를 동일 {@link Connection}에서 실행.
 */
final class RtaAggStagingJdbc {

	private RtaAggStagingJdbc() {
	}

	static long executeInsertMergeReturningRows(Connection conn, String sql) throws SQLException {
		try (Statement st = conn.createStatement()) {
			try {
				return st.executeLargeUpdate(sql);
			} catch (AbstractMethodError | UnsupportedOperationException e) {
				// log4jdbc 등 Statement 래퍼가 executeLargeUpdate 미구현
				return PgJdbcUpdateCount.toLong(st.executeUpdate(sql));
			}
		}
	}
}
