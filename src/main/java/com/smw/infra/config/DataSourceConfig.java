package com.smw.infra.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 배포 환경별로 JDBC URL / driver-class-name 조합이 어긋나도
 * PostgreSQL / log4jdbc 조합을 안전하게 보정한다.
 */
@Slf4j
@Configuration
public class DataSourceConfig {

	private static final String POSTGRESQL_URL_PREFIX = "jdbc:postgresql:";
	private static final String LOG4JDBC_POSTGRESQL_URL_PREFIX = "jdbc:log4jdbc:postgresql:";
	private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";
	private static final String LOG4JDBC_DRIVER = "net.sf.log4jdbc.sql.jdbcapi.DriverSpy";

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource.hikari")
	public HikariDataSource dataSource(DataSourceProperties properties) {
		HikariDataSource dataSource = properties.initializeDataSourceBuilder()
				.type(HikariDataSource.class)
				.build();

		ResolvedJdbcSettings resolved = resolve(properties.getUrl(), properties.getDriverClassName());
		if (StringUtils.hasText(resolved.jdbcUrl())) {
			dataSource.setJdbcUrl(resolved.jdbcUrl());
		}
		if (StringUtils.hasText(resolved.driverClassName())) {
			dataSource.setDriverClassName(resolved.driverClassName());
		}

		if (!equalsNullable(properties.getUrl(), resolved.jdbcUrl())
				|| !equalsNullable(properties.getDriverClassName(), resolved.driverClassName())) {
			log.warn("[datasource] normalized jdbc settings. url={} -> {}, driver={} -> {}",
					properties.getUrl(), resolved.jdbcUrl(),
					properties.getDriverClassName(), resolved.driverClassName());
		}
		return dataSource;
	}

	private static ResolvedJdbcSettings resolve(String url, String driverClassName) {
		if (!StringUtils.hasText(url)) {
			return new ResolvedJdbcSettings(url, driverClassName);
		}
		if (url.startsWith(LOG4JDBC_POSTGRESQL_URL_PREFIX)) {
			return new ResolvedJdbcSettings(url, LOG4JDBC_DRIVER);
		}
		if (url.startsWith(POSTGRESQL_URL_PREFIX)) {
			if (LOG4JDBC_DRIVER.equals(driverClassName)) {
				return new ResolvedJdbcSettings(toLog4jdbcUrl(url), LOG4JDBC_DRIVER);
			}
			return new ResolvedJdbcSettings(url, POSTGRESQL_DRIVER);
		}
		return new ResolvedJdbcSettings(url, driverClassName);
	}

	private static String toLog4jdbcUrl(String url) {
		return "jdbc:log4jdbc:" + url.substring("jdbc:".length());
	}

	private static boolean equalsNullable(String left, String right) {
		if (left == null) {
			return right == null;
		}
		return left.equals(right);
	}

	private record ResolvedJdbcSettings(String jdbcUrl, String driverClassName) {
	}
}
