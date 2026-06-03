package com.smw.infra.config;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * smw-app 등 API Pod: JDBC Quartz 클러스터에 수동 트리거만 발행하고 로컬에서는 Job을 실행하지 않는다.
 * {@code spring.quartz.auto-startup=false} 와 함께 쓰며, 기동 후 start → standby 로 클러스터 참여만 한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "smw.batch.quartz.trigger-only", havingValue = "true")
public class QuartzClusterTriggerOnlyConfig {

	@EventListener(ApplicationReadyEvent.class)
	public void enterStandbyAfterReady(ApplicationReadyEvent event) throws SchedulerException {
		Scheduler scheduler = event.getApplicationContext().getBean(Scheduler.class);
		if (!scheduler.isStarted()) {
			scheduler.start();
			log.info("[quartz-trigger-only] Scheduler started for JDBC cluster trigger publishing");
		}
		if (!scheduler.isInStandbyMode()) {
			scheduler.standby();
			log.info("[quartz-trigger-only] Scheduler in standby — Job execution delegated to batch worker Pod(s)");
		}
	}
}
