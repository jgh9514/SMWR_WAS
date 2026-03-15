package com.sysconf.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;

import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.KeyMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import com.admin.batch.mapper.BatchMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.batch.job", name = "enabled", havingValue = "true", matchIfMissing = false)
public class BatchConfig {

	@Autowired
	private Scheduler scheduler;

	@Autowired
	private BatchMapper mapper;

	@Autowired
	private ApplicationContext applicationContext;

	@Value("${spring.profiles.active:}")
	String profilesValue;

	@Value("${spring.batch.scheduler.enabled:true}")
	boolean schedulerEnabled;

	@SuppressWarnings("unchecked")
	@PostConstruct
	public void start() {
		log.info("===== BatchConfig start invoked (scheduler.enabled={}) =====", schedulerEnabled);
		if (!schedulerEnabled) {
			log.info("배치 스케줄러 자동 시작 비활성화 - 수동 실행만 가능");
			return;
		}
		try {
			List<Map<String, String>> scheduleList = loadScheduleList(null);
			for (Map<String, String> scheduleMap : scheduleList) {
				String jobKey = String.valueOf(scheduleMap.get("bat_id"));
				String jobClassName = scheduleMap.get("job_class");
				String cronExp = scheduleMap.get("cron_expr");
				String callYn = scheduleMap.get("use_yn");
				log.info("jobKey : {} / jobClassName : {} / cronExp : {} / callYn : {}", jobKey, jobClassName, cronExp, callYn);
				if ("Y".equalsIgnoreCase(callYn)) {
					try {
						Class<?> classObject = Class.forName(jobClassName);
						Map<String, Object> jobData = new HashMap<>();
						jobData.put("applicationContext", applicationContext);
						scheduler.scheduleJob(
								buildJobDetail((Class<? extends Job>) classObject, jobKey, jobKey, jobData),
								buildCronJobTrigger(cronExp));
					} catch (ClassNotFoundException e) {
						log.error("Not found class: {}", jobClassName);
					}
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage());
		}
	}

	public void clear() {
		try {
			scheduler.clear();
			log.info("BatchConfig cleared");
		} catch (Exception e) {
			log.error("BatchConfig clear failed", e);
		}
	}

	private static final String RUN_ONCE_GROUP = "runOnce";
	private static final long RUN_ONCE_TIMEOUT_MINUTES = 30;

	/**
	 * 배치를 동기 실행합니다. 배치가 완료될 때까지 대기한 후 결과를 반환합니다.
	 */
	public RunOnceResult runOnce(String jobKey, Map<String, Object> jobData) {
		try {
			List<Map<String, String>> list = loadScheduleList(jobKey);
			Map<String, String> config = list.stream()
					.filter(m -> jobKey.equals(String.valueOf(m.get("bat_id"))))
					.findFirst().orElse(null);
			if (config == null) {
				log.error("Batch config not found: {}", jobKey);
				return RunOnceResult.fail("배치 설정을 찾을 수 없습니다. (bat_id=" + jobKey + ")");
			}
			Class<?> classObject = Class.forName(config.get("job_class"));
			Map<String, Object> data = jobData != null ? new HashMap<>(jobData) : new HashMap<>();
			data.put("applicationContext", applicationContext);

			JobKey qJobKey = JobKey.jobKey(jobKey, RUN_ONCE_GROUP);
			CountDownLatch latch = new CountDownLatch(1);
			AtomicReference<JobExecutionException> jobError = new AtomicReference<>();

			JobListener waiter = new JobListener() {
				@Override
				public String getName() {
					return "runOnceWaiter-" + jobKey;
				}
				@Override
				public void jobToBeExecuted(JobExecutionContext context) {}
				@Override
				public void jobExecutionVetoed(JobExecutionContext context) {
					latch.countDown();
				}
				@Override
				public void jobWasExecuted(JobExecutionContext context, JobExecutionException e) {
					if (e != null) jobError.set(e);
					latch.countDown();
				}
			};

			scheduler.getListenerManager().addJobListener(waiter, KeyMatcher.keyEquals(qJobKey));

			try {
				Trigger trigger = TriggerBuilder.newTrigger()
						.withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0))
						.build();
				scheduler.scheduleJob(
						buildJobDetail((Class<? extends Job>) classObject, jobKey, RUN_ONCE_GROUP, data),
						trigger);

				boolean completed = latch.await(RUN_ONCE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
				if (!completed) {
					return RunOnceResult.fail("배치 실행 시간 초과 (" + RUN_ONCE_TIMEOUT_MINUTES + "분)");
				}
				JobExecutionException ex = jobError.get();
				if (ex != null) {
					String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
					return RunOnceResult.fail("배치 실행 실패: " + msg);
				}
				return RunOnceResult.success();
			} finally {
				scheduler.getListenerManager().removeJobListener(waiter.getName());
			}
		} catch (ClassNotFoundException e) {
			log.error("runOnce failed: job class not found, jobKey={}", jobKey, e);
			return RunOnceResult.fail("배치 클래스를 찾을 수 없습니다: " + e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("runOnce interrupted: jobKey={}", jobKey, e);
			return RunOnceResult.fail("배치 실행 대기 중 중단됨");
		} catch (Exception e) {
			log.error("runOnce failed: jobKey={}", jobKey, e);
			return RunOnceResult.fail("배치 실행 요청 실패: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
		}
	}

	public static class RunOnceResult {
		public final boolean success;
		public final String message;

		private RunOnceResult(boolean success, String message) {
			this.success = success;
			this.message = message;
		}

		public static RunOnceResult success() {
			return new RunOnceResult(true, null);
		}

		public static RunOnceResult fail(String message) {
			return new RunOnceResult(false, message);
		}
	}

	private JobDetail buildJobDetail(Class<? extends Job> jobClass, String name, String group, Map<String, Object> jobData) {
		JobDataMap map = new JobDataMap();
		if (jobData != null) map.putAll(jobData);
		return JobBuilder.newJob(jobClass).withIdentity(name, group).usingJobData(map).build();
	}

	private Trigger buildCronJobTrigger(String cronExp) {
		return TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(cronExp)).build();
	}

	private List<Map<String, String>> loadScheduleList(String batId) {
		Map<String, Object> param = new HashMap<>();
		if (batId != null) param.put("bat_id", batId);
		return mapper.selectBatchConfig(param);
	}
}
