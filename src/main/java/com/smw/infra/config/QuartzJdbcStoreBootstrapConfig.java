package com.smw.infra.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.quartz.QuartzDataSource;
import org.springframework.boot.autoconfigure.quartz.QuartzTransactionManager;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code JdbcStoreTypeConfiguration} 은 {@code @ConditionalOnSingleCandidate(DataSource.class)} 가 true 일 때만
 * {@link SchedulerFactoryBean} 에 DataSource 를 붙인다. DataSource 가 2개 이상이면 조건이 false 가 되어
 * RAMJobStore + {@code org.quartz.jobStore.*}(JDBC) properties 충돌로 기동이 실패할 수 있다.
 * <p>
 * {@code quartz-jdbc} 프로필(배치 Pod)에서만 Primary(또는 {@link QuartzDataSource}) 와
 * {@link QuartzTransactionManager} 기준으로 동일한 주입을 강제한다.
 */
@Configuration
@Profile("quartz-jdbc")
public class QuartzJdbcStoreBootstrapConfig {

	@Bean
	@Order(0)
	public SchedulerFactoryBeanCustomizer quartzJdbcDataSourceAndTransactionManagerCustomizer(
			DataSource dataSource,
			@QuartzDataSource ObjectProvider<DataSource> quartzDataSource,
			@QuartzTransactionManager ObjectProvider<PlatformTransactionManager> quartzTransactionManager,
			ObjectProvider<PlatformTransactionManager> transactionManager) {
		return (schedulerFactoryBean) -> {
			DataSource toUse = quartzDataSource.getIfAvailable();
			if (toUse == null) {
				toUse = dataSource;
			}
			schedulerFactoryBean.setDataSource(toUse);
			PlatformTransactionManager tx = quartzTransactionManager.getIfAvailable();
			if (tx == null) {
				tx = transactionManager.getIfUnique();
			}
			if (tx != null) {
				schedulerFactoryBean.setTransactionManager(tx);
			}
		};
	}
}
