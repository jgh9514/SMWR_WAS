package com.smw.rta.util;

/**
 * PostgreSQL JDBC 가 영향 행 수를 부호 없는 32비트로 넘길 때 {@link org.apache.ibatis.session.SqlSession} update 반환값이
 * 음수로 보이는 경우가 있음 — 로그·비교용으로 unsigned 로 해석.
 */
public final class PgJdbcUpdateCount {

	private PgJdbcUpdateCount() {
	}

	public static long toLong(int jdbcUpdateCount) {
		if (jdbcUpdateCount >= 0) {
			return jdbcUpdateCount;
		}
		return Integer.toUnsignedLong(jdbcUpdateCount);
	}
}
