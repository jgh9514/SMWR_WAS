package com.smw.rta.service;

import java.sql.Connection;
import java.sql.SQLException;

import org.postgresql.PGConnection;

/**
 * Hikari·log4jdbc 등으로 감싼 {@link Connection} 에서 {@link org.postgresql.copy.CopyManager}용
 * {@link PGConnection} 을 얻는다.
 */
final class RtaPgCopySupport {

	private RtaPgCopySupport() {
	}

	static PGConnection unwrapPg(Connection conn) throws SQLException {
		SQLException last = null;
		Connection c = conn;
		for (int depth = 0; depth < 16 && c != null; depth++) {
			try {
				PGConnection pg = c.unwrap(PGConnection.class);
				if (pg != null) {
					return pg;
				}
			} catch (SQLException e) {
				last = e;
			}
			Connection inner = unwrapDelegate(c);
			if (inner == null || inner == c) {
				break;
			}
			c = inner;
		}
		throw new SQLException("PGConnection unwrap 실패 — jdbc:postgresql 직결 또는 log4jdbc 하위 커넥션 필요", last);
	}

	private static Connection unwrapDelegate(Connection c) {
		Connection inner = reflectionConnection(c, "getUnderlyingConnection");
		if (inner == null) {
			inner = reflectionConnection(c, "getTargetConnection");
		}
		if (inner == null) {
			inner = reflectionConnection(c, "getRawObject");
		}
		if (inner == null) {
			inner = reflectionConnection(c, "getRealConnection");
		}
		if (inner == null) {
			try {
				java.lang.reflect.Field f = c.getClass().getDeclaredField("realConnection");
				f.setAccessible(true);
				Object v = f.get(c);
				if (v instanceof Connection) {
					inner = (Connection) v;
				}
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return inner;
	}

	private static Connection reflectionConnection(Connection c, String method) {
		try {
			java.lang.reflect.Method m = c.getClass().getMethod(method);
			Object v = m.invoke(c);
			if (v instanceof Connection) {
				return (Connection) v;
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return null;
	}
}
