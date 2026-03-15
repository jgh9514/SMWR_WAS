package com.sysconf.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
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
	
	@Value("${spring.profiles.active}")
	String profilesValue;

	@SuppressWarnings("unchecked")
	@PostConstruct
	public void start() {
		log.info("===== JobController start invoked =====");
	    try {
			List<Map<String, String>> scheduleList = loadScheduleList(null);
        	for(Map<String, String> scheduleMap : scheduleList) {
        		String jobKey = String.valueOf(scheduleMap.get("bat_id"));
				String jobClassName = scheduleMap.get("job_class"); // 배치 Job 클래스명
				String cronExp = scheduleMap.get("cron_expr");      // 크론 표현식
				String callYn = scheduleMap.get("use_yn");          // 실행 여부(Y/N)
				log.info("jobKey : " + jobKey + " / jobClassName : " + jobClassName + " / cronExp : " + cronExp + " / callYn : " + callYn);
        		if ("Y".equalsIgnoreCase(callYn)) {
					try {
						Class<?> classObject = Class.forName(jobClassName);
						log.info("job============== " + classObject);

						Map<String, Object> jobData = new HashMap<>();
						jobData.put("applicationContext", applicationContext);
						
						scheduler.scheduleJob(buildJobDetail(
								(Class<? extends Job>) classObject, jobKey, jobKey, jobData),
								buildCronJobTrigger(cronExp));
					} catch (ClassNotFoundException e) {
						log.error("Not found class: {}", jobClassName);
					}

        		}
        	}
	    }
		catch (Exception e) {
	        log.error(e.getMessage());
	    }
	}

	public void clear() {
		log.info("===== JobController Destroy =====");
	    try {
	    	// 초기??
			scheduler.clear();
	    }
		catch (Exception e) {
	        log.error(e.getMessage());
	    }
	}
	
	public Trigger buildCronJobTrigger(String scheduleExp) {
	    return TriggerBuilder.newTrigger()
	    		.withSchedule(CronScheduleBuilder.cronSchedule(scheduleExp))
	    		.build();
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
	public List<Map<String, String>> loadScheduleList(Map<String, Object> additionalParam) {
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
			
			if (scheduler == null) {
				log.error("Scheduler가 null입니다.");
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
			List<Map<String, String>> scheduleList = loadScheduleList(param);

			if (scheduleList == null || scheduleList.isEmpty()) {
				log.warn("배치 설정을 찾을 수 없습니다. jobKey={}, batId={}", jobKey, batId);
				return false;
			}

			Map<String, String> scheduleMap = scheduleList.get(0);
			if (scheduleMap == null) {
				log.error("배치 설정 Map이 null입니다. jobKey={}", jobKey);
				return false;
			}
			
			// job_class 또는 bat_cls_nm 필드 확인 (호환성)
			String jobClassName = scheduleMap.get("job_class");
			if (jobClassName == null || jobClassName.trim().isEmpty()) {
				jobClassName = scheduleMap.get("bat_cls_nm");
			}
			
			if (jobClassName == null || jobClassName.trim().isEmpty()) {
				log.error("Job 클래스명을 찾을 수 없습니다. jobKey={}, scheduleMap={}", jobKey, scheduleMap);
				return false;
			}

			JobKey quartzJobKey = JobKey.jobKey(jobKey);
			JobDataMap jobDataMap = new JobDataMap();
			jobDataMap.put("applicationContext", applicationContext);
			if (jobData != null) {
				jobDataMap.putAll(jobData);
			}

			// 실행 이력 등록 (트리거 기준)
			Map<String, Object> runHis = new HashMap<>();
			runHis.put("bat_id", batId);
			runHis.put("rslt_cd", "TRIGGERED");
			runHis.put("rslt_txt", "Manual trigger");
			
			try {
				mapper.insertBatchRunHis(runHis);
				Long runSn = (Long) runHis.get("run_sn");
				log.debug("배치 실행 이력 등록 완료. run_sn={}", runSn);
			} catch (Exception e) {
				log.warn("배치 실행 이력 등록 실패 (계속 진행): {}", e.getMessage());
			}

			// 이미 스케줄러에 등록된 경우에는 즉시 트리거만 발행
			if (scheduler.checkExists(quartzJobKey)) {
				scheduler.triggerJob(quartzJobKey, jobDataMap);
				log.info("배치 수동 실행 트리거 완료. batId={}, jobClassName={}", batId, jobClassName);
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

	private Long parseBatId(String jobKey) {
		try {
			return Long.valueOf(jobKey);
		} catch (Exception e) {
			return null;
		}
	}
}

