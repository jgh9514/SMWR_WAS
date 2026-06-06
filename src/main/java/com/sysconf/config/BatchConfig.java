package com.sysconf.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.admin.batch.ManualBatchRunOutcome;
import com.admin.batch.mapper.BatchMapper;
import com.smw.rta.config.BatchLogProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Quartz {@link Scheduler} 기반 배치.
 * <ul>
 *   <li>{@code smw.batch.quartz.enabled=true} 일 때만 DB {@code sys_batch_config} 크론 등록</li>
 *   <li>수동 실행({@link #runOnce})은 크론 OFF여도 동작 — 운영에서 스케줄만 끄고 API로 돌릴 때 사용</li>
 * </ul>
 * <p>{@code @ConditionalOnBean(Scheduler)} 는 부팅 순서상 스킵될 수 있어 사용하지 않는다. 크론 등록은 {@link ApplicationReadyEvent} 이후에 수행한다.</p>
 * <p>Quartz가 JDBC+클러스터({@code spring.quartz.job-store-type=jdbc})이면 Job/Trigger는 DB에 공유되며,
 * 여러 Pod가 동시에 기동해도 {@link #registerOrRescheduleCronJob}이 중복 {@code scheduleJob} 대신
 * {@code rescheduleJob}로 갱신한다.
 */
@Slf4j
@Configuration
public class BatchConfig {

	@Autowired
	private ObjectProvider<Scheduler> schedulerProvider;

	@Autowired 
	private BatchMapper mapper;
	
	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private BatchLogProperties batchLogProperties;
	
	@Value("${spring.profiles.active:}")
	String profilesValue;

	/** false 이면 크론 스케줄 미등록(수동 실행·단발 트리거는 가능) */
	@Value("${smw.batch.quartz.enabled:false}")
	private boolean quartzCronEnabled;

	private Scheduler resolveScheduler() {
		return schedulerProvider.getIfAvailable();
	}

	/**
	 * Quartz {@link Scheduler} 빈이 준비된 뒤에만 크론 등록({@code @ConditionalOnBean} 으로는 빈 전체가 스킵되는 경우가 있음).
	 */
	@SuppressWarnings("unchecked")
	@EventListener(ApplicationReadyEvent.class)
	public void registerCronJobsOnReady() {
		log.info("===== BatchConfig registerCronJobsOnReady (quartzCronEnabled={}) =====", quartzCronEnabled);
		if (!quartzCronEnabled) {
			log.info("Quartz 크론 등록 생략됨. 수동 실행(/api/v1/batch/run)은 Scheduler 준비 후 사용 가능합니다.");
			return;
		}
		Scheduler scheduler = resolveScheduler();
		if (scheduler == null) {
			log.error("Quartz Scheduler 빈이 없어 크론을 등록할 수 없습니다. spring-boot-starter-quartz 및 자동설정을 확인하세요.");
			return;
		}
	    try {
			List<Map<String, ?>> scheduleList = loadScheduleList(null);
        	for (Map<String, ?> scheduleMap : scheduleList) {
        		String jobKey = String.valueOf(scheduleMap.get("bat_id"));
				String jobClassName = scheduleMap.get("job_class") != null ? String.valueOf(scheduleMap.get("job_class")) : null; // 배치 Job 클래스명
				String cronExp = scheduleMap.get("cron_expr") != null ? String.valueOf(scheduleMap.get("cron_expr")) : null;      // 크론 표현식
				String callYn = scheduleMap.get("use_yn") != null ? String.valueOf(scheduleMap.get("use_yn")) : null;          // 실행 여부(Y/N)
				log.info("jobKey : " + jobKey + " / jobClassName : " + jobClassName + " / cronExp : " + cronExp + " / callYn : " + callYn);
        		if ("Y".equalsIgnoreCase(callYn)) {
					try {
						Class<?> classObject = Class.forName(jobClassName);
						log.info("job============== " + classObject);

						// JDBC JobStore: JobDataMap 은 직렬화·DB 보관 — ApplicationContext 는 SchedulerContext 로만
						// (QuartzSchedulerContextConfig)
						registerOrRescheduleCronJob(scheduler, jobKey, cronExp,
								(Class<? extends Job>) classObject, new HashMap<>());
					} catch (ClassNotFoundException e) {
						log.error("Not found class: {}", jobClassName);
					} catch (SchedulerException e) {
						log.error("Quartz job 등록 실패 batId={}, class={}", jobKey, jobClassName, e);
					}

        		}
        	}
	    }
		catch (Exception e) {
	        log.error(e.getMessage());
		}
	}

	/**
	 * sys_batch_config 등록(크론) — Job 키·트리거 키(batId_cron) 고정. JDBC+클러스터 시 여러 인스턴스가 동시에 부팅해도
	 * idempotent/갱신만 수행한다.
	 * <p>
	 * 트리거를 먼저 본다. QRTZ 에 이미 {@code 7_cron} 이 있는데 {@code checkExists(TriggerKey)}·Job 부재로
	 * {@code scheduleJob(Detail+Trigger)} 가 가면 "duplicate key qrtz_triggers_pkey" 가 난다(경합·부팅 순서·불일치).
	 */
	private void registerOrRescheduleCronJob(Scheduler scheduler, String jobKeyStr, String cronExp,
			Class<? extends Job> jobClass, Map<String, Object> jobData) throws SchedulerException {
		JobKey jk = JobKey.jobKey(jobKeyStr);
		JobDetail jobDetail = buildJobDetail(jobClass, jobKeyStr, jobKeyStr, jobData);
		Trigger trigger = TriggerBuilder.newTrigger()
				.withIdentity(jobKeyStr + "_cron", Scheduler.DEFAULT_GROUP)
				.forJob(jobDetail)
				.withSchedule(CronScheduleBuilder.cronSchedule(cronExp))
				.build();
		TriggerKey tk = trigger.getKey();
		if (scheduler.checkExists(tk)) {
			try {
				scheduler.rescheduleJob(tk, trigger);
				if (!scheduler.checkExists(jk)) {
					scheduler.addJob(jobDetail, true);
				}
				log.info("Quartz cron reschedule (기존 트리거). jobKey={}", jobKeyStr);
			} catch (Exception e) {
				// QRTZ_TRIGGERS에는 있지만 QRTZ_CRON_TRIGGERS에 없는 고아 트리거 — 삭제 후 재등록
				log.warn("rescheduleJob 실패(고아 트리거 의심), 삭제 후 재등록. triggerKey={}", tk, e);
				scheduler.unscheduleJob(tk);
				if (scheduler.checkExists(jk)) {
					scheduler.scheduleJob(trigger);
				} else {
					scheduler.scheduleJob(jobDetail, trigger);
				}
				log.info("Quartz cron 고아 트리거 복구 완료. jobKey={}", jobKeyStr);
			}
			return;
		}
		if (scheduler.checkExists(jk)) {
			scheduler.scheduleJob(trigger);
			log.info("Quartz job 존재, cron 트리거만 등록. jobKey={}", jobKeyStr);
			return;
		}
		try {
			scheduler.scheduleJob(jobDetail, trigger);
		} catch (ObjectAlreadyExistsException oae) {
			if (scheduler.checkExists(tk)) {
				scheduler.rescheduleJob(tk, trigger);
			} else if (scheduler.checkExists(jk)) {
				scheduler.scheduleJob(trigger);
			} else {
				throw oae;
			}
			log.info("Quartz job 동시 등록 경합 처리. jobKey={}", jobKeyStr, oae);
		} catch (SchedulerException e) {
			if (isLikelyQrtzDuplicateKey(e) && scheduler.checkExists(tk)) {
				scheduler.rescheduleJob(tk, trigger);
				log.info("Quartz qrtz_triggers PK 중복 복구(reschedule). jobKey={}", jobKeyStr);
				return;
			}
			throw e;
		}
	}

	private static boolean isLikelyQrtzDuplicateKey(SchedulerException e) {
		String msg = e.getMessage() != null ? e.getMessage() : "";
		if (msg.contains("duplicate key") && msg.contains("qrtz_")) {
			return true;
		}
		for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
			msg = t.getMessage() != null ? t.getMessage() : "";
			if (msg.contains("duplicate key") && (msg.contains("qrtz_") || msg.contains("QRTZ_"))) {
				return true;
			}
		}
		return false;
	}

	/** 배치 재시작 API: 스케줄러 비우고 크론 재등록 */
	public void clear() {
		log.info("===== JobController Destroy =====");
	    try {
			Scheduler scheduler = resolveScheduler();
			if (scheduler == null) {
				log.warn("Quartz Scheduler 빈이 없어 clear 를 건너뜁니다.");
				return;
			}
			scheduler.clear();
	    }
		catch (Exception e) {
	        log.error(e.getMessage());
	    }
	}

	/** {@link #clear()} 후 크론 재등록 */
	public void start() {
		registerCronJobsOnReady();
	}
	
	public Trigger buildSimpleJobTrigger(Integer hour) {
		return TriggerBuilder.newTrigger()
				.withSchedule(SimpleScheduleBuilder
				.simpleSchedule()
				.repeatForever()
				.withIntervalInHours(hour))
				.build();
	}

	public JobDetail buildJobDetail(Class<? extends Job> job, String name, String desc, Map<String, Object> params) {
		JobDataMap jobDataMap = new JobDataMap();
		jobDataMap.putAll(params);
		return JobBuilder
		    .newJob(job)
		    .withIdentity(name)
		    .withDescription(desc)
		    .usingJobData(jobDataMap)
		    .build();
	}

	/**
	 * 배치 설정 목록을 조회한다. 화면/서비스에서 공용으로 사용할 수 있도록 노출한다.
	 */
	public List<Map<String, ?>> loadScheduleList(Map<String, Object> additionalParam) {
		Map<String, Object> mapConfig = new HashMap<>();
		if (additionalParam != null) {
			mapConfig.putAll(additionalParam);
		}
		return mapper.selectBatchConfig(mapConfig);
	}

	/**
	 * 특정 배치를 즉시 한 번 실행한다.
	 * @param jobKey 실행할 배치 코드
	 * @param jobData 추가로 JobDataMap에 전달할 값 (nullable)
	 * @return 성공 여부
	 */
	@SuppressWarnings("unchecked")
	public boolean runOnce(String jobKey, Map<String, Object> jobData) {
		try {
			// 입력값 검증
			if (jobKey == null || jobKey.trim().isEmpty()) {
				log.warn("jobKey가 비어있습니다.");
				return false;
			}
			
			if (applicationContext == null) {
				log.error("ApplicationContext가 null입니다.");
				return false;
			}
			
			Scheduler scheduler = resolveScheduler();
			if (scheduler == null) {
				log.error("Quartz Scheduler 빈이 없습니다. spring-boot-starter-quartz 의존성과 자동설정을 확인하세요.");
				return false;
			}
			
			if (mapper == null) {
				log.error("BatchMapper가 null입니다.");
				return false;
			}
			
			Long batId = parseBatId(jobKey);
			if (batId == null) {
				log.warn("배치 ID를 숫자로 변환할 수 없습니다. jobKey={}", jobKey);
				return false;
			}

			Map<String, Object> param = new HashMap<>();
			param.put("bat_id", batId);
			List<Map<String, ?>> scheduleList = loadScheduleList(param);

			if (scheduleList == null || scheduleList.isEmpty()) {
				log.warn("배치 설정을 찾을 수 없습니다. jobKey={}, batId={}", jobKey, batId);
				return false;
			}

			Map<String, ?> scheduleMap = scheduleList.get(0);
			if (scheduleMap == null) {
				log.error("배치 설정 Map이 null입니다. jobKey={}", jobKey);
				return false;
			}
			
			// job_class 또는 bat_cls_nm 필드 확인 (호환성)
			Object jobClassObj = scheduleMap.get("job_class");
			String jobClassName = jobClassObj != null ? String.valueOf(jobClassObj) : null;
			if (jobClassName == null || jobClassName.trim().isEmpty()) {
				Object alt = scheduleMap.get("bat_cls_nm");
				jobClassName = alt != null ? String.valueOf(alt) : null;
			}
			
			if (jobClassName == null || jobClassName.trim().isEmpty()) {
				log.error("Job 클래스명을 찾을 수 없습니다. jobKey={}, scheduleMap={}", jobKey, scheduleMap);
				return false;
			}

			JobKey quartzJobKey = JobKey.jobKey(jobKey);
			JobDataMap jobDataMap = new JobDataMap();
			if (jobData != null) {
				jobDataMap.putAll(jobData);
			}

			// 실행 이력은 BaseBatchJob.execute 에서만 등록한다 (RUNNING → SUCCESS/FAIL).
			// 여기서 선INSERT 하면 TRIGGERED/Manual trigger 행이 먼저 쌓여 혼란을 준다.

			// 이미 스케줄러에 등록된 경우에는 즉시 트리거만 발행
			if (scheduler.checkExists(quartzJobKey)) {
				scheduler.triggerJob(quartzJobKey, jobDataMap);
				log.info("배치 수동 실행 트리거 완료(JDBC/cluster). batId={}, jobClassName={}, streamId={}",
						batId, jobClassName, jobData != null ? jobData.get("stream_id") : null);
				return true;
			}

			// 등록되지 않은 경우 단발성 JobDetail + Trigger를 만들어 실행
			Class<?> classObject = Class.forName(jobClassName);
			if (classObject == null) {
				log.error("Job 클래스를 로드할 수 없습니다. jobClassName={}", jobClassName);
				return false;
			}
			
			JobDetail jobDetail = buildJobDetail(
					(Class<? extends Job>) classObject, jobKey, jobKey, jobDataMap);

			Trigger trigger = TriggerBuilder.newTrigger()
					.forJob(jobDetail)
					.withIdentity(jobKey + "_manual_" + System.currentTimeMillis())
					.startNow()
					.build();

			scheduler.scheduleJob(jobDetail, trigger);
			log.info("배치 수동 실행 스케줄 완료. batId={}, jobClassName={}", batId, jobClassName);
			return true;
		} catch (ClassNotFoundException e) {
			log.error("수동 실행 대상 Job 클래스를 찾을 수 없습니다. jobKey={}", jobKey, e);
		} catch (NullPointerException e) {
			log.error("배치 수동 실행 중 NullPointerException 발생. jobKey={}", jobKey, e);
		} catch (Exception e) {
			log.error("배치 수동 실행 중 오류가 발생했습니다. jobKey={}", jobKey, e);
		}
		return false;
	}

	/**
	 * 수동 배치를 트리거한 뒤 {@code sys_batch_run_his} 가 종료 상태가 될 때까지 동기 대기한다.
	 * (Quartz Job 은 기존과 같이 워커 Pod 에서 실행되며, API 스레드는 DB 이력만 폴링한다.)
	 */
	public ManualBatchRunOutcome runOnceAndWait(String jobKey, Map<String, Object> jobData) {
		Long batId = parseBatId(jobKey);
		if (batId == null) {
			return ManualBatchRunOutcome.notTriggered();
		}
		long waitTimeoutMs = Math.max(60_000L, batchLogProperties.getManualRun().getWaitTimeoutMs());
		long appearTimeoutMs = Math.max(5_000L, batchLogProperties.getManualRun().getAppearTimeoutMs());
		long pollIntervalMs = Math.max(500L, batchLogProperties.getManualRun().getPollIntervalMs());

		Long maxRunSnBefore = mapper.selectMaxRunSnByBatId(batId);
		if (maxRunSnBefore == null) {
			maxRunSnBefore = 0L;
		}

		Map<String, Object> triggerData = jobData == null ? null : new HashMap<>(jobData);
		if (triggerData != null) {
			triggerData.remove("stream_id");
		}

		long t0 = System.currentTimeMillis();
		if (!runOnce(jobKey, triggerData)) {
			return ManualBatchRunOutcome.notTriggered();
		}

		Long runSn = waitForNewRunSn(batId, maxRunSnBefore, appearTimeoutMs, pollIntervalMs);
		if (runSn == null) {
			log.warn("수동 배치 RUNNING 이력 미등록. batId={}, maxRunSnBefore={}", batId, maxRunSnBefore);
			return new ManualBatchRunOutcome(true, false, true, null, null,
					"배치 트리거 후 실행 이력(RUNNING)을 확인하지 못했습니다.", System.currentTimeMillis() - t0);
		}

		boolean completed = waitUntilRunFinished(runSn, waitTimeoutMs, pollIntervalMs);
		Map<String, ?> detail = mapper.selectBatchRunHisDetail(runSn);
		String rsltCd = detail != null && detail.get("rslt_cd") != null ? String.valueOf(detail.get("rslt_cd")) : null;
		String rsltTxt = detail != null && detail.get("rslt_txt") != null ? String.valueOf(detail.get("rslt_txt")) : null;
		long elapsedMs = System.currentTimeMillis() - t0;
		if (!completed) {
			log.warn("수동 배치 완료 대기 시간 초과. batId={}, runSn={}, rsltCd={}", batId, runSn, rsltCd);
		} else {
			log.info("수동 배치 동기 완료. batId={}, runSn={}, rsltCd={}, elapsedMs={}", batId, runSn, rsltCd, elapsedMs);
		}
		return new ManualBatchRunOutcome(true, completed, !completed, runSn, rsltCd, rsltTxt, elapsedMs);
	}

	private Long waitForNewRunSn(Long batId, long maxRunSnBefore, long appearTimeoutMs, long pollIntervalMs) {
		long deadline = System.currentTimeMillis() + appearTimeoutMs;
		while (System.currentTimeMillis() < deadline) {
			Long currentMax = mapper.selectMaxRunSnByBatId(batId);
			if (currentMax != null && currentMax > maxRunSnBefore) {
				String rsltCd = mapper.selectBatchRunHisResultCode(currentMax);
				if (rsltCd != null && !rsltCd.isBlank()) {
					return currentMax;
				}
			}
			sleepForPoll(pollIntervalMs);
		}
		return null;
	}

	private boolean waitUntilRunFinished(Long runSn, long waitTimeoutMs, long pollIntervalMs) {
		long deadline = System.currentTimeMillis() + waitTimeoutMs;
		while (System.currentTimeMillis() < deadline) {
			String rsltCd = mapper.selectBatchRunHisResultCode(runSn);
			if (rsltCd == null) {
				return false;
			}
			if (!"RUNNING".equals(rsltCd)) {
				return true;
			}
			sleepForPoll(pollIntervalMs);
		}
		return false;
	}

	private static void sleepForPoll(long pollIntervalMs) {
		try {
			Thread.sleep(pollIntervalMs);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("수동 배치 대기 중단", ie);
		}
	}

	private Long parseBatId(String jobKey) {
		try {
			return Long.valueOf(jobKey);
		} catch (Exception e) {
			return null;
		}
	}
}

