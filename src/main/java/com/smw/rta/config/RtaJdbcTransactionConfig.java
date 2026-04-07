package com.smw.rta.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * RTA MyBatis 집계용 JDBC 트랜잭션.
 * <p>
 * {@code RtaMapper} 등만 쓰는 경계에서 {@link org.springframework.orm.jpa.JpaTransactionManager}를 쓰면
 * JPA {@code EntityManager}를 열어야 하고, Quartz 워커 등에서
 * "Could not open JPA EntityManager for transaction" 이 날 수 있다. 동일 {@link DataSource}에 대한
 * JDBC 전용 매니저로 커밋 경계를 분리한다.
 */
@Configuration
public class RtaJdbcTransactionConfig {

	@Bean(name = "rtaJdbcTransactionManager")
	public PlatformTransactionManager rtaJdbcTransactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}
}
