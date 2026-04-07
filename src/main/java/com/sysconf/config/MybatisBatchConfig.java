package com.sysconf.config;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RTA 등 대량 INSERT 구간에서 {@link ExecutorType#BATCH} + JDBC 배치 재작성(예: PostgreSQL
 * {@code reWriteBatchedInserts})를 쓰기 위한 {@link SqlSessionTemplate}.
 * 일반 조회·단건 SQL은 기본 {@link SqlSessionTemplate} 매퍼를 그대로 사용한다.
 */
@Configuration
public class MybatisBatchConfig {

	public static final String BATCH_SQL_SESSION_TEMPLATE = "batchSqlSessionTemplate";

	@Bean(BATCH_SQL_SESSION_TEMPLATE)
	public SqlSessionTemplate batchSqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
		return new SqlSessionTemplate(sqlSessionFactory, ExecutorType.BATCH);
	}
}
