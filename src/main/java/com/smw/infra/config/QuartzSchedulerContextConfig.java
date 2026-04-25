package com.smw.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

/**
 * JDBC JobStore 는 {@link org.quartz.JobDataMap} 을 BLOB/직렬화로 보관하므로
 * {@code ApplicationContext} 를 JobDataMap 에 넣을 수 없다.
 * <p>
 * {@link com.smw.monster.batch.BaseBatchJob} 은
 * {@code getScheduler().getContext().get("applicationContext")} 로 컨텍스트를 쓴다.
 * {@link SchedulerFactoryBean} 의 Javadoc(SchedulerContext)과 동일한 키를 사용한다.
 */
@Configuration
@ConditionalOnClass(SchedulerFactoryBean.class)
public class QuartzSchedulerContextConfig {

	@Bean
	public SchedulerFactoryBeanCustomizer quartzApplicationContextInSchedulerContext() {
		return schedulerFactoryBean -> schedulerFactoryBean
				.setApplicationContextSchedulerContextKey("applicationContext");
	}
}
