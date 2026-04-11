package com.smw.rta.mapper.type;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * PostgreSQL {@code bigint[]} 바인딩 — {@code WHERE col = ANY(#{rids})} 등 다건 rid 를 한 파라미터로 전달할 때 사용.
 * (VALUES/IN 다건 플레이스홀더 한도·다회 왕복 회피)
 */
public class PgBigintArrayTypeHandler extends BaseTypeHandler<long[]> {

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, long[] parameter, JdbcType jdbcType) throws SQLException {
		Connection c = ps.getConnection();
		Long[] boxed = new Long[parameter.length];
		for (int j = 0; j < parameter.length; j++) {
			boxed[j] = parameter[j];
		}
		ps.setArray(i, c.createArrayOf("bigint", boxed));
	}

	@Override
	public long[] getNullableResult(ResultSet rs, String columnName) {
		return null;
	}

	@Override
	public long[] getNullableResult(ResultSet rs, int columnIndex) {
		return null;
	}

	@Override
	public long[] getNullableResult(CallableStatement cs, int columnIndex) {
		return null;
	}
}
